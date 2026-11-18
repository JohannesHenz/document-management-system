package com.swen3.dms.document;

import org.springframework.core.io.Resource;

/** A document together with its stored file, for downloads. */
public record DocumentContent(Document document, Resource resource) {
}
