package com.fencgut961.aad;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class CloudBucketService {

    // carpeta que simula el bucket en la nube
    private final Path carpeta = Paths.get("workspace/store_data/output/bucket/aadtex-daily-reports/2026/09");

    public Path guardar(String nombre, String json) throws IOException {
        Files.createDirectories(carpeta);
        Path destino = carpeta.resolve(nombre);
        Files.writeString(destino, json, StandardCharsets.UTF_8);
        return destino;
    }
}