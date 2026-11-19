package com.swen3.dms.storage;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Connection settings for S3-compatible object storage, used when {@code dms.storage.type=minio}. */
@Validated
@ConfigurationProperties("dms.storage.minio")
public record MinioProperties(String endpoint, String accessKey, String secretKey, @NotBlank String bucket) {
}
