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

## Casos de uso

### Actores

| Actor | Quién es | Desde dónde actúa |
|---|---|---|
| Operario de campo | Persona que pide y sigue los riegos | `WM_FO` (menú de consola) |
| Técnico de la estación | Persona junto a la estación que provoca o repara averías y pide riegos manuales | `WM_WS_E` (menú de consola) |
| Operador de Central | Persona que vigila la red | Panel web de `WM_Central` (`http://localhost:8080`) |
| Reloj | El paso del tiempo | Límite de un riego, latidos de salud |

### Implementados

**CU-01 · Conectar una estación a la red**
- Actor: sistema (al arrancar `WM_WS_M` y `WM_WS_E`).
- Flujo:
  1. El Engine conecta con su Monitor (`HELLO`) y recibe su identidad (`ID`).
  2. El Monitor se autentica en Central (`AUTH`), que responde `AUTH_OK` con el estado actual.
  3. La estación aparece en el panel como `AVAILABLE`.
- Alternativo: si Central pierde la conexión con el Monitor, la estación pasa a `DISCONNECTED`.
- Código: `MonitorLink`, `StationMonitor`, `MonitorSession`, `StationRegistry.connect`.

**CU-02 · Vigilar la salud de una estación**
- Actor: reloj.
- Flujo:
  1. Cada segundo, el Monitor envía `PING` al Engine, que responde `PONG#OK`.
  2. El Monitor envía `HEALTH` a Central.
- Alternativo: si el Engine deja de responder tres veces seguidas o se cae la conexión,
  el Monitor informa a Central de una avería (`LEAK`).
- Código: `EngineSession`, `StationMonitor`, `SocketCentralConnection`.

**CU-03 · Simular y reparar una avería**
- Actor: técnico de la estación.
- Flujo:
  1. Pulsa `1` en el Engine. El Engine responde `PONG#KO`.
  2. El Monitor envía `LEAK` y Central pone la estación en `LEAK` (rojo en el panel).
  3. Pulsa `2`. El Engine vuelve a responder `OK`.
  4. El Monitor envía `LEAK_CLEARED` y la estación vuelve a `AVAILABLE`.
- Código: `EngineConsole`, `LeakSensor`, `StationMonitor`, `StationRegistry.reportLeak` / `clearLeak`.

**CU-04 · Consultar el estado de las estaciones**
- Actor: operario de campo.
- Flujo: opción `1` del menú del FO. Muestra cada estación con su estado, que se
  mantiene al día leyendo el topic `wm.station.status`.
- Código: `OperatorConsole`, `StationBoard`, `StationStatusListener`.

**CU-05 · Solicitar un riego**
- Actor: operario de campo.
- Flujo:
  1. Opción `2` del menú del FO: estación y duración en segundos.
  2. Central valida que la estación existe y está `AVAILABLE`, que el operario existe y está
     activo, y que la estación no está atendiendo otra petición.
  3. Central envía la orden `START` al Engine y espera su confirmación.
  4. El Engine abre la válvula y confirma. La estación pasa a `IRRIGATING`.
  5. El operario recibe cada paso: `RECEIVED`, `VALIDATING`, `AUTHORIZATION_REQUESTED`, `AUTHORIZED`.
- Alternativos:
  - Si alguna validación falla, el Engine rechaza la orden o no responde en 10 s, el operario
    recibe `DENIED` con el motivo.
  - Si el Engine confirma tarde, Central le envía `STOP`.
- Riego manual: el técnico de la estación pide el riego con la opción `4` del Engine. La
  petición va a Central con origen `STATION` y sigue el mismo flujo. Como no hay operario, si
  Central la deniega se lo dice a la propia estación con la orden `DENIED`.
- Código: `IrrigationRequests`, `ManualIrrigation`, `IrrigationService.request` / `commandAnswered`,
  `IrrigationController.execute`.

**CU-06 · Seguir el progreso de un riego**
- Actores: operario de campo y operador de Central.
- Flujo:
  1. Cada segundo, el Engine publica el caudal y los litros acumulados.
  2. Central lo muestra en la tarjeta de la estación del panel.
  3. El operario que pidió el riego lo ve en su consola.
- Código: `IrrigationController.tick`, `IrrigationService.telemetryReceived`, `PanelBroadcaster`,
  `IrrigationTelemetryListener`.

**CU-07 · Terminar un riego**
- Actores: reloj (fin por tiempo) o técnico de la estación (opción `3` del Engine).
- Flujo:
  1. El Engine cierra la válvula y publica el fin (`TIME_LIMIT` o `MANUAL_STOP`).
  2. Central vuelve a poner la estación en `AVAILABLE` y guarda el riego en SQLite.
  3. Central envía al operario el resumen: litros, duración y motivo.
- Código: `IrrigationController.tick` / `stopFromStation`, `IrrigationService.irrigationEnded`,
  `JdbcIrrigationRepository`.

**CU-08 · Monitorizar la red**
- Actor: operador de Central.
- Flujo: el panel web muestra todas las estaciones con su color de estado, el caudal y el
  agua de cada riego, un resumen de cuántas hay en cada estado y la actividad reciente
  (cambios de estado y órdenes enviadas). Se actualiza solo, mediante eventos del servidor (SSE).
- Código: `PanelBroadcaster`, `panel.js`.

**CU-09 · Cortar un riego cuando aparece una avería**
- Actor: sistema.
- Flujo:
  1. Durante un riego, el Monitor avisa a Central de una avería (`LEAK`), porque el Engine
     responde `KO` o ha dejado de responder.
  2. Central pone la estación en `LEAK` (rojo en el panel) y ordena al Engine cerrar la
     válvula (`STOP` con motivo `LEAK`).
  3. Central no espera la respuesta del Engine, que puede estar caído: guarda el riego con
     los litros del último segundo que recibió y `end_reason = LEAK`.
  4. El operario recibe el resumen con el motivo `LEAK`.
- Código: `MonitorSession`, `IrrigationService.leakReported`, `IrrigationController.execute`.

**CU-10 · Bloquear y activar estaciones**
- Actor: operador de Central (botones `Bloquear` y `Activar` de cada estación, o
  `Bloquear todas` y `Activar todas`).
- Flujo:
  1. Al bloquearla, la estación pasa a `OUT_OF_SERVICE`: naranja y con el texto "Fuera de
     Servicio" en el panel y en el Monitor. El bloqueo se guarda en SQLite.
  2. Si estaba regando, Central corta el riego igual que con una avería (`STOP` con motivo
     `BLOCKED`), lo guarda y avisa al operario.
  3. Mientras está bloqueada, Central deniega cualquier riego en ella.
  4. Al activarla, vuelve a `AVAILABLE` (verde).
- Código: `OrderController`, `CentralOrders`, `IrrigationService.stationBlocked`,
  `Station.blocked` / `activated`.

**CU-11 · Ordenar un riego desde Central**
- Actor: operador de Central (botón `Regar` de una estación disponible, o `Regar todas`,
  con los segundos que se indican en la cabecera del panel).
- Flujo: el mismo que CU-05 y CU-06, con origen `CENTRAL` y sin operario al que avisar.
  `Regar todas` lo pide en cada estación disponible.
- Código: `OrderController`, `CentralOrders.irrigate`, `IrrigationService.request`.

**CU-12 · Enviar peticiones desde un fichero**
- Actor: operario de campo.
- Flujo:
  1. Opción `3` del menú del FO: ruta del fichero, por ejemplo `requests/example.csv`
     (la carpeta `requests/` se monta en el contenedor del FO).
  2. El fichero tiene una petición por línea, `stationId,limitSeconds`. Se saltan las líneas
     vacías y las que empiezan por `#`.
  3. El FO envía la primera petición y espera a que termine, con éxito o denegada. Después
     espera 4 s y envía la siguiente.
  4. Al acabar muestra cuántas peticiones ha enviado.
- Alternativos:
  - Una línea con formato incorrecto se muestra y se salta.
  - Si una petición no termina en su duración más 30 s, el FO muestra "Imposible conectar
    con WM_Central" y pasa a la siguiente.
- Código: `RequestFile`, `IrrigationRequests.requestAndWait` / `notificationReceived`.

## Caídas de los módulos

**Se cae el Monitor (`WM_WS_M`)**
- Central marca la estación como `DISCONNECTED` (gris, "Desconectada"). Si estaba regando,
  corta el riego como en CU-09 (`STOP` con motivo `DISCONNECTED`), lo guarda con los litros
  del último segundo y avisa al operario.
- El Engine nota que ha perdido su Monitor: cierra la válvula si estaba abierta, muestra
  "Averiada" y no admite riegos, ni siquiera manuales.
- Cuando el Monitor vuelve, se autentica de nuevo en Central y el Engine se reconecta solo:
  la estación vuelve a estar disponible sin reiniciar nada más.
- Código: `MonitorSession`, `IrrigationService.monitorDisconnected`, `MonitorLink`,
  `IrrigationController.monitorLost` / `monitorBack`.

**Se cae el Engine (`WM_WS_E`)**
- El Monitor deja de recibir respuesta y avisa a Central de una avería (`LEAK`). Central pone
  la estación en rojo, corta el riego, lo guarda con los litros del último segundo y avisa al
  operario.
- Mientras riega, el Engine guarda cada segundo lo regado en `data/irrigation.json`, en un
  volumen de Docker que sobrevive a la caída.
- Al volver a arrancar, si encuentra ese fichero, envía a Central el estado final del riego
  (`IRRIGATION_ENDED` con los litros guardados) y lo borra. El Monitor ve que el Engine
  responde otra vez y la estación vuelve a estar disponible.
- Código: `FileIrrigationBackup`, `IrrigationController.tick` / `recoverInterruptedIrrigation`,
  `StationMonitor.engineLost` / `engineAnswered`.

**Se cae un FO (`WM_FO`)**
- El riego sigue: Central y la estación no dependen del FO.
- Cada FO lee sus notificaciones con su propio grupo de Kafka (`fo-FO-001`). Al volver a
  arrancar, Kafka le entrega las que se publicaron mientras estaba caído: el operario ve el
  resultado de su riego.

**Se cae Central (`WM_Central`)**
- Las estaciones siguen regando hasta terminar: el Engine no necesita a Central para regar, y
  el final del riego se queda en Kafka hasta que Central vuelve.
- Los Monitores muestran "Imposible conectar con WM_Central" y lo reintentan solos.
- Un FO que pide un riego espera 10 s la primera respuesta; si no llega, muestra "Imposible
  conectar con WM_Central". Al volver, Central no atiende las peticiones enviadas mientras
  estaba caída: empieza a leer el topic de peticiones desde el final.
- Central guarda en SQLite los riegos en marcha (tabla `ongoing_irrigations`). Al volver a
  arrancar, los recupera y procesa su final cuando le llega por Kafka: no se pierde ningún riego.
- Solo hay que volver a arrancar Central: Monitores, Engines y FO se recuperan solos.
- Código: `OngoingIrrigationRepository`, `IrrigationService.recoverOngoingIrrigations` /
  `monitorConnected`, `IrrigationRequestListener.onPartitionsAssigned`,
  `IrrigationRequests.requestAndWaitForAnswer`.

## Estado

En desarrollo. Están terminados:

- el protocolo de sockets;
- la conexión y la vigilancia de las estaciones;
- las averías, que cortan el riego en curso;
- el ciclo completo de un riego pedido por un operario;
- las órdenes del panel de Central: regar, bloquear y activar una o todas las estaciones;
- el riego manual pedido desde el menú de la propia estación;
- las peticiones leídas de un fichero;
- las caídas de cualquier módulo: Monitor, Engine, FO y Central;
- el despliegue de Central y Kafka en Railway.

Queda pendiente la memoria de la práctica.

## Despliegue en el laboratorio

`WM_Central` y Kafka se ejecutan en la nube (Railway). Cada PC del laboratorio solo
necesita Docker, git y una copia del repositorio. Las direcciones de Central y Kafka ya
están en `lab.env`; si cambian en Railway, se cambian en ese fichero.

**PC de las estaciones.** Cada estación necesita dos terminales: primero su Monitor y
después su Engine.

```bash
./scripts/lab-ws.sh monitor WS-01 "Parque de Canalejas"   # terminal 1
./scripts/lab-ws.sh engine WS-01                          # terminal 2
```

**PC de los operarios.** Una terminal por operario:

```bash
./scripts/lab-fo.sh FO-001
```

Se pueden lanzar tantas estaciones y operarios como se necesite, en el mismo PC o en
varios, cambiando el identificador. Para parar una aplicación, o simular que se cae, pulsa
`Ctrl+C` o cierra su terminal. La primera ejecución en cada PC construye las imágenes y
tarda unos minutos.

Si Central y Kafka se ejecutan en un equipo del aula en lugar de en la nube, arráncalos
en ese equipo con `PUBLIC_HOST=<su ip> docker compose up -d --build kafka wm-central` y
usa en `lab.env` esa IP con los puertos `5000` y `29092`.
