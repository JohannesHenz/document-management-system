package com.swen3.dms.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SnippetsTest {

    @Test
    void returnsWholeShortTextAroundMatch() {
        assertThat(Snippets.around("Invoice  number\n4711", "NUMBER")).isEqualTo("Invoice number 4711");
    }

    @Test
    void cutsLongTextAtWordBoundaries() {
        String text = "word ".repeat(40) + "needle " + "word ".repeat(40);

        String snippet = Snippets.around(text, "needle");

        assertThat(snippet).startsWith("…word").endsWith("word…").contains("needle");
        assertThat(snippet.length()).isLessThanOrEqualTo(2 * Snippets.CONTEXT_CHARS + "needle".length() + 2);
    }

    @Test
    void returnsNullWithoutMatch() {
        assertThat(Snippets.around("Invoice", "receipt")).isNull();
        assertThat(Snippets.around(null, "receipt")).isNull();
    }
}
