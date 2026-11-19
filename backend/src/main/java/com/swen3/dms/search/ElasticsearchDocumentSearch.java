package com.swen3.dms.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Searches titles, file names and extracted text in Elasticsearch. Combines prefix matching, so results appear
 * while typing, with fuzzy matching, which tolerates OCR errors and typos.
 */
@Component
@ConditionalOnProperty(name = "dms.search.elasticsearch.enabled", havingValue = "true")
class ElasticsearchDocumentSearch implements DocumentSearch {

    static final List<String> FIELDS = List.of("title^3", "filename^2", "content");
    private static final int MAX_RESULTS = 100;
    private static final int SNIPPET_LENGTH = 160;

    private final ElasticsearchClient client;
    private final String index;

    ElasticsearchDocumentSearch(ElasticsearchClient client, ElasticsearchProperties properties) {
        this.client = client;
        this.index = properties.index();
    }

    @Override
    public List<SearchHit> search(String query) {
        String text = query.strip();
        try {
            SearchResponse<Void> response = client.search(search -> search
                    .index(index)
                    .size(MAX_RESULTS)
                    .source(source -> source.fetch(false))
                    .query(q -> q.bool(bool -> bool
                            .should(s -> s.multiMatch(m -> m
                                    .query(text).fields(FIELDS).type(TextQueryType.BoolPrefix).operator(Operator.And)))
                            .should(s -> s.multiMatch(m -> m
                                    .query(text).fields(FIELDS).fuzziness("AUTO").operator(Operator.And)))
                            .minimumShouldMatch("1")))
                    .highlight(highlight -> highlight
                            .fields("content", field -> field
                                    .fragmentSize(SNIPPET_LENGTH)
                                    .numberOfFragments(1)
                                    .preTags("")
                                    .postTags(""))),
                    Void.class);
            return response.hits().hits().stream().map(ElasticsearchDocumentSearch::toSearchHit).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Elasticsearch search failed", e);
        }
    }

    private static SearchHit toSearchHit(Hit<Void> hit) {
        List<String> fragments = hit.highlight().get("content");
        String snippet = fragments == null || fragments.isEmpty()
                ? null
                : fragments.get(0).replaceAll("\\s+", " ").strip();
        return new SearchHit(UUID.fromString(hit.id()), snippet);
    }
}
