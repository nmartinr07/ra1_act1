package com.fencgut961.aad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class StoreIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(StoreIngestionScheduler.class);
    private static final int BUFFER_SIZE = 4096;

    private final CryptoService cryptoService;
    private final CloudBucketService bucketService;
    private final Path input;
    private final Path processed;
    private final Path signatures;
    private final Path error;
    private int batchCounter = 0;

    public StoreIngestionScheduler(CryptoService cryptoService,
                                   CloudBucketService bucketService,
                                   @Value("${store.base-path:workspace/store_data}") String basePath) {
        this.cryptoService = cryptoService;
        this.bucketService = bucketService;
        Path base = Paths.get(basePath);
        this.input = base.resolve("input");
        this.processed = base.resolve("output/processed");
        this.signatures = base.resolve("output/processed/signatures");
        this.error = base.resolve("output/error");
    }

    @Scheduled(initialDelay = 3000, fixedRate = 5000)
    public void processStoreBatches() {
        try {
            Files.createDirectories(input);
            Files.createDirectories(processed);
            Files.createDirectories(signatures);
            Files.createDirectories(error);

            List<Path> files = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(input)) {
                for (Path p : stream) {
                    if (Files.isRegularFile(p)) files.add(p);
                }
            }
            if (files.isEmpty()) return;

            log.info("Escaneando {}: {} fichero(s) encontrados", input, files.size());
            for (Path file : files) {
                processFile(file);
            }
        } catch (Exception e) {
            log.error("Error en el ciclo de ingestion", e);
        }
    }

    private void processFile(Path file) {
        String name = file.getFileName().toString();
        String lower = name.toLowerCase();
        try {
            if (Files.size(file) == 0) {
                moveToError(file);
                return;
            }
            if (lower.endsWith(".csv")) {
                processCsv(file);
            } else if (lower.endsWith(".enc")) {
                processEnc(file);
            } else if (lower.endsWith(".zip")) {
                processZip(file);
            } else if (lower.endsWith(".jpg")) {
                copySignature(file);
            } else {
                log.warn("Fichero ignorado (extension no soportada): {}", name);
            }
        } catch (Exception e) {
            log.error("Fichero corrupto o ilegible: {} -> {}", name, e.getMessage());
            try {
                moveToError(file);
            } catch (IOException ex) {
                log.error("No se pudo mover a error: {}", name, ex);
            }
        }
    }

    private void moveToError(Path file) throws IOException {
        String name = file.getFileName().toString();
        log.error("[ALERT EMAIL SENT -> soporte@aadtex.com] CRITICAL: Empty/Corrupt batch file: {}", name);
        Files.move(file, error.resolve(name), StandardCopyOption.REPLACE_EXISTING);
    }

    // ---------- CSV ----------
    private void processCsv(Path file) throws IOException {
        String name = file.getFileName().toString();
        try (InputStream in = Files.newInputStream(file)) {
            BigDecimal total = readCsv(in, name);
            writeSummary(name, "csv", total);
        }
        Files.move(file, processed.resolve(name), StandardCopyOption.REPLACE_EXISTING);
    }

    // ---------- ENC ----------
    private void processEnc(Path file) throws Exception {
        String name = file.getFileName().toString();
        byte[] encrypted = Files.readAllBytes(file);
        byte[] plain = cryptoService.decrypt(encrypted);
        try (InputStream in = new ByteArrayInputStream(plain)) {
            BigDecimal total = readCsv(in, name);
            writeSummary(name, "enc", total);
        }
        Files.move(file, processed.resolve(name), StandardCopyOption.REPLACE_EXISTING);
    }

    // ---------- ZIP ----------
    private void processZip(Path file) throws IOException {
        String name = file.getFileName().toString();
        BigDecimal total = BigDecimal.ZERO;
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = Paths.get(entry.getName()).getFileName().toString();
                if (entry.isDirectory()) continue;
                if (entryName.toLowerCase().endsWith(".csv")) {
                    // lee el CSV directamente del ZIP, sin volcar a disco
                    total = total.add(readCsv(nonClosing(zis), name + "!" + entryName));
                } else if (entryName.toLowerCase().endsWith(".jpg")) {
                    // copia la firma desde el ZIP con buffer de 4 KB
                    try (BufferedOutputStream out = new BufferedOutputStream(
                            Files.newOutputStream(signatures.resolve(entryName)))) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                    log.info("Firma extraida del ZIP: {}", entryName);
                }
                zis.closeEntry();
            }
        }
        writeSummary(name, "zip", total);
        Files.move(file, processed.resolve(name), StandardCopyOption.REPLACE_EXISTING);
    }

    // ---------- JPG ----------
    private void copySignature(Path file) throws IOException {
        String name = file.getFileName().toString();
        Path target = signatures.resolve(name);
        try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(file), BUFFER_SIZE);
             BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(target), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        Files.delete(file);
        log.info("Firma copiada con buffer de 4 KB: {}", name);
    }

    // ---------- Utilidades ----------
    private BigDecimal readCsv(InputStream in, String source) throws IOException {
        BigDecimal total = BigDecimal.ZERO;
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line = reader.readLine(); // cabecera
        if (line == null) throw new IOException("CSV sin contenido");
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            String[] cols = line.split(",");
            if (cols.length < 5) throw new IOException("Linea invalida: " + line);
            total = total.add(new BigDecimal(cols[2].trim()));
            log.info("[{}] {} | {} | {} | {}", source, cols[0], cols[1], cols[2], cols[3]);
        }
        log.info("[{}] Importe total del lote: {}", source, total);
        return total;
    }

    private InputStream nonClosing(InputStream in) {
        return new FilterInputStream(in) {
            @Override
            public void close() { /* no cerrar el ZipInputStream */ }
        };
    }

    private void writeSummary(String source, String type, BigDecimal total) throws IOException {
        batchCounter++;
        String json = "{\n"
                + "  \"batchId\": " + batchCounter + ",\n"
                + "  \"sourceFile\": \"" + source.replace("\"", "'") + "\",\n"
                + "  \"type\": \"" + type + "\",\n"
                + "  \"totalSales\": " + total.toPlainString() + "\n"
                + "}\n";
        Path out = bucketService.upload("summary_batch_" + batchCounter + ".json", json);
        log.info("Resumen JSON publicado: {}", out);
    }
}