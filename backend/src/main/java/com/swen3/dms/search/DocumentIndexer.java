package com.swen3.dms.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import com.swen3.dms.document.Document;
import com.swen3.dms.document.DocumentEvents.DocumentChanged;
import com.swen3.dms.document.DocumentEvents.DocumentDeleted;
import com.swen3.dms.document.DocumentRepository;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps the Elasticsearch index in line with the database. PostgreSQL stays the source of truth: changes are
 * indexed after they commit, and a missing index is rebuilt from the database on startup.
 */
@Component
@ConditionalOnProperty(name = "dms.search.elasticsearch.enabled", havingValue = "true")
class DocumentIndexer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexer.class);

    private final ElasticsearchClient client;
    private final DocumentRepository repository;
    private final String index;

    DocumentIndexer(ElasticsearchClient client, DocumentRepository repository, ElasticsearchProperties properties) {
        this.client = client;
        this.repository = repository;
        this.index = properties.index();
    }

    @EventListener(ApplicationReadyEvent.class)
    void createIndexIfMissing() throws IOException {
        if (client.indices().exists(exists -> exists.index(index)).value()) {
            return;
        }
        client.indices().create(create -> create
                .index(index)
                .mappings(mappings -> mappings
                        .properties("title", property -> property.text(text -> text))
                        .properties("filename", property -> property.text(text -> text))
                        .properties("content", property -> property.text(text -> text))));
        reindexAll();
    }

    void reindexAll() throws IOException {
        List<Document> documents = repository.findAll();
        if (documents.isEmpty()) {
            return;
        }
        BulkRequest.Builder bulk = new BulkRequest.Builder().refresh(Refresh.WaitFor);
        for (Document document : documents) {
            bulk.operations(operation -> operation.index(indexOp -> indexOp
                    .index(index).id(document.getId().toString()).document(IndexedDocument.of(document))));
        }
        if (client.bulk(bulk.build()).errors()) {
            log.error("Some documents could not be indexed while rebuilding index {}", index);
        }
        log.info("Indexed {} existing documents into {}", documents.size(), index);
    }

    @TransactionalEventListener
    void onChanged(DocumentChanged event) {
        repository.findById(event.documentId()).ifPresent(document -> {
            try {
                // wait_for: an uploaded or renamed document is searchable as soon as the request returns
                client.index(request -> request.index(index).id(document.getId().toString())
                        .document(IndexedDocument.of(document)).refresh(Refresh.WaitFor));
            } catch (IOException | RuntimeException e) {
                log.error("Could not index document {}", event.documentId(), e);
            }
        });
    }

    @TransactionalEventListener
    void onDeleted(DocumentDeleted event) {
        try {
            client.delete(request -> request.index(index).id(event.documentId().toString()).refresh(Refresh.WaitFor));
        } catch (IOException | RuntimeException e) {
            log.error("Could not remove document {} from the index", event.documentId(), e);
        }
    }
}
