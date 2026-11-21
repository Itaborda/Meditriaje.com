package com.meditriaje.repository.plsql;

import static org.assertj.core.api.Assertions.assertThat;

import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.MediTriajeException;
import com.meditriaje.exception.ResourceNotFoundException;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.UncategorizedSQLException;

/** Traducción de los errores ORA-20xxx de los paquetes a las excepciones de negocio existentes. */
class OracleErrorTranslatorTest {

    private final OracleErrorTranslator translator = new OracleErrorTranslator();

    private static DataAccessException oracleFailure(int code, String message) {
        return new UncategorizedSQLException("call", "{call X}", new SQLException(message, "72000", code));
    }

    @ParameterizedTest(name = "ORA-{0} -> {1}")
    @CsvSource({
        "20000, OperationConflictException",
        "20031, OperationConflictException",
        "20100, ResourceNotFoundException",
        "20200, OperationConflictException",
        "20300, InvalidDataException",
        "20400, UnauthorizedAccessException",
        "20500, InvalidDataException",
        "20599, InvalidDataException"
    })
    @DisplayName("cada rango de errores de aplicación se traduce a la excepción existente correspondiente")
    void mapsEveryApplicationRange(int code, String expected) {
        RuntimeException translated = translator.translate(oracleFailure(code, "ORA-" + code + ": Mensaje"));

        assertThat(translated.getClass().getSimpleName()).isEqualTo(expected);
    }

    @Test
    @DisplayName("el mensaje es la primera línea del error, sin prefijo ORA ni pila ORA-06512")
    void usesTheFirstLineAsMessage() {
        String raw = "ORA-20300: La receta ya fue anulada.\nORA-06512: at \"MEDITRIAJE_OWNER.PKG_COMMON\", line 9\n"
                + "ORA-06512: at line 1\nhttps://docs.oracle.com/error-help/db/ora-20300/";

        RuntimeException translated = translator.translate(oracleFailure(20300, raw));

        assertThat(translated).isInstanceOf(InvalidDataException.class).hasMessage("La receta ya fue anulada.");
        assertThat(((MediTriajeException) translated).getCode()).isEqualTo("DATOS_INVALIDOS");
    }

    @Test
    @DisplayName("no encontrado conserva el formato de RecursoNoEncontradoException")
    void notFoundKeepsExistingFormat() {
        RuntimeException translated =
                translator.translate(oracleFailure(20100, "ORA-20100: No hay una sede hospitalaria configurada."));

        assertThat(translated)
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No hay una sede hospitalaria configurada.");
    }

    @Test
    @DisplayName("sin texto de mensaje usa un mensaje genérico en español")
    void fallsBackToGenericMessage() {
        RuntimeException translated = translator.translate(oracleFailure(20400, "ORA-20400: "));

        assertThat(translated)
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessage("No tiene permisos para realizar esta operacion.");
    }

    @Test
    @DisplayName("ORA-00001 (unicidad) se traduce a conflicto 409")
    void uniqueViolationIsConflict() {
        RuntimeException translated = translator.translate(oracleFailure(1, "ORA-00001: unique constraint violated"));

        assertThat(translated)
                .isInstanceOf(OperationConflictException.class)
                .hasMessage("Ya existe un registro con esos datos.");
    }

    @ParameterizedTest(name = "ORA-{0} no se traduce")
    @CsvSource({"20600", "20990", "20999", "942", "1400"})
    @DisplayName("los errores internos, de migración y del motor se devuelven sin traducir (500)")
    void otherErrorsAreNotTranslated(int code) {
        DataAccessException failure = oracleFailure(code, "ORA-" + code + ": detalle interno");

        assertThat(translator.translate(failure)).isSameAs(failure);
    }

    @Test
    @DisplayName("un fallo sin SQLException se devuelve sin traducir")
    void failureWithoutSqlExceptionIsReturned() {
        DataAccessException failure = new DataAccessResourceFailureException("sin conexión");

        assertThat(translator.translate(failure)).isSameAs(failure);
    }
}
