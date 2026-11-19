package com.swen3.dms.ocr;

import com.swen3.dms.document.DocumentService;
import com.swen3.dms.ocr.OcrMessages.OcrResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "dms.ocr.enabled", havingValue = "true")
class OcrResultListener {

    private static final Logger log = LoggerFactory.getLogger(OcrResultListener.class);

    private final DocumentService documents;

    OcrResultListener(DocumentService documents) {
        this.documents = documents;
    }

    @RabbitListener(queues = RabbitConfig.RESULT_QUEUE)
    void onResult(OcrResult result) {
        if (result.success()) {
            documents.completeTextExtraction(result.documentId(), result.text());
        } else {
            log.warn("Text extraction failed for document {}: {}", result.documentId(), result.error());
            documents.failTextExtraction(result.documentId());
        }
    }
}
