package com.swen3.dms.search;

import java.util.Locale;

/** Cuts a short excerpt around the first occurrence of a search term. */
final class Snippets {

    static final int CONTEXT_CHARS = 60;

    private Snippets() {
    }

    /** Returns null if the text does not contain the term. */
    static String around(String text, String term) {
        if (text == null || term == null || term.isBlank()) {
            return null;
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        int index = flat.toLowerCase(Locale.ROOT).indexOf(term.strip().toLowerCase(Locale.ROOT));
        if (index < 0) {
            return null;
        }
        int start = Math.max(0, index - CONTEXT_CHARS);
        int end = Math.min(flat.length(), index + term.strip().length() + CONTEXT_CHARS);
        // do not cut words in half
        if (start > 0) {
            int space = flat.indexOf(' ', start);
            start = space >= 0 && space < index ? space + 1 : start;
        }
        if (end < flat.length()) {
            int space = flat.lastIndexOf(' ', end);
            end = space > index ? space : end;
        }
        return (start > 0 ? "…" : "") + flat.substring(start, end) + (end < flat.length() ? "…" : "");
    }
}
