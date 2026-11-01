// Muestra cada estación como una tarjeta de color y la mantiene al día con el flujo en directo del servidor.

const STATE_LABELS = {
    AVAILABLE: 'Disponible',
    IRRIGATING: 'Regando',
    LEAK: 'Fuga',
    OUT_OF_SERVICE: 'Fuera de Servicio',
    DISCONNECTED: 'Desconectada',
};

const stationsContainer = document.getElementById('stations');
const connectionText = document.getElementById('connection');
const secondsInput = document.getElementById('seconds');
const orderResult = document.getElementById('order-result');

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
    }

    card.dataset.state = station.state;
    card.className = 'station ' + station.state; // la clase CSS da el color
    // textContent (no innerHTML): los datos de una estación nunca pueden inyectar HTML.
    // replaceChildren borra también la línea de progreso; vuelve con la siguiente telemetría.
    card.replaceChildren(
        textElement('h2', station.id),
        textElement('p', station.location),
        textElement('p', STATE_LABELS[station.state], 'state-label'),
        orderButtons(station));
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
    if (stationId) {
        params.set('stationId', stationId);
    }
    if (order === 'irrigate') {
        const seconds = Number(secondsInput.value);
        if (!Number.isInteger(seconds) || seconds < 1) { // Central solo acepta segundos enteros
            orderResult.textContent = 'Indica los segundos de riego (1 o más)';
            return;
        }
        params.set('seconds', seconds); // el número ya convertido: "30.0" se envía como 30
    }
    try {
        const response = await fetch('/api/orders/' + order + '?' + params, { method: 'POST' });
        orderResult.textContent = await response.text();
    } catch (error) {
        orderResult.textContent = 'Imposible conectar con WM_Central';
    }
}

// Mientras una estación riega, su tarjeta muestra quién lo pidió, el caudal, el agua y el tiempo.
function showTelemetry(telemetry) {
    const card = document.getElementById(cardId(telemetry.stationId));
    if (!card || card.dataset.state !== 'IRRIGATING') {
        return;
    }
    const text = 'Operario ' + (telemetry.operatorId ?? '-') + ' · '
        + telemetry.flowLitresPerMinute.toFixed(1) + ' L/min · '
        + telemetry.accumulatedLitres.toFixed(1) + ' L · '
        + telemetry.elapsedSeconds + ' s';
    let progress = card.querySelector('.progress');
    if (!progress) {
        progress = textElement('p', '', 'progress');
        card.insertBefore(progress, card.querySelector('.buttons')); // encima de los botones
    }
    progress.textContent = text;
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
stream.onopen = () => { connectionText.textContent = 'Conectado a Central'; };
stream.onerror = () => { connectionText.textContent = 'Sin conexión con Central, reintentando…'; };
