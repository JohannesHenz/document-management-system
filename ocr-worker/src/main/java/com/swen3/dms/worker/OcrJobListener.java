package com.swen3.dms.worker;

import com.swen3.dms.worker.OcrMessages.OcrJob;
import com.swen3.dms.worker.OcrMessages.OcrResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Processes one OCR job at a time. Every job is answered: a file that cannot be read or recognized yields a
 * failure result, so the document never stays pending.
 */
@Component
public class OcrJobListener {

    private static final Logger log = LoggerFactory.getLogger(OcrJobListener.class);

    private final DocumentFiles files;
    private final TextExtractor extractor;
    private final RabbitTemplate rabbit;

    public OcrJobListener(DocumentFiles files, TextExtractor extractor, RabbitTemplate rabbit) {
        this.files = files;
        this.extractor = extractor;
        this.rabbit = rabbit;
    }

    @RabbitListener(queues = RabbitConfig.JOB_QUEUE)
    public void onJob(OcrJob job) {
        OcrResult result;
        long start = System.nanoTime();
        try {
            String text = extractor.extract(files.read(job.storageKey()), job.contentType());
            result = OcrResult.success(job.documentId(), text);
            log.info("Extracted {} characters from document {} in {} ms", text.length(), job.documentId(),
                    (System.nanoTime() - start) / 1_000_000);
        } catch (Exception e) {
            log.warn("Text extraction failed for document {}", job.documentId(), e);
            result = OcrResult.failure(job.documentId(), e.getMessage());
        }
        rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RESULT_ROUTING_KEY, result);
    }
}
