package com.fencgut961.aad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class MultiLanguageDataGenerator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MultiLanguageDataGenerator.class);
    private static final String HEADER = "StoreID,StoreName,SalesAmount,Category,TransactionDate\n";

    private final CryptoService cryptoService;
    private final Path basePath;

    public MultiLanguageDataGenerator(CryptoService cryptoService,
                                      @Value("${store.base-path:workspace/store_data}") String basePath) {
        this.cryptoService = cryptoService;
        this.basePath = Paths.get(basePath);
    }

    @Override
    public void run(String... args) throws Exception {
        Path input = basePath.resolve("input");
        Path keys = basePath.resolve("keys");
        Files.createDirectories(input);
        Files.createDirectories(keys);
        Files.createDirectories(basePath.resolve("output/processed/signatures"));
        Files.createDirectories(basePath.resolve("output/error"));
        Files.createDirectories(basePath.resolve("output/bucket"));

        // Clave
        Files.writeString(keys.resolve("AadTexSecret.key"), "AadTexSecret2026", StandardCharsets.UTF_8);

        // España
        String spain = HEADER
                + "ZARA_ES_001,Zara Puerta del Sol Madrid,14500.50,WOMEN,2026-09-13\n"
                + "ZARA_ES_001,Zara Puerta del Sol Madrid,3600.30,MEN,2026-09-13\n"
                + "ZARA_ES_001,Zara Gran Vía Madrid,2000.00,KIDS,2026-09-13\n";
        Files.writeString(input.resolve("daily_sales_spain.csv"), spain, StandardCharsets.UTF_8);
        Files.write(input.resolve("signature_spain.jpg"), fakeJpg("spain"));

        // China
        String china = HEADER
                + "ZARA_CH_001,Zara 上海南京东路店,28900.00,WOMEN,2026-09-13\n"
                + "ZARA_CH_001,Zara 上海南京东路店,12400.80,MEN,2026-09-13\n";
        Files.writeString(input.resolve("daily_sales_china.csv"), china, StandardCharsets.UTF_8);
        Files.write(input.resolve("signature_china.jpg"), fakeJpg("china"));

        // Financiero cifrado
        String financial = HEADER
                + "ZARA_CENTRAL_FIN,Central Financial Office,95000.00,FINANCIAL_REPORT,2026-09-13\n";
        Files.write(input.resolve("daily_sales_financial.enc"),
                cryptoService.encrypt(financial.getBytes(StandardCharsets.UTF_8)));
        Files.write(input.resolve("signature_financial.jpg"), fakeJpg("financial"));

        // ZIP USA
        String usa = HEADER
                + "ZARA_US_001,Zara Times Square New York,42000.00,WOMEN,2026-09-13\n"
                + "ZARA_US_001,Zara Times Square New York,18850.75,MEN,2026-09-13\n"
                + "ZARA_US_001,Zara 5th Avenue New York,7600.00,KIDS,2026-09-13\n";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry("daily_sales_usa.csv"));
            zos.write(usa.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("signature_usa.jpg"));
            zos.write(fakeJpg("usa"));
            zos.closeEntry();
        }
        Files.write(input.resolve("batch_usa.zip"), bos.toByteArray());

        // Defectuoso (0 bytes)
        Files.write(input.resolve("daily_sales_broken_store.csv"), new byte[0]);

        log.info("Datos de prueba generados en {}", input.toAbsolutePath());
    }

    private byte[] fakeJpg(String name) {
        byte[] header = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        byte[] body = ("FAKE-JPEG-SIGNATURE-" + name + "-").repeat(300).getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[header.length + body.length];
        System.arraycopy(header, 0, all, 0, header.length);
        System.arraycopy(body, 0, all, header.length, body.length);
        return all;
    }
}