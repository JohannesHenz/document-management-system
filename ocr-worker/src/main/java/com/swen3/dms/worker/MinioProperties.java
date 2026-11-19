package com.swen3.dms.worker;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("dms.storage.minio")
public record MinioProperties(@NotBlank String endpoint, String accessKey, String secretKey, @NotBlank String bucket) {
}
