package com.zju.imagemanger.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public interface StorageService {
    void put(String objectKey, InputStream input, long size, String contentType) throws IOException;
    InputStream get(String objectKey) throws IOException;
    Path materialize(String objectKey) throws IOException;
    void delete(String objectKey) throws IOException;
    Optional<String> generateSignedUrl(String objectKey);
    List<StoredObject> listObjects() throws IOException;

    record StoredObject(String objectKey, Instant lastModified) {}
}
