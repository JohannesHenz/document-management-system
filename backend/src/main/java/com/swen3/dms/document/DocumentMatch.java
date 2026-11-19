package com.swen3.dms.document;

/**
 * A document in a listing or search result.
 *
 * @param snippet excerpt of the extracted text around the search match, or null
 */
public record DocumentMatch(Document document, String snippet) {
}
