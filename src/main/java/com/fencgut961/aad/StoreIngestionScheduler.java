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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class StoreIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(StoreIngestionScheduler.class);

    private final Path input;
    private final Path processed;
    private final Path signatures;
    private final Path error;

    private final CryptoService cryptoService;
    private final CloudBucketService bucketService;

    // ficheros con extension no soportada, para avisar solo la primera vez
    private final Set<String> ignorados = new HashSet<>();

    public StoreIngestionScheduler(Rutas rutas, CryptoService cryptoService, CloudBucketService bucketService) {
        this.input = rutas.input();
        this.processed = rutas.processed();
        this.signatures = rutas.signatures();
        this.error = rutas.error();
        this.cryptoService = cryptoService;
        this.bucketService = bucketService;
    }

    // se ejecuta cada 5 segundos (espera 3 segundos al principio para que el generador termine)
    @Scheduled(initialDelay = 3000, fixedRate = 5000)
    public void processStoreBatches() throws IOException {
        Files.createDirectories(input);
        Files.createDirectories(processed);
        Files.createDirectories(signatures);
        Files.createDirectories(error);

        List<Path> ficheros = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(input)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p) && !ignorados.contains(p.getFileName().toString())) {
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

    private void procesar(Path fichero) {
        String nombre = fichero.getFileName().toString();
        String minusculas = nombre.toLowerCase();
        try {
            if (Files.size(fichero) == 0) {
                moverAError(fichero);
                return;
            }

            if (minusculas.endsWith(".csv")) {
                procesarCsv(fichero);
            } else if (minusculas.endsWith(".enc")) {
                procesarEnc(fichero);
            } else if (minusculas.endsWith(".zip")) {
                procesarZip(fichero);
            } else if (minusculas.endsWith(".jpg")) {
                procesarJpg(fichero);
            } else {
                if (ignorados.add(nombre)) {
                    log.warn("Fichero ignorado (extension no soportada): {}", nombre);
                }
            }
        } catch (Exception e) {
            log.error("Fallo al procesar {}: {}", nombre, e.getMessage());
            try {
                moverAError(fichero);
            } catch (IOException e2) {
                // si tampoco se puede mover, lo dejo y sigo con el siguiente fichero
                log.error("No se pudo mover {} a error: {}", nombre, e2.getMessage());
            }
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
        // se lee entero a un byte[] porque el enunciado pide descifrar en memoria
        byte[] cifrado = Files.readAllBytes(fichero);
        byte[] descifrado = cryptoService.decrypt(cifrado);
        BigDecimal total = leerCsv(new ByteArrayInputStream(descifrado), nombre);
        guardarResumen(nombre, total);
        Files.move(fichero, processed.resolve(nombre), StandardCopyOption.REPLACE_EXISTING);
    }

    private void procesarZip(Path fichero) throws IOException {
        String nombre = fichero.getFileName().toString();
        BigDecimal total = BigDecimal.ZERO;
        // guardo las firmas que voy sacando por si el zip falla a medias
        List<Path> firmasExtraidas = new ArrayList<>();
        try {
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(fichero))) {
                ZipEntry entrada;
                while ((entrada = zip.getNextEntry()) != null) {
                    if (entrada.isDirectory()) {
                        continue;
                    }
                    // me quedo solo con el nombre del fichero, sin carpetas ni "../"
                    String nombreEntrada = Paths.get(entrada.getName()).getFileName().toString();
                    String minusculas = nombreEntrada.toLowerCase();
                    if (minusculas.endsWith(".csv")) {
                        total = total.add(leerCsv(zip, nombre + "/" + nombreEntrada));
                    } else if (minusculas.endsWith(".jpg")) {
                        Path destino = signatures.resolve(nombreEntrada);
                        copiar(zip, destino);
                        firmasExtraidas.add(destino);
                        log.info("Firma extraida del ZIP: {}", nombreEntrada);
                    }
                }
            }
            guardarResumen(nombre, total);
            Files.move(fichero, processed.resolve(nombre), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            // el zip va a error, asi que borro las firmas que ya se habian extraido
            for (Path firma : firmasExtraidas) {
                Files.deleteIfExists(firma);
            }
            throw e;
        }
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
    static BigDecimal leerCsv(InputStream in, String origen) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        BigDecimal total = BigDecimal.ZERO;
        String linea = reader.readLine(); // salto la cabecera
        while ((linea = reader.readLine()) != null) {
            if (linea.isBlank()) {
                continue;
            }
            String[] campos = partirLinea(linea);
            if (campos.length < 4) {
                throw new IllegalArgumentException("Linea mal formada en " + origen + ": " + linea);
            }
            total = total.add(new BigDecimal(campos[2].trim()));
            log.info("[{}] {} | {} | {} | {}", origen, campos[0], campos[1], campos[2], campos[3]);
        }
        log.info("[{}] Importe total del lote: {}", origen, total);
        return total;
    }

    // separa por comas, pero ignora las que estan entre comillas ("Zara, Madrid")
    static String[] partirLinea(String linea) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '"') {
                if (entreComillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    // dos comillas seguidas dentro de un campo = una comilla
                    actual.append('"');
                    i++;
                } else {
                    entreComillas = !entreComillas;
                }
            } else if (c == ',' && !entreComillas) {
                campos.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        campos.add(actual.toString());
        return campos.toArray(new String[0]);
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
        int id = bucketService.siguienteId();
        String json = "{\n"
                + "  \"batchId\": " + id + ",\n"
                + "  \"sourceFile\": \"" + origen + "\",\n"
                + "  \"totalSales\": " + total.toPlainString() + "\n"
                + "}\n";
        Path ruta = bucketService.guardar("summary_batch_" + id + ".json", json);
        log.info("Resumen JSON publicado: {}", ruta);
    }
}