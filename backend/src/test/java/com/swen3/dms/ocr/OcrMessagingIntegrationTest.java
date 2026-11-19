package com.swen3.dms.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swen3.dms.document.Document;
import com.swen3.dms.document.DocumentRepository;
import com.swen3.dms.document.DocumentService;
import com.swen3.dms.document.TextStatus;
import com.swen3.dms.ocr.OcrMessages.OcrJob;
import com.swen3.dms.ocr.OcrMessages.OcrResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "dms.ocr.enabled=true")
@AutoConfigureMockMvc
@Testcontainers
class OcrMessagingIntegrationTest {

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storageLocation(DynamicPropertyRegistry registry) {
        registry.add("dms.storage.location", storageDir::toString);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RabbitTemplate rabbit;

    @Autowired
    DocumentRepository repository;

    @Autowired
    DocumentService documentService;

    @BeforeEach
    void reset() {
        repository.deleteAll();
        rabbit.execute(channel -> {
            channel.queuePurge(RabbitConfig.JOB_QUEUE);
            channel.queuePurge(RabbitConfig.RESULT_QUEUE + ".dead");
            return null;
        });
    }

    @Test
    void pdfUploadQueuesAnOcrJob() throws Exception {
        UUID id = upload("scan.pdf", "application/pdf");

        OcrJob job = receiveJob();
        Document document = repository.findById(id).orElseThrow();
        assertThat(job).isEqualTo(new OcrJob(id, document.getStorageKey(), "application/pdf"));
        assertThat(document.getTextStatus()).isEqualTo(TextStatus.PENDING);
    }

    @Test
    void unsupportedTypesAreNotQueued() throws Exception {
        UUID id = upload("notes.txt", "text/plain");

        assertThat(rabbit.receive(RabbitConfig.JOB_QUEUE, 1000)).isNull();
        assertThat(repository.findById(id).orElseThrow().getTextStatus()).isEqualTo(TextStatus.NOT_EXTRACTED);
    }

    @Test
    void successfulResultMakesTextSearchable() throws Exception {
        UUID id = upload("scan.png", "image/png");

        sendResult(new OcrResult(id, true, "Kaufvertrag Wohnung Wien", null));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findById(id).orElseThrow().getTextStatus()).isEqualTo(TextStatus.COMPLETED));
        assertThat(documentService.list("wohnung")).extracting(match -> match.document().getId()).containsExactly(id);
    }

    @Test
    void failedResultMarksDocument() throws Exception {
        UUID id = upload("scan.jpg", "image/jpeg");

        sendResult(new OcrResult(id, false, null, "unreadable image"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findById(id).orElseThrow().getTextStatus()).isEqualTo(TextStatus.FAILED));
    }

    @Test
    void resultForDeletedDocumentIsIgnored() throws Exception {
        sendResult(new OcrResult(UUID.randomUUID(), true, "orphan", null));
        UUID id = upload("scan.pdf", "application/pdf");
        sendResult(new OcrResult(id, true, "still processed", null));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findById(id).orElseThrow().getTextStatus()).isEqualTo(TextStatus.COMPLETED));
        assertThat(rabbit.receive(RabbitConfig.RESULT_QUEUE + ".dead", 500)).isNull();
    }

    @Test
    void malformedResultGoesToDeadLetterQueue() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(RabbitConfig.EXCHANGE, RabbitConfig.RESULT_ROUTING_KEY,
                new Message("not json".getBytes(StandardCharsets.UTF_8), properties));

        Message dead = rabbit.receive(RabbitConfig.RESULT_QUEUE + ".dead", 10_000);
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("not json");
    }

    private UUID upload(String filename, String contentType) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, new byte[] {1, 2, 3});
        String body = mvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private OcrJob receiveJob() {
        OcrJob job = rabbit.receiveAndConvert(RabbitConfig.JOB_QUEUE, 10_000,
                new ParameterizedTypeReference<OcrJob>() { });
        assertThat(job).as("OCR job").isNotNull();
        return job;
    }

    private void sendResult(OcrResult result) {
        rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RESULT_ROUTING_KEY, result);
    }
}
