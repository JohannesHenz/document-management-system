package com.swen3.dms.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Keeps files in a MinIO (S3-compatible) bucket under random UUID keys, so the OCR worker can read them
 * from a separate container. Creates the bucket on startup if needed.
 */
@Component
@ConditionalOnProperty(name = "dms.storage.type", havingValue = "minio")
public class MinioDocumentStorage implements DocumentStorage {

    /** Upload in 10 MiB parts, so the stream size does not need to be known up front. */
    private static final long PART_SIZE = 10L * 1024 * 1024;

    private final MinioClient client;
    private final String bucket;

    public MinioDocumentStorage(MinioProperties properties) {
        this.client = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
        this.bucket = properties.bucket();
        createBucketIfMissing();
    }

    @Override
    public String store(InputStream content) {
        String key = UUID.randomUUID().toString();
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(content, -1, PART_SIZE).build());
            return key;
        } catch (Exception e) {
            throw new StorageException("Could not store file", e);
        }
    }

    @Override
    public Resource load(String key) {
        try {
            return new InputStreamResource(client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build()));
        } catch (ErrorResponseException e) {
            throw new StorageException("No stored file for key " + key, e);
        } catch (Exception e) {
            throw new StorageException("Could not load stored file " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            // S3 semantics: removing a missing object succeeds
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new StorageException("Could not delete stored file " + key, e);
        }
    }

    private void createBucketIfMissing() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new StorageException("Could not prepare bucket " + bucket + " at the object storage", e);
        }
    }
}
