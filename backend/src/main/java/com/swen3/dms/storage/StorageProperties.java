package com.swen3.dms.storage;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** @param location directory in which uploaded files are stored */
@Validated
@ConfigurationProperties("dms.storage")
public record StorageProperties(@NotNull Path location) {
}
