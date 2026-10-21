package com.zju.imagemanger.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.List;
import java.util.stream.Stream;

@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {
    private final Path root;

    public LocalStorageService(@Value("${file.upload-dir}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void put(String objectKey, InputStream input, long size, String contentType) throws IOException {
        Path target = resolve(objectKey);
        Files.createDirectories(target.getParent());
        Files.copy(input, target);
    }

    @Override
    public InputStream get(String objectKey) throws IOException {
        return Files.newInputStream(resolve(objectKey));
    }

    @Override
    public Path materialize(String objectKey) {
        return resolve(objectKey);
    }

    @Override
    public void delete(String objectKey) throws IOException {
        Files.deleteIfExists(resolve(objectKey));
    }

    @Override
    public Optional<String> generateSignedUrl(String objectKey) {
        return Optional.empty();
    }

    @Override
    public List<StoredObject> listObjects() throws IOException {
        if (!Files.exists(root)) return List.of();
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).map(path -> {
                try {
                    String key = root.relativize(path).toString().replace('\\', '/');
                    return new StoredObject(key, Files.getLastModifiedTime(path).toInstant());
                } catch (IOException error) {
                    throw new java.io.UncheckedIOException(error);
                }
            }).toList();
        } catch (java.io.UncheckedIOException error) {
            throw error.getCause();
        }
    }

    private Path resolve(String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid object key");
        }
        return target;
    }
}
