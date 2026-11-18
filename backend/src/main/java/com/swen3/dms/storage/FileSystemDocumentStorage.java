package com.swen3.dms.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Keeps each file in one directory under a random UUID name. Keys are validated as UUIDs,
 * so a key can never point outside the storage directory.
 */
@Component
public class FileSystemDocumentStorage implements DocumentStorage {

    private static final Pattern KEY_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final Path root;

    public FileSystemDocumentStorage(StorageProperties properties) {
        this.root = properties.location().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new StorageException("Could not create storage directory " + root, e);
        }
    }

    @Override
    public String store(InputStream content) {
        String key = UUID.randomUUID().toString();
        Path target = resolve(key);
        try {
            Files.copy(content, target);
            return key;
        } catch (IOException e) {
            deleteQuietly(target);
            throw new StorageException("Could not store file", e);
        }
    }

    @Override
    public Resource load(String key) {
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            throw new StorageException("No stored file for key " + key);
        }
        return new FileSystemResource(file);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Could not delete stored file " + key, e);
        }
    }

    private Path resolve(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new StorageException("Invalid storage key");
        }
        return root.resolve(key);
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // best effort cleanup after a failed write
        }
    }
}
