package com.fencgut961.aad;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoreIngestionSchedulerTest {

    private static final String CABECERA = "StoreID,StoreName,SalesAmount,Category,TransactionDate\n";

    @Test
    void sumaLosImportesDelCsv() throws Exception {
        String csv = CABECERA
                + "A,Zara Madrid,100.50,WOMEN,2026-09-13\n"
                + "B,Zara Madrid,50.25,MEN,2026-09-13\n";

        BigDecimal total = StoreIngestionScheduler.leerCsv(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), "test.csv");

        assertEquals(new BigDecimal("150.75"), total);
    }

    @Test
    void lasComasEntreComillasNoSeparanCampos() throws Exception {
        String csv = CABECERA
                + "A,\"Zara, Puerta del Sol\",100.50,WOMEN,2026-09-13\n";

        BigDecimal total = StoreIngestionScheduler.leerCsv(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), "test.csv");

        assertEquals(new BigDecimal("100.50"), total);
    }

    @Test
    void partirLineaRespetaLasComillas() {
        String[] campos = StoreIngestionScheduler.partirLinea("A,\"Zara, \"\"Centro\"\"\",10.00,KIDS");

        assertArrayEquals(new String[]{"A", "Zara, \"Centro\"", "10.00", "KIDS"}, campos);
    }

    @Test
    void unaLineaIncompletaDaError() {
        String csv = CABECERA + "A,Zara\n";

        assertThrows(IllegalArgumentException.class, () -> StoreIngestionScheduler.leerCsv(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), "roto.csv"));
    }
}