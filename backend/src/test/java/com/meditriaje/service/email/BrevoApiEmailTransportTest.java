package com.meditriaje.service.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BrevoApiEmailTransportTest {

    private HttpServer server;
    private String serverUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String testApiKey = "xkeysib-secret-999-my-top-secret-token";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        int port = server.getAddress().getPort();
        serverUrl = "http://127.0.0.1:" + port + "/v3/smtp/email";
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("201 Creado: envío exitoso vía Brevo API retorna ResultadoEnvio exitoso")
    void shouldSendSuccessfullyWith201() {
        AtomicInteger requestsReceived = new AtomicInteger(0);

        server.createContext("/v3/smtp/email", exchange -> {
            requestsReceived.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("api-key")).isEqualTo(testApiKey);
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).contains("application/json");

            byte[] body = exchange.getRequestBody().readAllBytes();
            String json = new String(body, StandardCharsets.UTF_8);
            assertThat(json).contains("paciente@ejemplo.com");
            assertThat(json).contains("Confirmación de cita");

            String responseBody = "{\"messageId\":\"<20261006.12345@smtp-relay.brevo.com>\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, responseBody.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBody.getBytes(StandardCharsets.UTF_8));
            }
        });

        BrevoApiEmailTransport transport = new BrevoApiEmailTransport(
                serverUrl,
                testApiKey,
                "notificaciones@meditriaje.com",
                "MediTriaje",
                HttpClient.newHttpClient(),
                objectMapper
        );

        EmailSendResult resultado = transport.send(
                "paciente@ejemplo.com",
                "Confirmación de cita",
                "<p>Estimado paciente, su cita está programada.</p>"
        );

        assertThat(resultado.success()).isTrue();
        assertThat(resultado.code()).isEqualTo("OK");
        assertThat(requestsReceived.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("400 Bad Request: remitente no verificado devuelve error BREVO_400 sin reintento")
    void shouldHandle400UnverifiedSender() {
        AtomicInteger requestsReceived = new AtomicInteger(0);

        server.createContext("/v3/smtp/email", exchange -> {
            requestsReceived.incrementAndGet();
            String responseBody = "{\"code\":\"invalid_parameter\",\"message\":\"Sender domain not verified or invalid\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, responseBody.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBody.getBytes(StandardCharsets.UTF_8));
            }
        });

        BrevoApiEmailTransport transport = new BrevoApiEmailTransport(
                serverUrl,
                testApiKey,
                "no-verificado@ejemplo.com",
                "MediTriaje",
                HttpClient.newHttpClient(),
                objectMapper
        );

        EmailSendResult resultado = transport.send(
                "usuario@ejemplo.com",
                "Asunto Test",
                "<p>Contenido</p>"
        );

        assertThat(resultado.success()).isFalse();
        assertThat(resultado.code()).isEqualTo("BREVO_400");
        assertThat(resultado.message()).contains("Sender domain not verified");
        // No debe reintentar errores 4xx
        assertThat(requestsReceived.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("401 Unauthorized: clave inválida devuelve error BREVO_401 sin reintentos")
    void shouldHandle401InvalidKey() {
        AtomicInteger requestsReceived = new AtomicInteger(0);

        server.createContext("/v3/smtp/email", exchange -> {
            requestsReceived.incrementAndGet();
            String responseBody = "{\"code\":\"unauthorized\",\"message\":\"Key not found\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, responseBody.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBody.getBytes(StandardCharsets.UTF_8));
            }
        });

        BrevoApiEmailTransport transport = new BrevoApiEmailTransport(
                serverUrl,
                testApiKey,
                "notificaciones@meditriaje.com",
                "MediTriaje",
                HttpClient.newHttpClient(),
                objectMapper
        );

        EmailSendResult resultado = transport.send("usuario@ejemplo.com", "Asunto Test", "<p>Contenido</p>");

        assertThat(resultado.success()).isFalse();
        assertThat(resultado.code()).isEqualTo("BREVO_401");
        assertThat(requestsReceived.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("429 Too Many Requests: límite de tasa / cuota devuelve error BREVO_429 sin reintentos")
    void shouldHandle429QuotaExceeded() {
        AtomicInteger requestsReceived = new AtomicInteger(0);

        server.createContext("/v3/smtp/email", exchange -> {
            requestsReceived.incrementAndGet();
            String responseBody = "{\"code\":\"resourcelimit\",\"message\":\"Rate limit exceeded\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, responseBody.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBody.getBytes(StandardCharsets.UTF_8));
            }
        });

        BrevoApiEmailTransport transport = new BrevoApiEmailTransport(
                serverUrl,
                testApiKey,
                "notificaciones@meditriaje.com",
                "MediTriaje",
                HttpClient.newHttpClient(),
                objectMapper
        );

        EmailSendResult resultado = transport.send("usuario@ejemplo.com", "Asunto Test", "<p>Contenido</p>");

        assertThat(resultado.success()).isFalse();
        assertThat(resultado.code()).isEqualTo("BREVO_429");
        assertThat(requestsReceived.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("500 Server Error: reintenta exactamente 1 vez y retorna BREVO_5XX si persiste")
    void shouldRetryOnceOn5Xx() {
        AtomicInteger requestsReceived = new AtomicInteger(0);

        server.createContext("/v3/smtp/email", exchange -> {
            requestsReceived.incrementAndGet();
            String responseBody = "{\"message\":\"Internal Server Error\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, responseBody.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBody.getBytes(StandardCharsets.UTF_8));
            }
        });

        BrevoApiEmailTransport transport = new BrevoApiEmailTransport(
                serverUrl,
                testApiKey,
                "notificaciones@meditriaje.com",
                "MediTriaje",
                HttpClient.newHttpClient(),
                objectMapper
        );

        EmailSendResult resultado = transport.send("usuario@ejemplo.com", "Asunto Test", "<p>Contenido</p>");

        assertThat(resultado.success()).isFalse();
        assertThat(resultado.code()).isEqualTo("BREVO_5XX");
        // Exactamente 2 peticiones: Intento 1 + Reintento 1
        assertThat(requestsReceived.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("500 transitorio: tiene éxito en el reintento")
    void shouldSucceedOnRetryAfterTransient500() {
        AtomicInteger requestsReceived = new AtomicInteger(0);

        server.createContext("/v3/smtp/email", exchange -> {
            int attempt = requestsReceived.incrementAndGet();
            if (attempt == 1) {
                String responseBody = "{\"message\":\"Temporary glitch\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(503, responseBody.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBody.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                String responseBody = "{\"messageId\":\"<msg-recovered-123>\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(201, responseBody.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBody.getBytes(StandardCharsets.UTF_8));
                }
            }
        });

        BrevoApiEmailTransport transport = new BrevoApiEmailTransport(
                serverUrl,
                testApiKey,
                "notificaciones@meditriaje.com",
                "MediTriaje",
                HttpClient.newHttpClient(),
                objectMapper
        );

        EmailSendResult resultado = transport.send("usuario@ejemplo.com", "Asunto Test", "<p>Contenido</p>");

        assertThat(resultado.success()).isTrue();
        assertThat(resultado.code()).isEqualTo("OK");
        assertThat(requestsReceived.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Cero exposición de API key en mensaje sanitizado")
    void apiLeyNuncaExpuestaEnMensajes() {
        server.createContext("/v3/smtp/email", exchange -> {
            // El servidor simula un error que refleja la api-key
            String responseBody = "{\"message\":\"Error with key " + testApiKey + " in header\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, responseBody.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBody.getBytes(StandardCharsets.UTF_8));
            }
        });

        BrevoApiEmailTransport transport = new BrevoApiEmailTransport(
                serverUrl,
                testApiKey,
                "notificaciones@meditriaje.com",
                "MediTriaje",
                HttpClient.newHttpClient(),
                objectMapper
        );

        EmailSendResult resultado = transport.send("usuario@ejemplo.com", "Asunto Test", "<p>Contenido</p>");

        assertThat(resultado.success()).isFalse();
        assertThat(resultado.message()).doesNotContain(testApiKey);
        assertThat(resultado.message()).contains("[REDACTED_API_KEY]");
    }

    @Test
    @DisplayName("Si BREVO_API_KEY no está configurada, falla rápido con código BREVO_CONFIG_ERROR")
    void shouldFailWhenApiKeyEmpty() {
        BrevoApiEmailTransport transport = new BrevoApiEmailTransport(
                serverUrl,
                "",
                "notificaciones@meditriaje.com",
                "MediTriaje",
                HttpClient.newHttpClient(),
                objectMapper
        );

        EmailSendResult resultado = transport.send("usuario@ejemplo.com", "Asunto", "<p>Html</p>");
        assertThat(resultado.success()).isFalse();
        assertThat(resultado.code()).isEqualTo("BREVO_CONFIG_ERROR");
    }
}
