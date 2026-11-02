package com.fencgut961.aad;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudBucketServiceTest {

    @TempDir
    Path temporal;

    @Test
    void elIdNoPisaResumenesAnteriores() throws Exception {
        CloudBucketService bucket = new CloudBucketService(new Rutas(temporal.toString()));

        assertEquals(1, bucket.siguienteId());

        Path guardado = bucket.guardar("summary_batch_1.json", "{}");

        assertTrue(Files.exists(guardado));
        assertEquals(2, bucket.siguienteId());
    }
}