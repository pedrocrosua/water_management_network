// Shows every station as a coloured card and keeps it up to date with the server's live stream.

const STATE_LABELS = {
    AVAILABLE: 'Disponible',
    IRRIGATING: 'Regando',
    LEAK: 'Fuga',
    OUT_OF_SERVICE: 'Fuera de Servicio',
    DISCONNECTED: 'Desconectada',
};

const stationsContainer = document.getElementById('stations');
const connectionText = document.getElementById('connection');

function showStation(station) {
    let card = document.getElementById(station.id);
    if (!card) {
        card = document.createElement('article');
        card.id = station.id;
        stationsContainer.appendChild(card);
    }

    card.className = 'station ' + station.state;
    // textContent (not innerHTML) so data coming from a station can never inject HTML.
    card.replaceChildren(
        textElement('h2', station.id),
        textElement('p', station.location),
        textElement('p', STATE_LABELS[station.state], 'state-label'));
}

// While a station irrigates, its card shows who asked for it, the flow, the water and the time.
function showTelemetry(telemetry) {
    const card = document.getElementById(telemetry.stationId);
    if (!card || !card.classList.contains('IRRIGATING')) {
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
