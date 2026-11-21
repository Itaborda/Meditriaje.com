package com.meditriaje.util;

import com.meditriaje.exception.InvalidDataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NormaColombianaValidator — Pruebas de validación normativa colombiana")
class ColombianStandardValidatorTest {

    // Reloj fijo al 1 de Julio de 2026 en Bogotá
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-01T12:00:00Z"), ZoneId.of("America/Bogota"));
    private final LocalDate today = LocalDate.now(clock); // 2026-07-01

    @Nested
    @DisplayName("Validación de Formato de Documentos")
    class DocumentValidationTest {

        @ParameterizedTest(name = "Cédula válida: {0}")
        @ValueSource(strings = {"123456", "1098765432", "52987654", "80123456"})
        void validCitizenshipCard(String docNumber) {
            assertThatCode(() -> ColombianStandardValidator.validateDocument("CC", docNumber))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "Cédula inválida: {0}")
        @ValueSource(strings = {"12345", "12345678901", "10987A5432", "ABCDEF", "10-234-567"})
        void invalidCitizenshipCard(String docNumber) {
            assertThatThrownBy(() -> ColombianStandardValidator.validateDocument("CC", docNumber))
                    .isInstanceOf(InvalidDataException.class)
                    .hasMessageContaining("Cedula de Ciudadania (CC) debe contener entre 6 y 10 digitos");
        }

        @ParameterizedTest(name = "Tarjeta Identidad válida: {0}")
        @ValueSource(strings = {"1098765432", "10987654321"})
        void validIdentityCard(String docNumber) {
            assertThatCode(() -> ColombianStandardValidator.validateDocument("TI", docNumber))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "Tarjeta Identidad inválida: {0}")
        @ValueSource(strings = {"123456789", "109876543210", "109876543A"})
        void invalidIdentityCard(String docNumber) {
            assertThatThrownBy(() -> ColombianStandardValidator.validateDocument("TI", docNumber))
                    .isInstanceOf(InvalidDataException.class)
                    .hasMessageContaining("Tarjeta de Identidad (TI) debe contener 10 u 11 digitos");
        }

        @ParameterizedTest(name = "Registro Civil válido: {0}")
        @ValueSource(strings = {"1098765432", "10987654321"})
        void validCivilRegistry(String docNumber) {
            assertThatCode(() -> ColombianStandardValidator.validateDocument("RC", docNumber))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "Cédula Extranjería válida: {0}")
        @ValueSource(strings = {"123456", "E123456", "CE98765432"})
        void validForeignerId(String docNumber) {
            assertThatCode(() -> ColombianStandardValidator.validateDocument("CE", docNumber))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "Pasaporte válido: {0}")
        @ValueSource(strings = {"PA12345", "CO9876543210", "PASS12345678"})
        void validPassport(String docNumber) {
            assertThatCode(() -> ColombianStandardValidator.validateDocument("PA", docNumber))
                    .doesNotThrowAnyException();
        }

        @Test
        void unknownDocumentTypeRejected() {
            assertThatThrownBy(() -> ColombianStandardValidator.validateDocument("DNI", "12345678"))
                    .isInstanceOf(InvalidDataException.class)
                    .hasMessageContaining("Tipo de documento no valido segun la norma colombiana");
        }
    }

    @Nested
    @DisplayName("Validación de Coherencia Documento vs Edad")
    class DocumentAgeCoherenceTest {

        @Test
        void citizenshipCardValidForAdult() {
            LocalDate born18YearsAgo = today.minusYears(18);
            LocalDate born30YearsAgo = today.minusYears(30);

            assertThatCode(() -> ColombianStandardValidator.validateDocumentAgeCoherence("CC", born18YearsAgo, clock))
                    .doesNotThrowAnyException();
            assertThatCode(() -> ColombianStandardValidator.validateDocumentAgeCoherence("CC", born30YearsAgo, clock))
                    .doesNotThrowAnyException();
        }

        @Test
        void citizenshipCardRejectedForMinor() {
            LocalDate born17YearsAgo = today.minusYears(17).minusDays(30);

            assertThatThrownBy(() -> ColombianStandardValidator.validateDocumentAgeCoherence("CC", born17YearsAgo, clock))
                    .isInstanceOf(InvalidDataException.class)
                    .hasMessageContaining("La Cedula de Ciudadania (CC) solo es valida para personas mayores de 18 anos");
        }

        @Test
        void identityCardValidRange() {
            LocalDate born7YearsAgo = today.minusYears(7);
            LocalDate born15YearsAgo = today.minusYears(15);
            LocalDate born17YearsAgo = today.minusYears(17);

            assertThatCode(() -> ColombianStandardValidator.validateDocumentAgeCoherence("TI", born7YearsAgo, clock))
                    .doesNotThrowAnyException();
            assertThatCode(() -> ColombianStandardValidator.validateDocumentAgeCoherence("TI", born15YearsAgo, clock))
                    .doesNotThrowAnyException();
            assertThatCode(() -> ColombianStandardValidator.validateDocumentAgeCoherence("TI", born17YearsAgo, clock))
                    .doesNotThrowAnyException();
        }

        @Test
        void identityCardRejectedUnder7() {
            LocalDate born6YearsAgo = today.minusYears(6);

            assertThatThrownBy(() -> ColombianStandardValidator.validateDocumentAgeCoherence("TI", born6YearsAgo, clock))
                    .isInstanceOf(InvalidDataException.class)
                    .hasMessageContaining("La Tarjeta de Identidad (TI) solo es valida para menores entre 7 y 17 anos");
        }

        @Test
        void identityCardRejectedOver17() {
            LocalDate born18YearsAgo = today.minusYears(18);

            assertThatThrownBy(() -> ColombianStandardValidator.validateDocumentAgeCoherence("TI", born18YearsAgo, clock))
                    .isInstanceOf(InvalidDataException.class)
                    .hasMessageContaining("La Tarjeta de Identidad (TI) solo es valida para menores entre 7 y 17 anos");
        }

        @Test
        void civilRegistryValidUnder7() {
            LocalDate born2YearsAgo = today.minusYears(2);
            LocalDate born6YearsAgo = today.minusYears(6).minusMonths(6);

            assertThatCode(() -> ColombianStandardValidator.validateDocumentAgeCoherence("RC", born2YearsAgo, clock))
                    .doesNotThrowAnyException();
            assertThatCode(() -> ColombianStandardValidator.validateDocumentAgeCoherence("RC", born6YearsAgo, clock))
                    .doesNotThrowAnyException();
        }

        @Test
        void civilRegistryRejectedOver6() {
            LocalDate born7YearsAgo = today.minusYears(7);

            assertThatThrownBy(() -> ColombianStandardValidator.validateDocumentAgeCoherence("RC", born7YearsAgo, clock))
                    .isInstanceOf(InvalidDataException.class)
                    .hasMessageContaining("El Registro Civil (RC) solo es valido para ninos menores de 7 anos");
        }

        @Test
        void futureDateRejected() {
            LocalDate tomorrow = today.plusDays(1);

            assertThatThrownBy(() -> ColombianStandardValidator.validateDocumentAgeCoherence("CC", tomorrow, clock))
                    .isInstanceOf(InvalidDataException.class)
                    .hasMessageContaining("La fecha de nacimiento no puede corresponder al futuro");
        }
    }

    @Nested
    @DisplayName("Validación de Nombres, Apellidos y Celular Colombia")
    class ContactDataTest {

        @ParameterizedTest(name = "Nombre válido: {0}")
        @ValueSource(strings = {"Juan Carlos", "María José", "O'Connor", "Jean-Luc", "Ángela Inés", "Ñusta Müller"})
        void validNames(String value) {
            assertThatCode(() -> ColombianStandardValidator.validateNamesOrSurnames("nombres", value))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "Nombre inválido: {0}")
        @ValueSource(strings = {"Juan123", "Carlos@Admin", "A", "<script>"})
        void invalidNames(String value) {
            assertThatThrownBy(() -> ColombianStandardValidator.validateNamesOrSurnames("nombres", value))
                    .isInstanceOf(InvalidDataException.class)
                    .hasMessageContaining("solo puede contener letras, espacios, tildes y guiones");
        }

        @ParameterizedTest(name = "Celular Colombia válido: {0}")
        @ValueSource(strings = {"3001234567", "3159876543", "3201122334", "+573001234567", "+573105556677", " 300 1234567 "})
        void validColombianMobile(String phone) {
            assertThatCode(() -> ColombianStandardValidator.validateColombianPhone(phone))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "Celular Colombia inválido: {0}")
        @ValueSource(strings = {"2345678", "4001234567", "6011234567", "30012345", "300123456789", "+572345678"})
        void invalidColombianMobile(String phone) {
            assertThatThrownBy(() -> ColombianStandardValidator.validateColombianPhone(phone))
                    .isInstanceOf(InvalidDataException.class)
                    .hasMessageContaining("celular colombiano valido de 10 digitos (iniciando por 3)");
        }

        @Test
        void blankMobileAllowedBecauseOptional() {
            assertThatCode(() -> ColombianStandardValidator.validateColombianPhone(null))
                    .doesNotThrowAnyException();
            assertThatCode(() -> ColombianStandardValidator.validateColombianPhone(""))
                    .doesNotThrowAnyException();
            assertThatCode(() -> ColombianStandardValidator.validateColombianPhone("   "))
                    .doesNotThrowAnyException();
        }
    }
}
