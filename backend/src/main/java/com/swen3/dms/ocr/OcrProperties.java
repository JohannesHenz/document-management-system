package com.swen3.dms.ocr;

import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.MediaType;

/**
 * @param enabled      whether uploads are sent to the OCR worker via RabbitMQ
 * @param contentTypes the media types the worker can extract text from
 */
@ConfigurationProperties("dms.ocr")
public record OcrProperties(boolean enabled, Set<String> contentTypes) {

    public OcrProperties {
        contentTypes = contentTypes == null ? Set.of() : Set.copyOf(contentTypes);
    }

    public boolean accepts(String contentType) {
        if (!enabled || contentType == null) {
            return false;
        }
        try {
            MediaType type = MediaType.parseMediaType(contentType);
            return contentTypes.contains((type.getType() + "/" + type.getSubtype()).toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
