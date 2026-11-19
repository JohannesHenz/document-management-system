package com.swen3.dms.worker;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param tesseractCommand  the Tesseract executable
 * @param languages         Tesseract languages, e.g. {@code eng+deu}
 * @param dpi               resolution at which scanned PDF pages are rendered for OCR
 * @param maxPages          pages beyond this are not processed, to bound the work per document
 * @param timeoutPerPage    how long Tesseract may take for one page
 */
@Validated
@ConfigurationProperties("dms.ocr")
public record WorkerProperties(
        @NotBlank String tesseractCommand,
        @NotBlank String languages,
        @Min(72) @Max(600) int dpi,
        @Min(1) int maxPages,
        Duration timeoutPerPage) {
}
