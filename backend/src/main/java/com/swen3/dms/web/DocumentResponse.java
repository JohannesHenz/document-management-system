package com.swen3.dms.web;

import com.swen3.dms.document.Document;
import com.swen3.dms.document.DocumentMatch;
import com.swen3.dms.document.TextStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * @param snippet excerpt of the extracted text around a search match; only set in search results
 */
public record DocumentResponse(UUID id, String title, String filename, String contentType, long sizeBytes,
                               Instant uploadedAt, TextStatus textStatus, String snippet) {

    static DocumentResponse from(Document document) {
        return from(new DocumentMatch(document, null));
    }

    static DocumentResponse from(DocumentMatch match) {
        Document document = match.document();
        return new DocumentResponse(document.getId(), document.getTitle(), document.getOriginalFilename(),
                document.getContentType(), document.getSizeBytes(), document.getUploadedAt(),
                document.getTextStatus(), match.snippet());
    }
}
