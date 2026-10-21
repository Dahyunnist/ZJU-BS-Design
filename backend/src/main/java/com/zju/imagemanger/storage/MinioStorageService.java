package com.zju.imagemanger.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "minio")
public class MinioStorageService implements StorageService {
    private final MinioClient client;
    private final String bucket;

    public MinioStorageService(MinioClient client, @Value("${storage.minio.bucket}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void put(String objectKey, InputStream input, long size, String contentType) throws IOException {
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .stream(input, size, -1).contentType(contentType).build());
        } catch (Exception e) {
            throw new IOException("Object storage write failed", e);
        }
    }

    @Override
    public InputStream get(String objectKey) throws IOException {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new IOException("Object storage read failed", e);
        }
    }

    @Override
    public java.nio.file.Path materialize(String objectKey) throws IOException {
        java.nio.file.Path temporary = java.nio.file.Files.createTempFile("image-object-", ".bin");
        try (InputStream input = get(objectKey)) {
            java.nio.file.Files.copy(input, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return temporary;
        } catch (IOException error) {
            java.nio.file.Files.deleteIfExists(temporary);
            throw error;
        }
    }

    @Override
    public void delete(String objectKey) throws IOException {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new IOException("Object storage delete failed", e);
        }
    }

    @Override
    public Optional<String> generateSignedUrl(String objectKey) {
        try {
            return Optional.of(client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(io.minio.http.Method.GET).bucket(bucket).object(objectKey).expiry(300).build()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign object URL", e);
        }
    }

    @Override
    public List<StoredObject> listObjects() throws IOException {
        try {
            List<StoredObject> objects = new ArrayList<>();
            Iterable<Result<Item>> results = client.listObjects(
                    ListObjectsArgs.builder().bucket(bucket).recursive(true).build());
            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir()) objects.add(new StoredObject(item.objectName(), item.lastModified().toInstant()));
            }
            return objects;
        } catch (Exception error) {
            throw new IOException("Object storage listing failed", error);
        }
    }

}
