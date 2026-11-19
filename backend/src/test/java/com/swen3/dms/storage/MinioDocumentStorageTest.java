package com.swen3.dms.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MinioDocumentStorageTest {

    @Container
    static final MinIOContainer MINIO = new MinIOContainer(TestImages.MINIO);

    MinioDocumentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new MinioDocumentStorage(new MinioProperties(
                MINIO.getS3URL(), MINIO.getUserName(), MINIO.getPassword(), "documents"));
    }

    @Test
    void storesLoadsAndDeletesContent() throws Exception {
        String key = storage.store(new ByteArrayInputStream("hello minio".getBytes(StandardCharsets.UTF_8)));

        assertThat(storage.load(key).getContentAsString(StandardCharsets.UTF_8)).isEqualTo("hello minio");

        storage.delete(key);
        assertThatThrownBy(() -> storage.load(key)).isInstanceOf(StorageException.class);
    }

    @Test
    void storesFilesLargerThanOnePart() throws Exception {
        byte[] content = new byte[12 * 1024 * 1024];
        content[content.length - 1] = 42;

        String key = storage.store(new ByteArrayInputStream(content));

        assertThat(storage.load(key).getContentAsByteArray()).isEqualTo(content);
    }

    @Test
    void deletingMissingContentSucceeds() {
        assertThatCode(() -> storage.delete("does-not-exist")).doesNotThrowAnyException();
    }

    @Test
    void reusesAnExistingBucket() {
        String key = storage.store(new ByteArrayInputStream(new byte[] {1}));

        MinioDocumentStorage second = new MinioDocumentStorage(new MinioProperties(
                MINIO.getS3URL(), MINIO.getUserName(), MINIO.getPassword(), "documents"));

        assertThatCode(() -> second.load(key).getContentAsByteArray()).doesNotThrowAnyException();
    }
}
