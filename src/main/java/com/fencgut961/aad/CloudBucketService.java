package com.fencgut961.aad;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class CloudBucketService {

    // carpeta que simula el bucket en la nube
    private final Path carpeta;

    public CloudBucketService(Rutas rutas) {
        this.carpeta = rutas.bucket();
    }

    // busca el primer numero libre para no sobrescribir resumenes anteriores
    public int siguienteId() throws IOException {
        Files.createDirectories(carpeta);
        int id = 1;
        while (Files.exists(carpeta.resolve("summary_batch_" + id + ".json"))) {
            id++;
        }
        return id;
    }

    public Path guardar(String nombre, String json) throws IOException {
        Files.createDirectories(carpeta);
        Path destino = carpeta.resolve(nombre);
        Files.writeString(destino, json, StandardCharsets.UTF_8);
        return destino;
    }
}