package com.swen3.dms.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param index name of the Elasticsearch index holding the searchable documents */
@ConfigurationProperties("dms.search.elasticsearch")
public record ElasticsearchProperties(boolean enabled, String index) {
}
