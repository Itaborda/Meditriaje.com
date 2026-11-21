package com.meditriaje.repository.plsql;

import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.ResourceNotFoundException;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Traduce los errores de aplicación lanzados en los paquetes PL/SQL a las excepciones de negocio existentes, de
 * modo que {@code GlobalExceptionHandler} responda con el mismo formato {@code ApiError} de siempre.
 *
 * <ul>
 *   <li>ORA-20000..20099: integridad o inmutabilidad: {@link ConflictoOperacionException} (409)
 *   <li>ORA-20100..20199: recurso no encontrado: {@link RecursoNoEncontradoException} (404)
 *   <li>ORA-20200..20299: conflicto o transición de estado inválida: {@link ConflictoOperacionException} (409)
 *   <li>ORA-20300..20399: regla de negocio o validación: {@link DatosInvalidosException} (400)
 *   <li>ORA-20400..20499: acceso denegado: {@link AccesoNoAutorizadoException} (403)
 *   <li>ORA-20500..20599: catálogo o importación: {@link DatosInvalidosException} (400)
 *   <li>ORA-00001: violación de unicidad: {@link ConflictoOperacionException} (409)
 * </ul>
 *
 * <p>Cualquier otro error (incluidos -20600..-20999, reservados para aserciones de migración y errores internos)
 * se devuelve sin traducir y termina como 500 genérico. El mensaje de la excepción traducida es el texto que el
 * paquete pasó a {@code PKG_COMMON.raise_error} (primera línea, sin el prefijo ORA ni la pila ORA-06512); por eso
 * los paquetes solo deben usar mensajes en español aptos para el usuario final.
 */
@Component
public class OracleErrorTranslator {

    private static final int UNIQUE_VIOLATION = 1;
    private static final int APPLICATION_ERROR_FIRST = 20000;
    private static final int APPLICATION_ERROR_LAST = 20599;
    private static final int MAX_MESSAGE_LENGTH = 500;

    /** Texto de la primera línea: "ORA-20123: mensaje". */
    private static final Pattern FIRST_MESSAGE = Pattern.compile("ORA-\\d{5}: ([^\\r\\n]*)");

    public RuntimeException translate(DataAccessException failure) {
        SQLException sql = findSqlException(failure);
        if (sql == null) {
            return failure;
        }
        int errorCode = sql.getErrorCode();
        if (errorCode == UNIQUE_VIOLATION) {
            return new OperationConflictException("Ya existe un registro con esos datos.");
        }
        if (errorCode < APPLICATION_ERROR_FIRST || errorCode > APPLICATION_ERROR_LAST) {
            return failure;
        }
        String message = applicationMessage(sql.getMessage());
        return switch (errorCode / 100) {
            case 200 -> new OperationConflictException(
                    orDefault(message, "La operacion viola una regla de integridad."));
            case 201 -> new ResourceNotFoundException(orDefault(message, "registro solicitado"));
            case 202 -> new OperationConflictException(
                    orDefault(message, "La operacion entra en conflicto con el estado actual."));
            case 203, 205 -> new InvalidDataException(
                    orDefault(message, "La operacion incumple una regla de negocio."));
            case 204 -> new UnauthorizedAccessException(
                    orDefault(message, "No tiene permisos para realizar esta operacion."));
            default -> failure;
        };
    }

    private static SQLException findSqlException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sql && sql.getErrorCode() != 0) {
                return sql;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String applicationMessage(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = FIRST_MESSAGE.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        String text = matcher.group(1).trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > MAX_MESSAGE_LENGTH ? text.substring(0, MAX_MESSAGE_LENGTH) : text;
    }

    private static String orDefault(String message, String fallback) {
        return message != null ? message : fallback;
    }
}
