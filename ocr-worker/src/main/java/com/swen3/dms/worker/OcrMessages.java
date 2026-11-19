package com.swen3.dms.worker;

import java.util.UUID;

/** The JSON messages exchanged with the backend, which declares the same records. */
public final class OcrMessages {

    private OcrMessages() {
    }

    public record OcrJob(UUID documentId, String storageKey, String contentType) {
    }

    public record OcrResult(UUID documentId, boolean success, String text, String error) {

        static OcrResult success(UUID documentId, String text) {
            return new OcrResult(documentId, true, text, null);
        }

        static OcrResult failure(UUID documentId, String error) {
            return new OcrResult(documentId, false, null, error);
        }
    }
}
