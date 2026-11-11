package com.wanted.lmspractice.global.file;

import com.wanted.lmspractice.global.exception.BusinessException;
import com.wanted.lmspractice.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * [역할] 업로드 이미지 저장(UUID 파일명)·삭제·읽기와 형식·크기 검증 — chap06 save()를 한곳으로 공통화
 * [담당] 팀원 4 · #16   [기획] D-22(JPG/PNG/WebP, 장당 50MB), NFR-01·05
 * [연결]
 *  - 호출받음: WorkService (최종 과제 저장·수정, #19 일반 작품도 같은 메서드 사용)
 *  - 호출함:   없음 (파일 시스템만)
 *  - 읽기:     WorkFileController 가 WorkService.openFile 을 거쳐 load(key) 로 경로를 얻는다. 업로드 폴더는 정적 리소스로 노출하지 않는다.
 * [검증 순서] ① 장수·빈 파일·크기·확장자 (읽지 않고 판단) → ② 저장하면서 파일 앞부분(매직 바이트)이 실제 JPG/PNG/WebP 인지 확인.
 *   확장자만 바꾼 위장 파일은 ②에서 걸린다. JDK ImageIO 는 WebP 를 읽지 못해 매직 바이트로 판별한다(새 의존성 없음).
 * [실패 처리] storeAll 은 중간에 하나라도 실패하면 이미 저장한 파일을 모두 지운 뒤 예외를 던진다(F-05).
 *   deleteQuietly 가 실패하면 key 를 재처리 대기 목록에 넣고 FileDeleteRetryScheduler 가 다시 지운다(#161).
 *   대기 목록은 업로드 폴더의 .pending-deletes 파일에도 적어 두어 서버를 재시작해도 이어서 지운다.
 * [기술] 🟢 MultipartFile·UUID·transferTo (chap06)
 */
@Slf4j
@Component
public class FileStorage {

    /** D-22: 장당 50MB. DB CHECK(source_size_bytes ≤ 52428800)·application.yaml max-file-size 와 같은 값 */
    public static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

    /** 허용 확장자 → 저장할 MIME (DB CHECK ck_files_mime 값과 글자까지 같아야 한다) */
    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");

    private static final int HEADER_BYTES = 12;

    /** ❓ 임시값: 삭제 재처리 횟수(처음 실패 포함). 스케줄러 간격 10분이면 약 1시간 동안 다시 시도한다 */
    static final int MAX_DELETE_ATTEMPTS = 6;

    /** 재처리 대기 목록을 적어 두는 파일 — 업로드 폴더 바로 아래. 한 줄에 "시도 횟수<TAB>key" (#161 보완: 재시작해도 유지) */
    static final String PENDING_DELETES_FILE = ".pending-deletes";

    /** 삭제에 실패해 다시 지울 key → 지금까지 시도한 횟수 (여러 요청 스레드가 함께 쓰므로 동시성 Map) */
    private final Map<String, Integer> pendingDeletes = new ConcurrentHashMap<>();

    private final Path root;

    /** 대기 목록 파일 경로. S3 모드에서도 업로드 폴더(UPLOAD_DIR)는 서버의 고정 경로라(영상 저장) 같은 곳에 둔다 */
    private final Path pendingFile;

    /** null 이면 로컬 디스크(file.upload-dir), 있으면 S3 호환 저장소(file.storage=s3) */
    private final RemoteObjectStore remote;

    /** 로컬 디스크 전용 — 테스트와 로컬 개발용 */
    public FileStorage(String uploadDir) {
        this(uploadDir, null);
    }

    @Autowired
    public FileStorage(@Value("${file.upload-dir}") String uploadDir, @Autowired(required = false) RemoteObjectStore remote) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.remote = remote;
        this.pendingFile = root.resolve(PENDING_DELETES_FILE);
        loadPendingDeletes();
    }

    /**
     * 이미지 여러 장 저장 — 전부 저장하거나(성공), 하나도 남기지 않는다(실패).
     * 호출: WorkService.saveFinalWorkVersion / updateFinalWorkVersion
     * @throws BusinessException INVALID_IMAGE_FILE, FILE_TOO_LARGE, FILE_SAVE_FAILED
     */
    public List<StoredFile> storeAll(List<MultipartFile> files) {
        files.forEach(this::precheck);                       // 읽기 전에 거를 수 있는 것은 먼저 거른다(디스크 쓰기 전 실패)
        List<StoredFile> stored = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                stored.add(store(file));
            }
            return stored;
        } catch (RuntimeException e) {
            deleteAllQuietly(stored);                        // F-05: 앞서 저장한 파일을 남기지 않는다
            throw e;
        }
    }

    /** 1개 저장 — UUID 파일명으로 업로드 폴더 아래 works/ 에 쓴다. 앞부분이 실제 이미지가 아니면 INVALID_IMAGE_FILE */
    public StoredFile store(MultipartFile file) {
        return store(file, "works");
    }

    /** 폴더를 정해 1개 저장 — 취미·대분류 대표 사진은 hobby/ 에 둔다. 검증·UUID 파일명은 store(file)와 같다 */
    public StoredFile store(MultipartFile file, String folder) {
        String extension = precheck(file);
        String mime = sniffMime(file);
        if (mime == null || !mime.equals(MIME_BY_EXTENSION.get(extension))) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);   // 확장자와 실제 내용이 다름 = 위장 파일
        }
        String key = folder + "/" + UUID.randomUUID() + "." + extension;      // 원래 파일명은 경로에 쓰지 않는다(경로 조작 방지)
        try {
            if (remote != null) {
                try (InputStream in = file.getInputStream()) {
                    remote.put(key, in, file.getSize(), mime);
                }
            } else {
                Path target = root.resolve(key).normalize();
                Files.createDirectories(target.getParent());
                file.transferTo(target);
            }
        } catch (IOException | RuntimeException e) {
            deleteQuietly(key);                              // 쓰다 만 파일이 남지 않게
            throw new BusinessException(ErrorCode.FILE_SAVE_FAILED);
        }
        return new StoredFile(key, safeOriginalName(file), mime, file.getSize());
    }

    /** 저장된 파일의 실제 경로. 업로드 폴더 밖을 가리키는 key 이거나 파일이 없으면 NOT_FOUND */
    public Path load(String key) {
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return file;
    }

    /** 시드 이미지 키 접두어 — 저장소에 들어 있는 시연용 파일(uploads/seed/*)은 앱이 지우지 않는다 */
    private static final String SEED_KEY_PREFIX = "seed/";

    /**
     * 파일 내용을 읽는 스트림 — 로컬이든 클라우드든 같은 방식으로 읽는다. 호출한 쪽이 닫는다.
     * 호출: WorkService.openFile → WorkFileController, LessonVideoController
     * @throws BusinessException NOT_FOUND (없는 파일·저장 폴더 밖 key)
     */
    public InputStream open(String key) {
        if (remote == null) {
            try {
                return Files.newInputStream(load(key));
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
        }
        if (key.contains("..")) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        try {
            return remote.open(key);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    /** 클라우드 저장소일 때만 짧게 유효한 읽기 주소를 돌려준다(영상 재생용). 로컬이면 null → 서버가 직접 내려보낸다 */
    public String presignedUrlOrNull(String key, java.time.Duration validFor) {
        if (remote == null || key.contains("..")) {
            return null;
        }
        return remote.presignedGetUrl(key, validFor);
    }

    public boolean isRemote() {
        return remote != null;
    }

    /**
     * 삭제 — 없는 파일은 무시한다. 실패해도 예외를 던지지 않는다(이미 끝난 DB 처리를 되돌리지 않기 위해).
     * 실패한 key 는 재처리 대기 목록에 넣고, FileDeleteRetryScheduler 가 주기적으로 다시 지운다(고아 파일 방지).
     * 시드 이미지(seed/…)는 작품 이미지를 교체해도 지우지 않는다 — 저장소 파일이라 DB를 다시 적재해도 돌아오지 않는다(로컬·클라우드 공통).
     */
    public void deleteQuietly(String key) {
        if (key == null || key.startsWith(SEED_KEY_PREFIX)) {
            return;
        }
        if (!tryDelete(key)) {
            pendingDeletes.putIfAbsent(key, 1);
            savePendingDeletes();
            log.warn("파일 삭제 실패 — 재처리 대기 목록에 넣음: key={}", key);
        }
    }

    /**
     * 재처리 대기 중인 파일을 다시 지운다. 성공하면 목록에서 빼고, MAX_DELETE_ATTEMPTS 번 실패하면 ERROR 로그를 남기고 포기한다
     * (로그의 key 로 직접 지워야 한다). 대기 목록은 .pending-deletes 파일에도 적혀 있어 서버를 재시작해도 이어서 시도한다.
     * 호출: FileDeleteRetryScheduler (주기 실행)
     * @return 아직 대기 중인 개수
     */
    public int retryPendingDeletes() {
        if (pendingDeletes.isEmpty()) {
            return 0;
        }
        for (String key : List.copyOf(pendingDeletes.keySet())) {
            if (tryDelete(key)) {
                pendingDeletes.remove(key);
                log.info("파일 삭제 재처리 성공: key={}", key);
                continue;
            }
            int attempts = pendingDeletes.merge(key, 1, Integer::sum);
            if (attempts >= MAX_DELETE_ATTEMPTS) {
                pendingDeletes.remove(key);
                log.error("파일 삭제 재처리 {}회 실패 — 고아 파일, 직접 삭제 필요: key={}", attempts, key);
            }
        }
        savePendingDeletes();
        return pendingDeletes.size();
    }

    /**
     * 서버 시작 때 .pending-deletes 를 읽어 대기 목록을 되살린다. 형식이 깨진 줄은 건너뛴다.
     * 파일을 못 읽어도 서버는 뜬다(WARN) — 그 key 들은 이전 실행의 WARN 로그로 확인할 수 있다.
     */
    private void loadPendingDeletes() {
        if (!Files.isRegularFile(pendingFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(pendingFile, StandardCharsets.UTF_8)) {
                int tab = line.indexOf('\t');
                if (tab <= 0 || tab == line.length() - 1) {
                    continue;
                }
                try {
                    pendingDeletes.put(line.substring(tab + 1), Integer.parseInt(line.substring(0, tab)));
                } catch (NumberFormatException ignored) {
                    // 깨진 줄은 버린다
                }
            }
            if (!pendingDeletes.isEmpty()) {
                log.info("파일 삭제 재처리 대기 {}건을 이어서 처리합니다", pendingDeletes.size());
            }
        } catch (IOException e) {
            log.warn("파일 삭제 재처리 대기 목록을 읽지 못했습니다: {}", pendingFile, e);
        }
    }

    /**
     * 대기 목록을 파일에 다시 쓴다(임시 파일에 쓴 뒤 바꿔치기 — 쓰는 도중 꺼져도 이전 내용이 남는다). 비면 파일을 지운다.
     * synchronized: 요청 스레드와 스케줄러가 동시에 쓰더라도, 잠금 안에서 그 순간의 목록을 읽으므로 마지막에 쓴 내용이 최신이다.
     * 쓰기에 실패해도 메모리 목록으로 재처리는 계속된다(WARN).
     */
    private synchronized void savePendingDeletes() {
        try {
            if (pendingDeletes.isEmpty()) {
                Files.deleteIfExists(pendingFile);
                return;
            }
            List<String> lines = new ArrayList<>();
            pendingDeletes.forEach((key, attempts) -> lines.add(attempts + "\t" + key));
            Files.createDirectories(root);
            Path temp = root.resolve(PENDING_DELETES_FILE + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, pendingFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("파일 삭제 재처리 대기 목록을 저장하지 못했습니다: {}", pendingFile, e);
        }
    }

    /** 재처리 대기 중인 key (확인·테스트용, 읽기 전용) */
    public Set<String> getPendingDeleteKeys() {
        return Collections.unmodifiableSet(pendingDeletes.keySet());
    }

    // 실제 삭제 1회. 없는 파일·업로드 폴더 밖 key 는 지울 것이 없으므로 성공으로 본다
    private boolean tryDelete(String key) {
        try {
            if (remote != null) {
                if (!key.contains("..")) {
                    remote.delete(key);
                }
                return true;
            }
            Path file = root.resolve(key).normalize();
            if (file.startsWith(root)) {
                Files.deleteIfExists(file);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public void deleteAllQuietly(List<StoredFile> files) {
        files.forEach(f -> deleteQuietly(f.getKey()));
    }

    // 디스크를 읽지 않고 판단할 수 있는 검사. 통과하면 소문자 확장자를 돌려준다
    private String precheck(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String name = file.getOriginalFilename();
        int dot = name == null ? -1 : name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!MIME_BY_EXTENSION.containsKey(extension)) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
        return extension;
    }

    // 파일 앞 12바이트로 JPEG(FF D8 FF) / PNG(89 50 4E 47 0D 0A 1A 0A) / WebP(RIFF....WEBP) 판별. 아니면 null
    private String sniffMime(MultipartFile file) {
        byte[] h = new byte[HEADER_BYTES];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(h, 0, HEADER_BYTES);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_SAVE_FAILED);
        }
        if (read >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (read >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
            return "image/png";
        }
        if (read >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    // work_files.original_name VARCHAR(255) 에 맞추고, 경로 구분자가 섞여 와도 파일 이름만 남긴다
    private String safeOriginalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
