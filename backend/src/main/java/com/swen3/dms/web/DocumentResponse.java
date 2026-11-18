package com.swen3.dms.web;

import com.swen3.dms.document.Document;
import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(UUID id, String title, String filename, String contentType, long sizeBytes,
                               Instant uploadedAt) {

    static DocumentResponse from(Document document) {
        return new DocumentResponse(document.getId(), document.getTitle(), document.getOriginalFilename(),
                document.getContentType(), document.getSizeBytes(), document.getUploadedAt());
    }
}
