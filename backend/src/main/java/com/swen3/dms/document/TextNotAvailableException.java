package com.swen3.dms.document;

import java.util.UUID;

/** Thrown when the extracted text of a document is requested before extraction succeeded. */
public class TextNotAvailableException extends RuntimeException {

    public TextNotAvailableException(UUID id, TextStatus status) {
        super("Document " + id + " has no extracted text (status " + status + ").");
    }
}
