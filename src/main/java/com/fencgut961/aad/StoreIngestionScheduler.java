package com.fencgut961.aad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class StoreIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(StoreIngestionScheduler.class);

    private final Path input = Paths.get("workspace/store_data/input");
    private final Path processed = Paths.get("workspace/store_data/output/processed");
    private final Path signatures = Paths.get("workspace/store_data/output/processed/signatures");
    private final Path error = Paths.get("workspace/store_data/output/error");

    private final CryptoService cryptoService;
    private final CloudBucketService bucketService;
    private int contador = 0;

    public StoreIngestionScheduler(CryptoService cryptoService, CloudBucketService bucketService) {
        this.cryptoService = cryptoService;
        this.bucketService = bucketService;
    }

    // se ejecuta cada 5 segundos (espera 3 segundos al principio para que el generador termine)
    @Scheduled(initialDelay = 3000, fixedRate = 5000)
    public void processStoreBatches() throws IOException {
        // creo las carpetas si no existen
        Files.createDirectories(input);
        Files.createDirectories(processed);
        Files.createDirectories(signatures);
        Files.createDirectories(error);

        // guardo los ficheros de input en una lista
        List<Path> ficheros = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(input)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    ficheros.add(p);
                }
            }
        }

        if (ficheros.isEmpty()) {
            return;
        }
        log.info("Escaneando {}: {} fichero(s) encontrados", input, ficheros.size());

        for (Path fichero : ficheros) {
            procesar(fichero);
        }
    }

    private void procesar(Path fichero) throws IOException {
        String nombre = fichero.getFileName().toString();
        try {
            // fichero vacio
            if (Files.size(fichero) == 0) {
                moverAError(fichero);
                return;
            }

            if (nombre.endsWith(".csv")) {
                procesarCsv(fichero);
            } else if (nombre.endsWith(".enc")) {
                procesarEnc(fichero);
            } else if (nombre.endsWith(".zip")) {
                procesarZip(fichero);
            } else if (nombre.endsWith(".jpg")) {
                procesarJpg(fichero);
            }
        } catch (Exception e) {
            log.error("Fallo al procesar {}: {}", nombre, e.getMessage());
            moverAError(fichero);
        }
    }

    private void moverAError(Path fichero) throws IOException {
        String nombre = fichero.getFileName().toString();
        log.error("[ALERT EMAIL SENT -> soporte@aadtex.com] CRITICAL: Empty/Corrupt batch file: {}", nombre);
        Files.move(fichero, error.resolve(nombre), StandardCopyOption.REPLACE_EXISTING);
    }

    private void procesarCsv(Path fichero) throws IOException {
        String nombre = fichero.getFileName().toString();
        BigDecimal total;
        try (InputStream in = Files.newInputStream(fichero)) {
            total = leerCsv(in, nombre);
        }
        guardarResumen(nombre, total);
        Files.move(fichero, processed.resolve(nombre), StandardCopyOption.REPLACE_EXISTING);
    }

    private void procesarEnc(Path fichero) throws Exception {
        String nombre = fichero.getFileName().toString();
        // leo los bytes cifrados y los descifro en memoria
        byte[] cifrado = Files.readAllBytes(fichero);
        byte[] descifrado = cryptoService.decrypt(cifrado);
        BigDecimal total = leerCsv(new ByteArrayInputStream(descifrado), nombre);
        guardarResumen(nombre, total);
        Files.move(fichero, processed.resolve(nombre), StandardCopyOption.REPLACE_EXISTING);
    }

    private void procesarZip(Path fichero) throws IOException {
        String nombre = fichero.getFileName().toString();
        BigDecimal total = BigDecimal.ZERO;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(fichero))) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                String nombreEntrada = entrada.getName();
                if (nombreEntrada.endsWith(".csv")) {
                    total = total.add(leerCsv(zip, nombre + "/" + nombreEntrada));
                } else if (nombreEntrada.endsWith(".jpg")) {
                    copiar(zip, signatures.resolve(nombreEntrada));
                    log.info("Firma extraida del ZIP: {}", nombreEntrada);
                }
            }
        }
        guardarResumen(nombre, total);
        Files.move(fichero, processed.resolve(nombre), StandardCopyOption.REPLACE_EXISTING);
    }

    private void procesarJpg(Path fichero) throws IOException {
        String nombre = fichero.getFileName().toString();
        try (InputStream in = Files.newInputStream(fichero)) {
            copiar(in, signatures.resolve(nombre));
        }
        Files.delete(fichero);
        log.info("Firma copiada con buffer de 4 KB: {}", nombre);
    }

    // lee el csv en UTF-8 y suma la columna SalesAmount
    private BigDecimal leerCsv(InputStream in, String origen) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        BigDecimal total = BigDecimal.ZERO;
        String linea = reader.readLine(); // salto la cabecera
        while ((linea = reader.readLine()) != null) {
            if (linea.isBlank()) {
                continue;
            }
            String[] campos = linea.split(",");
            total = total.add(new BigDecimal(campos[2]));
            log.info("[{}] {} | {} | {} | {}", origen, campos[0], campos[1], campos[2], campos[3]);
        }
        log.info("[{}] Importe total del lote: {}", origen, total);
        return total;
    }

    // copia binaria con buffer de 4 KB
    private void copiar(InputStream origen, Path destino) throws IOException {
        BufferedInputStream entrada = new BufferedInputStream(origen);
        try (BufferedOutputStream salida = new BufferedOutputStream(Files.newOutputStream(destino))) {
            byte[] buffer = new byte[4096];
            int leidos;
            while ((leidos = entrada.read(buffer)) != -1) {
                salida.write(buffer, 0, leidos);
            }
        }
    }

    private void guardarResumen(String origen, BigDecimal total) throws IOException {
        contador++;
        String json = "{\n"
                + "  \"batchId\": " + contador + ",\n"
                + "  \"sourceFile\": \"" + origen + "\",\n"
                + "  \"totalSales\": " + total.toPlainString() + "\n"
                + "}\n";
        Path ruta = bucketService.guardar("summary_batch_" + contador + ".json", json);
        log.info("Resumen JSON publicado: {}", ruta);
    }
}