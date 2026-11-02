# ra1_act1 - AadTex Store Ingestion Engine

Repositorio: https://github.com/nmartinr07/ra1_act1

## Como ejecutarlo
Hace falta Java 21 o superior. En la carpeta del proyecto:

    .\mvnw spring-boot:run

Al arrancar se crean los ficheros de prueba en workspace/store_data/input.
A los 3 segundos empieza a procesarlos y repite el escaneo cada 5 segundos.

## Que hace
- Lee los CSV en UTF-8 y suma las ventas de cada lote.
- Descifra el .enc (AES-128) en memoria.
- Lee el ZIP sin extraerlo a disco.
- Copia las firmas JPG con un buffer de 4 KB.
- Los ficheros vacios o rotos van a output/error y se escribe una alerta a soporte@aadtex.com.
- Por cada lote se guarda un summary_batch_N.json en output/bucket.