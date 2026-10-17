# WaterManagement

Práctica de **Sistemas Distribuidos** — Universidad de Alicante, curso 2026/2027.

**Autor:** Pedro Cros · **Grupo de prácticas:** 2

## Descripción

Sistema distribuido que simula la gestión centralizada del riego de los parques y jardines de una
ciudad. Está formado por cuatro aplicaciones independientes:

| Aplicación | Función |
|---|---|
| `WM_Central` | Lógica y gobierno del sistema, base de datos y panel de monitorización |
| `WM_WS_E` | Engine de la estación de riego (caudalímetro y electroválvula) |
| `WM_WS_M` | Monitor de la estación de riego (salud, registro y autenticación en la Central) |
| `WM_FO` | Aplicación del operador de campo (Field Operator) |

Las aplicaciones se comunican mediante **sockets TCP** con protocolo propio y mediante **Kafka**
(event streaming).

## Tecnologías

Java + Spring Boot · Apache Kafka · SQLite · Docker · Railway

## Enunciado

[`Practica_SD2627_WaterManagement_Vfinal.pdf`](Practica_SD2627_WaterManagement_Vfinal.pdf)

## Estado

En desarrollo.

El esqueleto de los cuatro ejecutables está creado. `wm-common` incluye un codec para
tramas `<STX><DATA><ETX><LRC>`: codifica mensajes ASCII imprimibles y rechaza las tramas
mal formadas o con un checksum incorrecto. `FrameReader` reúne las tramas desde un flujo
TCP aunque lleguen fragmentadas o varias juntas. `FrameChannel` gestiona los envíos,
las confirmaciones ACK/NACK, el tiempo de espera y tres reintentos. La integración entre
aplicaciones y la lógica de riego están pendientes.

`wm-common` también ofrece `Validator`, una clase de comprobaciones reutilizables por
todos los módulos: valores no nulos, textos no vacíos, longitud máxima, caracteres ASCII
imprimibles y números positivos.

## Compilación y pruebas

Con Docker disponible, desde la raíz del proyecto:

```bash
docker compose run --rm maven mvn -B verify
```

Para probar únicamente el módulo común:

```bash
docker compose run --rm maven mvn -B -pl wm-common -am test
```
