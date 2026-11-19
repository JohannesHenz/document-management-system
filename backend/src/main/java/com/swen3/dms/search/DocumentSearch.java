package com.swen3.dms.search;

import java.util.List;

/** Full-text search over document titles, file names and extracted text. */
public interface DocumentSearch {

    /** Returns matching documents, best match first. */
    List<SearchHit> search(String query);
}
