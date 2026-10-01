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

Java + Spring Boot · Apache Kafka · SQLite · Docker · AWS

## Enunciado

[`Practica_SD2627_WaterManagement_Vfinal.pdf`](Practica_SD2627_WaterManagement_Vfinal.pdf)

## Estado

En desarrollo.
