// Muestra cada estación como una tarjeta de color y la mantiene al día con el flujo en directo de Central.

const STATE_LABELS = {
    AVAILABLE: 'Disponible',
    IRRIGATING: 'Regando',
    LEAK: 'Fuga',
    OUT_OF_SERVICE: 'Fuera de Servicio',
    DISCONNECTED: 'Desconectada',
};

// Qué significa cada estado, en una frase, según el enunciado.
const STATE_DESCRIPTIONS = {
    AVAILABLE: 'Lista para regar',
    IRRIGATING: 'Riego en marcha',
    LEAK: 'Fuga detectada: no riega hasta repararla',
    OUT_OF_SERVICE: 'Parada por orden de Central',
    DISCONNECTED: 'Sin conexión con Central',
};

// Nombre de cada orden en la lista de actividad.
const ORDER_NAMES = {
    irrigate: 'Regar',
    block: 'Bloquear',
    activate: 'Activar',
};

const MAX_ACTIVITY_ITEMS = 30;

const stationsContainer = document.getElementById('stations');
const noStationsText = document.getElementById('no-stations');
const connectionText = document.getElementById('connection');
const lastUpdateText = document.getElementById('last-update');
const secondsInput = document.getElementById('seconds');
const orderResult = document.getElementById('order-result');
const activityList = document.getElementById('activity');
const noActivityText = document.getElementById('no-activity');

// Prefijo en el id del HTML: el id de una estación nunca coincide con otro elemento de la página.
function cardId(stationId) {
    return 'station-' + stationId;
}

function showStation(station) {
    let card = document.getElementById(cardId(station.id));
    if (!card) {
        card = document.createElement('article');
        card.id = cardId(station.id);
        stationsContainer.appendChild(card);
    } else if (card.dataset.state !== station.state) {
        // Solo se apuntan los cambios: al conectarse, Central envía todas las estaciones.
        addActivity(station.id + ': ' + STATE_LABELS[card.dataset.state] + ' → ' + STATE_LABELS[station.state]);
    }

    card.dataset.state = station.state;
    card.className = 'station ' + station.state; // la clase CSS da el color
    // textContent (no innerHTML): los datos de una estación nunca pueden inyectar HTML.
    // replaceChildren pone el agua a cero; la siguiente telemetría la vuelve a rellenar.
    card.replaceChildren(
        stationHeader(station),
        textElement('p', station.location, 'location'),
        waterFigures(),
        textElement('p', STATE_DESCRIPTIONS[station.state], 'detail'),
        orderButtons(station));
    updateSummary();
    showUpdateTime();
}

// Id de la estación y una etiqueta con su estado.
function stationHeader(station) {
    const header = document.createElement('div');
    header.className = 'station-header';
    header.append(
        textElement('h3', station.id),
        textElement('span', STATE_LABELS[station.state], 'state-badge'));
    return header;
}

// Caudal y agua acumulada. Empiezan a cero y la telemetría los actualiza mientras se riega.
function waterFigures() {
    const figures = document.createElement('div');
    figures.className = 'figures';
    figures.append(
        waterFigure('flow', 'L/min'),
        waterFigure('litres', 'L'));
    return figures;
}

function waterFigure(name, unit) {
    const figure = document.createElement('p');
    figure.className = 'figure';
    const value = textElement('span', '0.0', 'figure-value');
    value.dataset.figure = name; // así showTelemetry encuentra cada número
    figure.append(value, textElement('span', unit, 'figure-unit'));
    return figure;
}

// Botones de cada tarjeta. Solo se pueden pulsar los que tienen sentido en el estado actual.
function orderButtons(station) {
    const buttons = document.createElement('div');
    buttons.className = 'buttons';
    buttons.append(
        orderButton('Regar', 'irrigate', station.id, station.state !== 'AVAILABLE'),
        orderButton('Bloquear', 'block', station.id, station.outOfService),
        orderButton('Activar', 'activate', station.id, !station.outOfService));
    return buttons;
}

function orderButton(text, order, stationId, disabled) {
    const button = textElement('button', text);
    button.type = 'button';
    button.disabled = disabled;
    button.addEventListener('click', () => sendOrder(order, stationId));
    return button;
}

// Envía una orden a Central (CU-10 y CU-11). Sin estación, la orden es para todas.
async function sendOrder(order, stationId) {
    const params = new URLSearchParams();
    let target = 'todas las estaciones';
    if (stationId) {
        params.set('stationId', stationId);
        target = stationId;
    }
    if (order === 'irrigate') {
        const seconds = Number(secondsInput.value);
        if (!Number.isInteger(seconds) || seconds < 1) { // Central solo acepta segundos enteros
            showOrderResult('Indica los segundos de riego (1 o más)', false);
            return;
        }
        params.set('seconds', seconds); // el número ya convertido: "30.0" se envía como 30
    }
    try {
        const response = await fetch('/api/orders/' + order + '?' + params, { method: 'POST' });
        const answer = await response.text();
        showOrderResult(answer, response.ok);
        addActivity('Orden ' + ORDER_NAMES[order] + ' a ' + target + ': ' + answer);
    } catch (error) {
        showOrderResult('Imposible conectar con WM_Central', false);
    }
}

// Respuesta de Central a la última orden: en verde si salió bien y en rojo si no.
function showOrderResult(text, ok) {
    orderResult.textContent = text;
    if (ok) {
        orderResult.className = 'order-result ok';
    } else {
        orderResult.className = 'order-result error';
    }
    orderResult.hidden = false;
}

// Mientras una estación riega, su tarjeta muestra el caudal, el agua, quién lo pidió y el tiempo.
function showTelemetry(telemetry) {
    const card = document.getElementById(cardId(telemetry.stationId));
    if (!card || card.dataset.state !== 'IRRIGATING') {
        return;
    }
    let requester = 'Riego sin operario'; // pedido desde Central o desde la propia estación
    if (telemetry.operatorId) {
        requester = 'Operario ' + telemetry.operatorId;
    }
    card.querySelector('[data-figure="flow"]').textContent = telemetry.flowLitresPerMinute.toFixed(1);
    card.querySelector('[data-figure="litres"]').textContent = telemetry.accumulatedLitres.toFixed(1);
    card.querySelector('.detail').textContent = requester + ' · ' + telemetry.elapsedSeconds + ' s';
    showUpdateTime();
}

// Cuenta cuántas estaciones hay en cada estado para el resumen de arriba.
function updateSummary() {
    const counts = {};
    for (const state in STATE_LABELS) {
        counts[state] = 0;
    }
    for (const card of stationsContainer.children) {
        counts[card.dataset.state] = counts[card.dataset.state] + 1;
    }
    for (const state in STATE_LABELS) {
        document.getElementById('count-' + state).textContent = counts[state];
    }
    noStationsText.hidden = stationsContainer.children.length > 0;
}

// Apunta un suceso al principio de la lista de actividad. Solo se guardan los últimos.
function addActivity(text) {
    const item = document.createElement('li');
    item.append(textElement('time', currentTime()), textElement('span', text));
    activityList.prepend(item);
    if (activityList.children.length > MAX_ACTIVITY_ITEMS) {
        activityList.lastElementChild.remove();
    }
    noActivityText.hidden = true;
}

function showUpdateTime() {
    lastUpdateText.textContent = 'Última actualización: ' + currentTime();
}

function currentTime() {
    return new Date().toLocaleTimeString('es-ES');
}

function textElement(tag, text, cssClass) {
    const element = document.createElement(tag);
    element.textContent = text;
    if (cssClass) {
        element.className = cssClass;
    }
    return element;
}

document.getElementById('irrigate-all').addEventListener('click', () => sendOrder('irrigate'));
document.getElementById('block-all').addEventListener('click', () => sendOrder('block'));
document.getElementById('activate-all').addEventListener('click', () => sendOrder('activate'));

const stream = new EventSource('/api/stations/stream');

stream.addEventListener('station', event => showStation(JSON.parse(event.data)));
stream.addEventListener('telemetry', event => showTelemetry(JSON.parse(event.data)));
stream.onopen = () => {
    connectionText.textContent = 'Conectado a Central';
    connectionText.className = 'connection online';
};
stream.onerror = () => {
    connectionText.textContent = 'Sin conexión con Central, reintentando…';
    connectionText.className = 'connection offline';
};
