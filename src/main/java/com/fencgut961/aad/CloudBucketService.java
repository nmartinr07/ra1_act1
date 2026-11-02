package com.fencgut961.aad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class CloudBucketService {

    private final Path bucketPath;

    public CloudBucketService(@Value("${store.base-path:workspace/store_data}") String basePath) {
        this.bucketPath = Paths.get(basePath, "output", "bucket", "aadtex-daily-reports", "2026", "09");
    }

    public Path upload(String fileName, String json) throws IOException {
        Files.createDirectories(bucketPath);
        Path target = bucketPath.resolve(fileName);
        Files.writeString(target, json, StandardCharsets.UTF_8);
        return target;
    }
}