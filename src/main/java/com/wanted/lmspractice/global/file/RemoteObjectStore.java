package com.wanted.lmspractice.global.file;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * [역할] 업로드 파일을 S3 호환 저장소(Cloudflare R2·AWS S3 등)에 넣고 빼는 클라이언트 — FileStorage 가 file.storage=s3 일 때 쓴다
 * [연결] 호출받음: FileStorage (저장·삭제·읽기·영상 임시 주소). file.storage 가 s3 가 아니면 이 빈은 만들어지지 않는다.
 * [설정] file.s3.endpoint(R2는 필수, AWS S3는 비움) · region · bucket · access-key · secret-key — 모두 환경변수로 넣는다
 */
@Component
@ConditionalOnProperty(name = "file.storage", havingValue = "s3")
public class RemoteObjectStore {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public RemoteObjectStore(@Value("${file.s3.endpoint:}") String endpoint,
                             @Value("${file.s3.region:auto}") String region,
                             @Value("${file.s3.bucket}") String bucket,
                             @Value("${file.s3.access-key}") String accessKey,
                             @Value("${file.s3.secret-key}") String secretKey) {
        this.bucket = bucket;
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        var s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        var clientBuilder = S3Client.builder().region(Region.of(region)).credentialsProvider(credentials)
                .serviceConfiguration(s3Config);
        var presignerBuilder = S3Presigner.builder().region(Region.of(region)).credentialsProvider(credentials)
                .serviceConfiguration(s3Config);
        if (!endpoint.isBlank()) {                       // R2 등 AWS 가 아닌 S3 호환 서비스
            clientBuilder.endpointOverride(URI.create(endpoint));
            presignerBuilder.endpointOverride(URI.create(endpoint));
        }
        this.client = clientBuilder.build();
        this.presigner = presignerBuilder.build();
    }

    public void put(String key, InputStream in, long size, String mimeType) throws IOException {
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(mimeType).build(),
                RequestBody.fromInputStream(in, size));
    }

    /** 없는 key 이면 NoSuchKeyException(런타임) — FileStorage 가 NOT_FOUND 로 바꾼다 */
    public InputStream open(String key) {
        return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    /** 짧게 유효한 읽기 주소 — 권한 확인을 마친 뒤에만 만든다(영상 재생 중 구간 이동을 저장소가 직접 처리) */
    public String presignedGetUrl(String key, Duration validFor) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(validFor)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build()).url().toString();
    }
}
