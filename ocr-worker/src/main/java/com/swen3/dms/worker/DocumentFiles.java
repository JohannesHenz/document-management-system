package com.swen3.dms.worker;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.InputStream;
import org.springframework.stereotype.Component;

/** Reads uploaded files from the bucket the backend writes them to. */
@Component
public class DocumentFiles {

    private final MinioClient client;
    private final String bucket;

    public DocumentFiles(MinioProperties properties) {
        this.client = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
        this.bucket = properties.bucket();
    }

    public byte[] read(String storageKey) throws Exception {
        try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(storageKey).build())) {
            return in.readAllBytes();
        }
    }
}
