package com.swen3.dms.web;

import com.swen3.dms.document.Document;
import com.swen3.dms.document.DocumentContent;
import com.swen3.dms.document.DocumentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestPart("file") MultipartFile file,
                                                   @RequestParam(required = false) String title) {
        Document document = documentService.upload(file, title);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(document.getId()).toUri();
        return ResponseEntity.created(location).body(DocumentResponse.from(document));
    }

    /** Lists all documents, newest first, or only those whose title or file name contains {@code q}. */
    @GetMapping
    public List<DocumentResponse> list(@RequestParam(required = false) String q) {
        return documentService.list(q).stream().map(DocumentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) {
        return DocumentResponse.from(documentService.get(id));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        DocumentContent content = documentService.getContent(id);
        Document document = content.document();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .contentLength(document.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(document.getOriginalFilename()).toString())
                .body(content.resource());
    }

    /** Plain {@code filename} for ASCII names; RFC 5987 {@code filename*} only when it is needed. */
    private static ContentDisposition attachment(String filename) {
        ContentDisposition.Builder builder = ContentDisposition.attachment();
        boolean ascii = StandardCharsets.US_ASCII.newEncoder().canEncode(filename);
        return (ascii ? builder.filename(filename) : builder.filename(filename, StandardCharsets.UTF_8)).build();
    }

    @PatchMapping("/{id}")
    public DocumentResponse rename(@PathVariable UUID id, @Valid @RequestBody UpdateDocumentRequest request) {
        return DocumentResponse.from(documentService.rename(id, request.title()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
