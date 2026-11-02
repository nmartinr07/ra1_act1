package com.fencgut961.aad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class MultiLanguageDataGenerator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MultiLanguageDataGenerator.class);

    private final CryptoService cryptoService;

    public MultiLanguageDataGenerator(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @Override
    public void run(String... args) throws Exception {
        Path input = Paths.get("workspace/store_data/input");
        Path keys = Paths.get("workspace/store_data/keys");
        Files.createDirectories(input);
        Files.createDirectories(keys);
        Files.createDirectories(Paths.get("workspace/store_data/output/processed/signatures"));
        Files.createDirectories(Paths.get("workspace/store_data/output/error"));
        Files.createDirectories(Paths.get("workspace/store_data/output/bucket"));

        String cabecera = "StoreID,StoreName,SalesAmount,Category,TransactionDate\n";

        // clave
        Files.writeString(keys.resolve("AadTexSecret.key"), "AadTexSecret2026", StandardCharsets.UTF_8);

        // csv de Espana
        String espana = cabecera
                + "ZARA_ES_001,Zara Puerta del Sol Madrid,14500.50,WOMEN,2026-09-13\n"
                + "ZARA_ES_001,Zara Puerta del Sol Madrid,3600.30,MEN,2026-09-13\n"
                + "ZARA_ES_001,Zara Gran V\u00eda Madrid,2000.00,KIDS,2026-09-13\n";
        Files.writeString(input.resolve("daily_sales_spain.csv"), espana, StandardCharsets.UTF_8);
        Files.write(input.resolve("signature_spain.jpg"), crearJpg());

        // csv de China
        String china = cabecera
                + "ZARA_CH_001,Zara \u4e0a\u6d77\u5357\u4eac\u4e1c\u8def\u5e97,28900.00,WOMEN,2026-09-13\n"
                + "ZARA_CH_001,Zara \u4e0a\u6d77\u5357\u4eac\u4e1c\u8def\u5e97,12400.80,MEN,2026-09-13\n";
        Files.writeString(input.resolve("daily_sales_china.csv"), china, StandardCharsets.UTF_8);
        Files.write(input.resolve("signature_china.jpg"), crearJpg());

        // fichero cifrado
        String financiero = cabecera
                + "ZARA_CENTRAL_FIN,Central Financial Office,95000.00,FINANCIAL_REPORT,2026-09-13\n";
        byte[] cifrado = cryptoService.encrypt(financiero.getBytes(StandardCharsets.UTF_8));
        Files.write(input.resolve("daily_sales_financial.enc"), cifrado);
        Files.write(input.resolve("signature_financial.jpg"), crearJpg());

        // zip de USA con un csv y un jpg
        String usa = cabecera
                + "ZARA_US_001,Zara Times Square New York,42000.00,WOMEN,2026-09-13\n"
                + "ZARA_US_001,Zara Times Square New York,18850.75,MEN,2026-09-13\n"
                + "ZARA_US_001,Zara 5th Avenue New York,7600.00,KIDS,2026-09-13\n";
        try (OutputStream salida = Files.newOutputStream(input.resolve("batch_usa.zip"));
             ZipOutputStream zip = new ZipOutputStream(salida)) {
            zip.putNextEntry(new ZipEntry("daily_sales_usa.csv"));
            zip.write(usa.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("signature_usa.jpg"));
            zip.write(crearJpg());
            zip.closeEntry();
        }

        // fichero vacio (defectuoso)
        Files.write(input.resolve("daily_sales_broken_store.csv"), new byte[0]);

        log.info("Datos de prueba generados en {}", input.toAbsolutePath());
    }

    // jpg falso: empieza con los magic bytes FF D8 FF E0 y el resto son ceros
    private byte[] crearJpg() {
        byte[] jpg = new byte[10000];
        jpg[0] = (byte) 0xFF;
        jpg[1] = (byte) 0xD8;
        jpg[2] = (byte) 0xFF;
        jpg[3] = (byte) 0xE0;
        return jpg;
    }
}