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
        textElement('p', STATE_LABELS[station.state], 'state-label'));
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
        card.appendChild(progress);
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

const stream = new EventSource('/api/stations/stream');

stream.addEventListener('station', event => showStation(JSON.parse(event.data)));
stream.addEventListener('telemetry', event => showTelemetry(JSON.parse(event.data)));
stream.onopen = () => { connectionText.textContent = 'Conectado a Central'; };
stream.onerror = () => { connectionText.textContent = 'Sin conexión con Central, reintentando…'; };
