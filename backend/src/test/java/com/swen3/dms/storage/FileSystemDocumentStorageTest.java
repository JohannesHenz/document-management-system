package com.swen3.dms.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemDocumentStorageTest {

    @TempDir
    Path root;

    FileSystemDocumentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new FileSystemDocumentStorage(new StorageProperties(root.resolve("documents")));
    }

    @Test
    void storesLoadsAndDeletesContent() throws Exception {
        String key = storage.store(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        assertThat(storage.load(key).getContentAsString(StandardCharsets.UTF_8)).isEqualTo("hello");

        storage.delete(key);
        assertThatThrownBy(() -> storage.load(key)).isInstanceOf(StorageException.class);
    }

    @Test
    void eachStoreGetsItsOwnKey() {
        String first = storage.store(new ByteArrayInputStream(new byte[] {1}));
        String second = storage.store(new ByteArrayInputStream(new byte[] {1}));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void deletingAMissingFileIsANoOp() {
        storage.delete("00000000-0000-0000-0000-000000000000");
    }

    @Test
    void rejectsKeysThatAreNotGeneratedByTheStorage() {
        assertThatThrownBy(() -> storage.load("../../etc/passwd")).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.delete("..")).isInstanceOf(StorageException.class);
    }
}
