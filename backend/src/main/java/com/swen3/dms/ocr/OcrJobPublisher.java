package com.swen3.dms.ocr;

import com.swen3.dms.document.DocumentEvents.TextExtractionRequested;
import com.swen3.dms.document.DocumentService;
import com.swen3.dms.ocr.OcrMessages.OcrJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends an OCR job once the upload is committed, so the worker never sees a document that does not exist. */
@Component
@ConditionalOnProperty(name = "dms.ocr.enabled", havingValue = "true")
class OcrJobPublisher {

    private static final Logger log = LoggerFactory.getLogger(OcrJobPublisher.class);

    private final RabbitTemplate rabbit;
    private final DocumentService documents;

    OcrJobPublisher(RabbitTemplate rabbit, DocumentService documents) {
        this.rabbit = rabbit;
        this.documents = documents;
    }

    @TransactionalEventListener
    void onTextExtractionRequested(TextExtractionRequested event) {
        try {
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.JOB_ROUTING_KEY,
                    new OcrJob(event.documentId(), event.storageKey(), event.contentType()));
        } catch (AmqpException e) {
            log.error("Could not queue OCR job for document {}", event.documentId(), e);
            documents.failTextExtraction(event.documentId());
        }
    }
}
