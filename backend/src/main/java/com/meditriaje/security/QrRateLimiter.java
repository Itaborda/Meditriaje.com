package com.meditriaje.security;

import com.meditriaje.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de tasa (Rate Limiter) en memoria por dirección IP para endpoints públicos de QR (SEC-002).
 * Aplica ventana deslizante de tiempo para prevenir ataques de fuerza bruta o saturación.
 */
@Component
public class QrRateLimiter {

    private final int maxRequestsPerWindow;
    private final long windowSeconds;
    private final Clock clock;
    private final Map<String, Deque<Long>> historyByIp = new ConcurrentHashMap<>();

    public QrRateLimiter() {
        this(15, 60, Clock.systemUTC());
    }

    @Autowired
    public QrRateLimiter(
            @Value("${meditriaje.security.qr.rate-limit-per-window:15}") int maxRequestsPerWindow,
            @Value("${meditriaje.security.qr.rate-limit-window-seconds:60}") long windowSeconds
    ) {
        this(maxRequestsPerWindow, windowSeconds, Clock.systemUTC());
    }

    public QrRateLimiter(int maxRequestsPerWindow, long windowSeconds, Clock clock) {
        this.maxRequestsPerWindow = Math.max(1, maxRequestsPerWindow);
        this.windowSeconds = Math.max(1, windowSeconds);
        this.clock = (clock != null) ? clock : Clock.systemUTC();
    }

    /**
     * Valida si la dirección IP excede el límite de peticiones permitido.
     * Si lo excede, lanza {@link LimitePeticionesExcedidoException}.
     * Si no lo excede, registra el acceso en la ventana temporal.
     *
     * @param ip Dirección IP del cliente
     */
    public void validateLimit(String ip) {
        String key = (ip != null && !ip.isBlank()) ? ip.trim() : "unknown";
        long ahora = clock.millis();
        long limiteInferior = ahora - (windowSeconds * 1000L);

        historyByIp.compute(key, (k, cola) -> {
            if (cola == null) {
                cola = new ArrayDeque<>();
            }
            // Eliminar marcas temporales fuera de la ventana
            while (!cola.isEmpty() && cola.peekFirst() < limiteInferior) {
                cola.pollFirst();
            }

            if (cola.size() >= maxRequestsPerWindow) {
                throw new RateLimitExceededException(
                        "Ha superado el límite de solicitudes permitidas para la verificación de emergencia. Intente de nuevo más tarde."
                );
            }

            cola.addLast(ahora);
            return cola;
        });
    }

    /**
     * Limpia el historial de accesos (útil para pruebas unitarias).
     */
    public void reset() {
        historyByIp.clear();
    }
}
