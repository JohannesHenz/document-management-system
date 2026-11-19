package com.swen3.dms.search;

import com.swen3.dms.document.DocumentRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Searches with SQL {@code LIKE}. Used when Elasticsearch is disabled, e.g. for local development and tests.
 */
@Component
@ConditionalOnProperty(name = "dms.search.elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
class DatabaseDocumentSearch implements DocumentSearch {

    private final DocumentRepository repository;

    DatabaseDocumentSearch(DocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchHit> search(String query) {
        String term = query.strip();
        String pattern = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
        return repository.search(pattern).stream()
                .map(document -> new SearchHit(document.getId(), Snippets.around(document.getExtractedText(), term)))
                .toList();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
