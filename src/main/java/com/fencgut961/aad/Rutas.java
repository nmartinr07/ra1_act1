package com.fencgut961.aad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

// aqui estan todas las rutas en un solo sitio, la base sale de application.properties
@Component
public class Rutas {

    private final Path base;

    public Rutas(@Value("${store.base-path}") String base) {
        this.base = Paths.get(base);
    }

    public Path input() {
        return base.resolve("input");
    }

    public Path keys() {
        return base.resolve("keys");
    }

    public Path processed() {
        return base.resolve("output/processed");
    }

    public Path signatures() {
        return base.resolve("output/processed/signatures");
    }

    public Path error() {
        return base.resolve("output/error");
    }

    public Path bucket() {
        return base.resolve("output/bucket/aadtex-daily-reports/2026/09");
    }
}