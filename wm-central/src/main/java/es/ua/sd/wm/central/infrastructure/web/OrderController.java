package es.ua.sd.wm.central.infrastructure.web;

import es.ua.sd.wm.central.application.CentralOrders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Órdenes del panel (CU-10 y CU-11). Con stationId la orden es para esa estación; sin él, para
 * todas. Responde con un texto corto que el panel muestra tal cual.
 */
@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final CentralOrders orders;

    OrderController(CentralOrders orders) {
        this.orders = orders;
    }

    @PostMapping("/irrigate")
    String irrigate(@RequestParam(required = false) String stationId, @RequestParam int seconds) {
        if (stationId == null) {
            int asked = orders.irrigateAll(seconds);
            return "Riego pedido en " + asked + " estaciones disponibles (" + seconds + " s)";
        }
        orders.irrigate(stationId, seconds);
        return "Riego pedido en " + stationId + " (" + seconds + " s)";
    }

    @PostMapping("/block")
    String block(@RequestParam(required = false) String stationId) {
        if (stationId == null) {
            return orders.blockAll() + " estaciones bloqueadas";
        }
        orders.block(stationId);
        return stationId + " bloqueada";
    }

    @PostMapping("/activate")
    String activate(@RequestParam(required = false) String stationId) {
        if (stationId == null) {
            return orders.activateAll() + " estaciones activadas";
        }
        orders.activate(stationId);
        return stationId + " activada";
    }

    /** Estación desconocida o segundos no válidos: 400 con el motivo. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> rejectOrder(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(error.getMessage());
    }
}
