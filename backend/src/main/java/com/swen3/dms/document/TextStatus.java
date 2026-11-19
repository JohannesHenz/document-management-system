package com.swen3.dms.document;

/** Where text extraction for a document stands. */
public enum TextStatus {
    /** Queued for the OCR worker. */
    PENDING,
    /** Text was extracted and is searchable. */
    COMPLETED,
    /** The worker could not extract text. */
    FAILED,
    /** Extraction is disabled or the file type is not supported. */
    NOT_EXTRACTED
}
