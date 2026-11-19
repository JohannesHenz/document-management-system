package com.swen3.dms.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.swen3.dms.worker.OcrMessages.OcrJob;
import com.swen3.dms.worker.OcrMessages.OcrResult;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Sends jobs through RabbitMQ with files in MinIO, as the backend does, and checks the results. */
@SpringBootTest
@Testcontainers
class OcrWorkerIntegrationTest {

    private static final String BUCKET = "documents";

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    /** MinIO no longer publishes community images; this is a community-maintained build of the same server. */
    @Container
    static final MinIOContainer MINIO = new MinIOContainer(
            DockerImageName.parse("pgsty/minio:RELEASE.2026-08-04T00-00-00Z").asCompatibleSubstituteFor("minio/minio"));

    @DynamicPropertySource
    static void minio(DynamicPropertyRegistry registry) {
        registry.add("dms.storage.minio.endpoint", MINIO::getS3URL);
        registry.add("dms.storage.minio.access-key", MINIO::getUserName);
        registry.add("dms.storage.minio.secret-key", MINIO::getPassword);
        registry.add("dms.storage.minio.bucket", () -> BUCKET);
    }

    @BeforeAll
    static void createBucket() throws Exception {
        minioClient().makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
    }

    @Autowired
    RabbitTemplate rabbit;

    @Test
    void extractsTextAndReportsSuccess() throws Exception {
        String key = UUID.randomUUID().toString();
        byte[] pdf = TestDocuments.textPdf("Kaufvertrag zwischen A und B");
        minioClient().putObject(PutObjectArgs.builder().bucket(BUCKET).object(key)
                .stream(new ByteArrayInputStream(pdf), pdf.length, -1).build());
        UUID documentId = UUID.randomUUID();

        sendJob(new OcrJob(documentId, key, "application/pdf"));

        OcrResult result = receiveResult();
        assertThat(result.documentId()).isEqualTo(documentId);
        assertThat(result.success()).isTrue();
        assertThat(result.text()).isEqualTo("Kaufvertrag zwischen A und B");
    }

    @Test
    void reportsFailureForMissingFile() {
        UUID documentId = UUID.randomUUID();

        sendJob(new OcrJob(documentId, "missing-key", "application/pdf"));

        OcrResult result = receiveResult();
        assertThat(result.documentId()).isEqualTo(documentId);
        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotBlank();
    }

    private void sendJob(OcrJob job) {
        rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.JOB_ROUTING_KEY, job);
    }

    private OcrResult receiveResult() {
        OcrResult result = rabbit.receiveAndConvert(RabbitConfig.RESULT_QUEUE, 15_000,
                new ParameterizedTypeReference<OcrResult>() { });
        assertThat(result).as("OCR result").isNotNull();
        return result;
    }

    private static MinioClient minioClient() {
        return MinioClient.builder().endpoint(MINIO.getS3URL())
                .credentials(MINIO.getUserName(), MINIO.getPassword()).build();
    }
}
