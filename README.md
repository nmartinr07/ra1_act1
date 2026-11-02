# ra1_act1: AadTex Store Ingestion Engine (App 1)

Repositorio GitHub: https://github.com/nmartinr07/ra1_act1

## Ejecucion
Requisitos: Java 17 o superior.

    mvn spring-boot:run
    (o en Windows: .\mvnw spring-boot:run)

Al arrancar, MultiLanguageDataGenerator crea los datos de prueba en workspace/store_data/input/
y, a los 3 segundos, StoreIngestionScheduler (cada 5 s) los procesa.

## Que hace
- Spring Scheduler (@EnableScheduling, @Scheduled(fixedRate = 5000)).
- NIO.2: Path, Files.createDirectories, Files.move, Files.size.
- ZIP en caliente con ZipInputStream / ZipEntry, sin ficheros temporales.
- Descifrado AES-128 (AES/ECB/PKCS5Padding) en RAM con CryptoService y ByteArrayInputStream.
- CSV leidos con InputStreamReader en StandardCharsets.UTF_8 (espanol y chino).
- Firmas JPG copiadas con BufferedInputStream/BufferedOutputStream y buffer de 4 KB.
- Ficheros de 0 bytes o corruptos: alerta a soporte@aadtex.com y movidos a output/error/.
- Resumen summary_batch_N.json publicado por CloudBucketService en
  output/bucket/aadtex-daily-reports/2026/09/.

## Estructura
    workspace/store_data/
      input/    entrada de las tiendas
      keys/     clave AES
      output/processed/ (y signatures/), output/error/, output/bucket/

Las rutas son relativas al proyecto, asi que funciona en cualquier equipo.