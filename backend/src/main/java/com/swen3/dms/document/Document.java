package com.swen3.dms.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Metadata of an uploaded document. The file content itself lives in document storage. */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, unique = true, length = 64)
    private String storageKey;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "text_status", nullable = false, length = 16)
    private TextStatus textStatus = TextStatus.NOT_EXTRACTED;

    @Column(name = "extracted_text", columnDefinition = "text")
    private String extractedText;

    protected Document() {
        // for JPA
    }

    public Document(String title, String originalFilename, String contentType, long sizeBytes,
                    String storageKey, Instant uploadedAt) {
        this.title = title;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void rename(String newTitle) {
        this.title = newTitle;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public TextStatus getTextStatus() {
        return textStatus;
    }

    public String getExtractedText() {
        return extractedText;
    }

    void markTextPending() {
        this.textStatus = TextStatus.PENDING;
        this.extractedText = null;
    }

    void completeText(String text) {
        this.textStatus = TextStatus.COMPLETED;
        this.extractedText = text;
    }

    void failText() {
        this.textStatus = TextStatus.FAILED;
        this.extractedText = null;
    }
}
