package com.meditriaje.exception;

import com.meditriaje.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Manejador global de excepciones.
 * Toda respuesta de error retorna {@link ApiError} en JSON.
 * Nunca expone stack traces ni datos clínicos/personales al cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Recurso no encontrado: codigo={}", ex.getCode());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(ex.getCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        log.warn("Ruta o recurso no encontrado: {}", ex.getResourcePath());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("RECURSO_NO_ENCONTRADO", "El recurso solicitado no fue encontrado.", null));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        log.warn("Autenticacion fallida: codigo={}", ex.getCode());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(ex.getCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiError> handleInvalidToken(InvalidTokenException ex) {
        log.warn("Token invalido: codigo={}", ex.getCode());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(ex.getCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(InvalidDataException.class)
    public ResponseEntity<ApiError> handleInvalidData(InvalidDataException ex) {
        log.warn("Datos invalidos: codigo={}", ex.getCode());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(ex.getCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ApiError> handleUnauthorizedAccess(UnauthorizedAccessException ex) {
        log.warn("Acceso no autorizado: codigo={}", ex.getCode());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(ex.getCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> handleSpringAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        log.warn("Acceso denegado por seguridad: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("ACCESO_DENEGADO", "No tiene permisos para acceder a este recurso.", null));
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiError> handleSpringAuthentication(org.springframework.security.core.AuthenticationException ex) {
        log.warn("Autenticacion requerida por seguridad: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("NO_AUTENTICADO", "Debe iniciar sesion para acceder a este recurso.", null));
    }

    @ExceptionHandler({AppointmentUnavailableException.class, OperationConflictException.class})
    public ResponseEntity<ApiError> handleConflict(MediTriajeException ex) {
        log.warn("Conflicto de operacion o estado: codigo={}", ex.getCode());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(ex.getCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimitExceeded(RateLimitExceededException ex) {
        log.warn("Limite de peticiones excedido: codigo={}", ex.getCode());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiError.of(ex.getCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validacion fallida: {}", detail);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("VALIDACION_FALLIDA", detail, null));
    }

    /**
     * Captura cualquier excepción no manejada explícitamente.
     * Logea el error internamente pero nunca expone el mensaje al cliente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Error inesperado: tipo={}", ex.getClass().getSimpleName());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("ERROR_INTERNO", "Ocurrio un error interno. Intente mas tarde.", null));
    }
}
