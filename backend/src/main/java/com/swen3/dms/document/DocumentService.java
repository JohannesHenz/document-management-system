package com.swen3.dms.document;

import com.swen3.dms.document.DocumentEvents.DocumentChanged;
import com.swen3.dms.document.DocumentEvents.DocumentDeleted;
import com.swen3.dms.document.DocumentEvents.TextExtractionRequested;
import com.swen3.dms.ocr.OcrProperties;
import com.swen3.dms.search.DocumentSearch;
import com.swen3.dms.search.SearchHit;
import com.swen3.dms.storage.DocumentStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class DocumentService {

    static final int MAX_TEXT_LENGTH = 255;
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository repository;
    private final DocumentStorage storage;
    private final DocumentSearch search;
    private final OcrProperties ocr;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DocumentService(DocumentRepository repository, DocumentStorage storage, DocumentSearch search,
                           OcrProperties ocr, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.search = search;
        this.ocr = ocr;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Document upload(MultipartFile file, String title) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("The uploaded file is empty.");
        }
        String filename = cleanFilename(file.getOriginalFilename());
        String effectiveTitle = StringUtils.hasText(title) ? title.strip() : filename;
        requireValidText("Title", effectiveTitle);
        requireValidText("File name", filename);
        String contentType = StringUtils.hasText(file.getContentType()) ? file.getContentType() : DEFAULT_CONTENT_TYPE;

        String storageKey;
        try (InputStream in = file.getInputStream()) {
            storageKey = storage.store(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file.", e);
        }

        Document document = new Document(effectiveTitle, filename, contentType, file.getSize(),
                storageKey, Instant.now(clock).truncatedTo(ChronoUnit.MICROS)); // database precision
        if (ocr.accepts(contentType)) {
            document.markTextPending();
        }
        try {
            document = repository.saveAndFlush(document);
        } catch (RuntimeException e) {
            storage.delete(storageKey); // do not leave an orphaned file behind
            throw e;
        }
        if (document.getTextStatus() == TextStatus.PENDING) {
            events.publishEvent(new TextExtractionRequested(document.getId(), storageKey, contentType));
        }
        events.publishEvent(new DocumentChanged(document.getId()));
        return document;
    }

    /** Lists all documents newest first, or searches titles, file names and extracted text. */
    public List<DocumentMatch> list(String query) {
        if (!StringUtils.hasText(query)) {
            return repository.findAllByOrderByUploadedAtDesc().stream()
                    .map(document -> new DocumentMatch(document, null))
                    .toList();
        }
        List<SearchHit> hits = search.search(query);
        Map<UUID, Document> documents = repository.findAllById(hits.stream().map(SearchHit::documentId).toList())
                .stream().collect(Collectors.toMap(Document::getId, Function.identity()));
        return hits.stream()
                .filter(hit -> documents.containsKey(hit.documentId())) // the index may briefly lag behind deletes
                .map(hit -> new DocumentMatch(documents.get(hit.documentId()), hit.snippet()))
                .toList();
    }

    public Document get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    public String getText(UUID id) {
        Document document = get(id);
        if (document.getTextStatus() != TextStatus.COMPLETED) {
            throw new TextNotAvailableException(id, document.getTextStatus());
        }
        return Objects.requireNonNullElse(document.getExtractedText(), "");
    }

    public DocumentContent getContent(UUID id) {
        Document document = get(id);
        return new DocumentContent(document, storage.load(document.getStorageKey()));
    }

    @Transactional
    public Document rename(UUID id, String title) {
        String newTitle = title == null ? "" : title.strip();
        requireValidText("Title", newTitle);
        Document document = get(id);
        document.rename(newTitle);
        events.publishEvent(new DocumentChanged(id));
        return document;
    }

    /** Stores the text extracted by the OCR worker. Results for deleted documents are ignored. */
    @Transactional
    public void completeTextExtraction(UUID id, String text) {
        repository.findById(id).ifPresentOrElse(document -> {
            document.completeText(text);
            events.publishEvent(new DocumentChanged(id));
        }, () -> log.info("Ignoring extracted text for deleted document {}", id));
    }

    /** Runs in its own transaction, as it is also called after the upload transaction has committed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failTextExtraction(UUID id) {
        repository.findById(id).ifPresent(document -> {
            document.failText();
            events.publishEvent(new DocumentChanged(id));
        });
    }

    @Transactional
    public void delete(UUID id) {
        Document document = get(id);
        repository.delete(document);
        repository.flush();
        events.publishEvent(new DocumentDeleted(id));
        try {
            storage.delete(document.getStorageKey());
        } catch (RuntimeException e) {
            // The metadata is gone, so the document is deleted for users; an orphaned file is only wasted space.
            log.warn("Could not delete stored file {} of document {}", document.getStorageKey(), id, e);
        }
    }

    private static String cleanFilename(String originalFilename) {
        // Browsers may send a full client-side path; keep only the last segment.
        String name = StringUtils.getFilename(StringUtils.cleanPath(originalFilename == null ? "" : originalFilename));
        return StringUtils.hasText(name) ? name.strip() : "unnamed";
    }

    private static void requireValidText(String field, String value) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidDocumentException(field + " must not be blank.");
        }
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new InvalidDocumentException(field + " must be at most " + MAX_TEXT_LENGTH + " characters.");
        }
    }
}
