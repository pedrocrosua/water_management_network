# Desplegar WaterManagement en una sola máquina

Esta guía levanta Kafka, Central, dos estaciones y hasta dos operarios en tu ordenador.
El panel está en **http://localhost:8080**. Puedes hacerlo manualmente o con los scripts
del repositorio; ambos usan `docker-compose.yml`.

Necesitas Docker en funcionamiento, Docker Compose con la opción `up --wait` y Bash
para los scripts (Linux, macOS o WSL). Tu usuario debe tener acceso a Docker.
No necesitas instalar Java ni Maven en el ordenador. La primera construcción necesita
Internet para descargar las imágenes y las dependencias.

Ejecuta los comandos desde la raíz del repositorio. Los puertos `8080`, `5000` y `29092`
deben estar libres o utilizados por los servicios de este mismo proyecto.

## Arranque manual

### 1. Construir las imágenes

```bash
docker compose build
```

Repite este paso cuando cambies el código. Las descargas y capas ya construidas se
reutilizan cuando sea posible.

### 2. Arrancar los servicios de fondo

```bash
docker compose up -d --wait --wait-timeout 180 kafka wm-central ws1-monitor ws2-monitor
```

Kafka debe superar su comprobación de salud. Para Central y los monitores, Compose
comprueba que sus contenedores estén en ejecución; sus conexiones pueden tardar unos
segundos más. Si el comando falla, revisa los registros antes de seguir.

### 3. Abrir cada consola en una terminal diferente

Estación WS-01:

```bash
docker compose run --rm --no-deps ws1-engine
```

Estación WS-02:

```bash
docker compose run --rm --no-deps ws2-engine
```

Operario FO-001:

```bash
docker compose run --rm --no-deps fo1
```

Opcionalmente, abre otra terminal para FO-002:

```bash
docker compose run --rm --no-deps fo2
```

Mantén estas terminales abiertas y ejecuta cada consola una sola vez. `--rm` elimina
su contenedor al salir; `--no-deps` utiliza los servicios que ya arrancaste en el paso 2.
Los monitores se ejecutan en segundo plano. Los Engines y operarios necesitan su propia
terminal para usar el menú; por eso se arrancan por separado.

## Arranque con scripts

Construye las imágenes y arranca los servicios de fondo:

```bash
./scripts/local-up.sh
```

Después ejecuta **cada línea en una terminal diferente**:

```bash
./scripts/local-console.sh ws1-engine
./scripts/local-console.sh ws2-engine
./scripts/local-console.sh fo1
```

Para añadir el segundo operario, usa `./scripts/local-console.sh fo2` en otra terminal.
Los scripts no abren ventanas automáticamente. Resuelven la carpeta del proyecto a
partir de su ubicación y seleccionan expresamente el Compose local; también puedes
invocarlos por su ruta absoluta desde otra carpeta. Todos admiten `--help`.

## Comprobar el funcionamiento

1. Abre **http://localhost:8080** y espera a que WS-01 y WS-02 aparezcan como
   **Disponible** (`AVAILABLE` en las consolas).
2. En FO-001, elige la opción `1` para consultar las estaciones.
3. Elige la opción `2` para pedir un riego, indica `WS-01` y una duración en segundos.
4. Comprueba el progreso en la consola y en el panel; al terminar, la estación debe
   volver a **Disponible** y el operario debe recibir el resumen.

Para consultar los contenedores y los registros:

```bash
docker compose ps --all
docker compose logs --tail=100 kafka wm-central ws1-monitor ws2-monitor
```

Si Docker responde `permission denied`, tu usuario no tiene acceso al servicio Docker.
En un PC del laboratorio sin permisos de administración, necesitas que el responsable
del equipo habilite ese acceso. Si aparece un error de puerto ocupado, comprueba qué
aplicación está utilizando los puertos indicados al inicio de la guía.

## Parar el sistema

Primero termina cada consola con `Ctrl+C`. Después ejecuta:

```bash
docker compose down
```

El equivalente automatizado es:

```bash
./scripts/local-down.sh
```

Ambos conservan el volumen `wm-data`, donde está SQLite. No añadas `--volumes` si quieres
conservar las estaciones y los riegos guardados. Puedes volver a arrancar el sistema
siguiendo los pasos anteriores.
