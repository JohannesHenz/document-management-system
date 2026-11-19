package com.swen3.dms.search;

import com.swen3.dms.document.Document;

/** What is stored in Elasticsearch for each document; the id is the document's UUID. */
record IndexedDocument(String title, String filename, String content) {

    static IndexedDocument of(Document document) {
        return new IndexedDocument(document.getTitle(), document.getOriginalFilename(), document.getExtractedText());
    }
}
