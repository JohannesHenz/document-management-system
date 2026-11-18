package com.swen3.dms.document;

/** Thrown when an upload or update is rejected because of its input. */
public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException(String message) {
        super(message);
    }
}
