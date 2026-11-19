package com.swen3.dms.ocr;

import java.util.UUID;

/**
 * The JSON messages exchanged with the OCR worker. The worker declares the same records; they are matched by
 * shape, not by Java class name.
 */
public final class OcrMessages {

    private OcrMessages() {
    }

    public record OcrJob(UUID documentId, String storageKey, String contentType) {
    }

    /** @param error why extraction failed; null on success */
    public record OcrResult(UUID documentId, boolean success, String text, String error) {
    }
}
