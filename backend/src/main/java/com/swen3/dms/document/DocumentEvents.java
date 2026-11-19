package com.swen3.dms.document;

import java.util.UUID;

/**
 * Events published by {@link DocumentService}. Listeners use {@code @TransactionalEventListener}, so they only
 * react once the change is committed.
 */
public final class DocumentEvents {

    private DocumentEvents() {
    }

    /** A file was stored and its text should be extracted. */
    public record TextExtractionRequested(UUID documentId, String storageKey, String contentType) {
    }

    /** A document was created or its title or extracted text changed. */
    public record DocumentChanged(UUID documentId) {
    }

    public record DocumentDeleted(UUID documentId) {
    }
}
