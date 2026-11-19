package com.swen3.dms.search;

import java.util.UUID;

/**
 * A document matching a search.
 *
 * @param snippet an excerpt of the extracted text around the match, or null if the match was elsewhere
 */
public record SearchHit(UUID documentId, String snippet) {
}
