package com.wanted.lmspractice.global.file;

import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * [역할] 관리자·강사 영상 업로드 저장소 — MP4 검사·저장·목록·삭제, 재생 시간 읽기. 파일만 다루고 DB는 모른다.
 *   저장 위치(file.upload-dir 기준, Git 제외):  관리자 → videos/uploaded/{파일}
 *                                               강사   → videos/uploaded/instructor-{강사id}/{파일}
 *   파일은 key(= lessons.video_key 에 넣는 값)로 가리킨다. 학습자 재생(LessonVideoController)도 같은 key로 파일을 찾는다.
 * [연결]
 *  - 호출받음: LessonVideoService (업로드·목록·연결·삭제)
 *  - 호출함:   없음 (파일 시스템만)
 *  - 참고:     이미지 업로드(FileStorage, 팀원 4 · #16)와 같은 방식 — ① 읽기 전 검사(빈 파일·크기·확장자) → ② 파일 앞부분으로 실제 형식 확인,
 *              원래 파일명은 허용 문자만 남겨 쓴다(경로 조작 방지). FileStorage는 이미지 전용이라 손대지 않고 따로 둔다.
 * ❓ 로컬 디스크 전용: file.storage=s3 로 실행해도 이 기능은 로컬 uploads/ 폴더에 저장한다(S3는 설정이 준비되면 구현·테스트).
 * [기술] 🟢 MultipartFile·UUID·transferTo (chap06), 🟢 java.nio Files
 */
@Slf4j
@Component
public class VideoStorage {

    /** 영상 한 개 최대 500MB. application.yaml spring.servlet.multipart.max-file-size 와 같은 값 */
    public static final long MAX_VIDEO_BYTES = 500L * 1024 * 1024;

    /** file.upload-dir 기준 저장 폴더 = 이 기능이 만든 video_key 의 접두어 */
    public static final String KEY_PREFIX = "videos/uploaded/";

    private static final String INSTRUCTOR_DIR_PREFIX = "instructor-";

    /** 저장 파일명 규칙: 한글·영문·숫자·_·- 만 + .mp4 */
    private static final String NAME_RULE = "[0-9A-Za-z가-힣_\\-]+\\.mp4";

    /** 이 기능이 만든 key 만 받는다: videos/uploaded/(instructor-{id}/)?{파일명} — 그 밖의 경로(../, 다른 영상 폴더)는 전부 거부 */
    private static final Pattern KEY_RULE =
            Pattern.compile("^" + Pattern.quote(KEY_PREFIX) + "(?:" + INSTRUCTOR_DIR_PREFIX + "(\\d{1,19})/)?(" + NAME_RULE + ")$");

    private static final Pattern INSTRUCTOR_DIR = Pattern.compile("^" + INSTRUCTOR_DIR_PREFIX + "\\d{1,19}$");

    private static final int MAX_BASE_NAME_LENGTH = 60;

    /** file.upload-dir 절대 경로 (key 를 붙여 실제 파일 위치를 만든다) */
    private final Path uploadRoot;

    /** uploadRoot/videos/uploaded */
    private final Path dir;

    public VideoStorage(@Value("${file.upload-dir}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.dir = uploadRoot.resolve(KEY_PREFIX).normalize();
    }

    /**
     * MP4 1개 저장. 원래 이름 + "_" + 무작위 8글자 + ".mp4" 로 저장한다(같은 이름을 다시 올려도 덮어쓰지 않는다).
     * @param ownerInstructorId 올린 강사 id — 관리자면 null
     * @throws BusinessException INVALID_VIDEO_FILE(빈 파일·확장자·내용이 MP4 아님), VIDEO_TOO_LARGE, FILE_SAVE_FAILED
     */
    public UploadedVideo store(MultipartFile file, Long ownerInstructorId) {
        // ① 읽기 전에 거를 수 있는 것
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_VIDEO_FILE);
        }
        if (file.getSize() > MAX_VIDEO_BYTES) {
            throw new BusinessException(ErrorCode.VIDEO_TOO_LARGE);
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!original.toLowerCase(Locale.ROOT).endsWith(".mp4")) {
            throw new BusinessException(ErrorCode.INVALID_VIDEO_FILE);
        }
        // ② 앞 8바이트: MP4는 5~8번째 바이트가 "ftyp" — 확장자만 바꾼 파일을 거른다
        if (!looksLikeMp4(file)) {
            throw new BusinessException(ErrorCode.INVALID_VIDEO_FILE);
        }

        String fileName = baseNameOf(original) + "_" + UUID.randomUUID().toString().substring(0, 8) + ".mp4";
        String key = folderKeyOf(ownerInstructorId) + fileName;
        Path target = uploadRoot.resolve(key).normalize();
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException | RuntimeException e) {
            log.warn("영상 저장 실패: {}", e.getMessage());
            deleteQuietly(target);   // 쓰다 만 파일이 남지 않게
            throw new BusinessException(ErrorCode.FILE_SAVE_FAILED);
        }
        return toUploadedVideo(target, key, ownerInstructorId);
    }

    /** 관리자용 전체 목록 — 관리자 파일 + 모든 강사 폴더. 최근에 올린 것부터 */
    public List<UploadedVideo> listAll() {
        List<UploadedVideo> all = new ArrayList<>(listFolder(null));
        if (Files.isDirectory(dir)) {
            try (Stream<Path> sub = Files.list(dir)) {
                sub.filter(p -> Files.isDirectory(p) && INSTRUCTOR_DIR.matcher(p.getFileName().toString()).matches())
                        .forEach(p -> all.addAll(listFolder(Long.valueOf(
                                p.getFileName().toString().substring(INSTRUCTOR_DIR_PREFIX.length())))));
            } catch (IOException e) {
                log.warn("영상 폴더 읽기 실패: {}", e.getMessage());
            }
        }
        all.sort(Comparator.comparing(UploadedVideo::getUploadedAt).reversed());
        return all;
    }

    /** 한 사람의 목록 — 강사는 자기 폴더만 본다. ownerInstructorId 가 null 이면 관리자 파일만 */
    public List<UploadedVideo> listOf(Long ownerInstructorId) {
        List<UploadedVideo> list = new ArrayList<>(listFolder(ownerInstructorId));
        list.sort(Comparator.comparing(UploadedVideo::getUploadedAt).reversed());
        return list;
    }

    /** key 로 파일 정보. 규칙에 안 맞는 key(경로 조작·다른 영상 폴더)이거나 파일이 없으면 NOT_FOUND */
    public UploadedVideo find(String key) {
        Path file = load(key);
        return toUploadedVideo(file, key, ownerOf(key));
    }

    /** key 의 실제 경로(미리보기용). 규칙에 안 맞거나 파일이 없으면 NOT_FOUND */
    public Path load(String key) {
        if (key == null || !KEY_RULE.matcher(key).matches()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Path file = uploadRoot.resolve(key).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return file;
    }

    /** 이 기능이 만든 key 인지 (videos/uploaded/ 아래, 이름 규칙에 맞음) */
    public boolean isUploadedKey(String key) {
        return key != null && KEY_RULE.matcher(key).matches();
    }

    /** key 를 올린 강사 id. 관리자 파일이면 null. 규칙에 안 맞는 key 는 NOT_FOUND */
    public Long ownerOf(String key) {
        Matcher m = key == null ? null : KEY_RULE.matcher(key);
        if (m == null || !m.matches()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return m.group(1) == null ? null : Long.valueOf(m.group(1));
    }

    /** 삭제 — uploaded/ 아래 파일만 지울 수 있다(drawing·cooking 등 다른 영상 폴더는 key 규칙에서 걸러진다) */
    public void delete(String key) {
        Path file = load(key);
        try {
            Files.delete(file);
        } catch (IOException e) {
            log.warn("영상 삭제 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.FILE_SAVE_FAILED);
        }
    }

    private String folderKeyOf(Long ownerInstructorId) {
        return ownerInstructorId == null ? KEY_PREFIX : KEY_PREFIX + INSTRUCTOR_DIR_PREFIX + ownerInstructorId + "/";
    }

    private List<UploadedVideo> listFolder(Long ownerInstructorId) {
        String folderKey = folderKeyOf(ownerInstructorId);
        Path folder = uploadRoot.resolve(folderKey).normalize();
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().matches(NAME_RULE))
                    .map(p -> toUploadedVideo(p, folderKey + p.getFileName(), ownerInstructorId))
                    .toList();
        } catch (IOException e) {
            log.warn("영상 목록 읽기 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private UploadedVideo toUploadedVideo(Path file, String key, Long ownerInstructorId) {
        try {
            LocalDateTime modified = LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault());
            return new UploadedVideo(key, file.getFileName().toString(), ownerInstructorId,
                    Files.size(file), readDurationSec(file), modified);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_SAVE_FAILED);
        }
    }

    private boolean looksLikeMp4(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(8);
            return head.length == 8 && "ftyp".equals(new String(head, 4, 4, StandardCharsets.US_ASCII));
        } catch (IOException e) {
            return false;
        }
    }

    /** 원래 파일명에서 경로·확장자를 떼고 허용 문자만 남긴다. 남는 게 없으면 "video" */
    private String baseNameOf(String original) {
        String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
        name = name.substring(0, name.length() - ".mp4".length());
        name = name.replaceAll("[^0-9A-Za-z가-힣_\\-]", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
        if (name.length() > MAX_BASE_NAME_LENGTH) {
            name = name.substring(0, MAX_BASE_NAME_LENGTH);
        }
        return name.isEmpty() ? "video" : name;
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("임시 영상 삭제 실패: {}", e.getMessage());
        }
    }

    // ── MP4 재생 시간 읽기: moov 상자 안의 mvhd 상자(timescale·duration)만 찾아 읽는다. 파일 전체를 읽지 않는다 ──

    /** 재생 시간(초). MP4 구조가 예상과 다르면 null (업로드 자체는 막지 않는다) */
    static Double readDurationSec(Path file) {
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            long[] moov = findBox(ch, 0, ch.size(), "moov");
            if (moov == null) {
                return null;
            }
            long[] mvhd = findBox(ch, moov[0], moov[1], "mvhd");
            if (mvhd == null) {
                return null;
            }
            ByteBuffer buf = ByteBuffer.allocate(32);
            readFully(ch, buf, mvhd[0]);
            buf.flip();
            int version = buf.get(0) & 0xFF;
            long timescale;
            long duration;
            if (version == 1) {   // version(1)+flags(3)+생성(8)+수정(8) 다음
                timescale = buf.getInt(20) & 0xFFFFFFFFL;
                duration = buf.getLong(24);
            } else {              // version(1)+flags(3)+생성(4)+수정(4) 다음
                timescale = buf.getInt(12) & 0xFFFFFFFFL;
                duration = buf.getInt(16) & 0xFFFFFFFFL;
            }
            return timescale == 0 || duration <= 0 ? null : (double) duration / timescale;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** [start, end) 구간에서 type 상자를 찾아 {내용 시작, 상자 끝} 을 돌려준다. 없으면 null */
    private static long[] findBox(FileChannel ch, long start, long end, String type) throws IOException {
        long pos = start;
        ByteBuffer header = ByteBuffer.allocate(8);
        while (pos + 8 <= end) {
            header.clear();
            readFully(ch, header, pos);
            header.flip();
            long size = header.getInt() & 0xFFFFFFFFL;
            byte[] name = new byte[4];
            header.get(name);
            long headerLength = 8;
            if (size == 1) {          // 64비트 크기
                header.clear();
                readFully(ch, header, pos + 8);
                header.flip();
                size = header.getLong();
                headerLength = 16;
            } else if (size == 0) {   // 파일 끝까지
                size = end - pos;
            }
            if (size < headerLength || pos + size > end) {
                return null;
            }
            if (type.equals(new String(name, StandardCharsets.US_ASCII))) {
                return new long[]{pos + headerLength, pos + size};
            }
            pos += size;
        }
        return null;
    }

    private static void readFully(FileChannel ch, ByteBuffer buf, long position) throws IOException {
        long p = position;
        while (buf.hasRemaining()) {
            int n = ch.read(buf, p);
            if (n < 0) {
                throw new EOFException();
            }
            p += n;
        }
    }
}
