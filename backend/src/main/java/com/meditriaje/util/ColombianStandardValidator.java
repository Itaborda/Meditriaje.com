package com.meditriaje.util;

import com.meditriaje.exception.InvalidDataException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validador de identificación y datos de personas conforme a la normativa colombiana de salud
 * (MinSalud RIPS / Registraduría Nacional / Resolución 3374 y 2275 de 2023 / Ley 1581 de 2012).
 */
public final class ColombianStandardValidator {

    public static final ZoneId COLOMBIA_ZONE = ZoneId.of("America/Bogota");

    private static final Set<String> VALID_DOCUMENT_TYPES = Set.of("CC", "TI", "RC", "CE", "PA");

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^[0-9]+$");
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern NAMES_PATTERN = Pattern.compile("^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s'-]{2,60}$");
    private static final Pattern COLOMBIA_MOBILE_PATTERN = Pattern.compile("^(\\+57)?3[0-9]{9}$");

    private ColombianStandardValidator() {
        // Utility class
    }

    /**
     * Valida el formato del número de documento según el tipo documental reconocido en Colombia.
     */
    public static void validateDocument(String documentType, String documentNumber) {
        if (documentType == null || documentType.isBlank()) {
            throw new InvalidDataException("El tipo de documento es obligatorio.");
        }
        String type = documentType.trim().toUpperCase();
        if (!VALID_DOCUMENT_TYPES.contains(type)) {
            throw new InvalidDataException("Tipo de documento no valido segun la norma colombiana: " + documentType + ". Permitidos: CC, TI, RC, CE, PA.");
        }

        if (documentNumber == null || documentNumber.isBlank()) {
            throw new InvalidDataException("El numero de documento es obligatorio.");
        }
        String number = documentNumber.trim();

        switch (type) {
            case "CC" -> {
                if (!NUMERIC_PATTERN.matcher(number).matches() || number.length() < 6 || number.length() > 10) {
                    throw new InvalidDataException("La Cedula de Ciudadania (CC) debe contener entre 6 y 10 digitos numericos.");
                }
            }
            case "TI" -> {
                if (!NUMERIC_PATTERN.matcher(number).matches() || (number.length() != 10 && number.length() != 11)) {
                    throw new InvalidDataException("La Tarjeta de Identidad (TI) debe contener 10 u 11 digitos numericos.");
                }
            }
            case "RC" -> {
                if (!NUMERIC_PATTERN.matcher(number).matches() || (number.length() != 10 && number.length() != 11)) {
                    throw new InvalidDataException("El Registro Civil (RC) debe contener 10 u 11 digitos numericos.");
                }
            }
            case "CE" -> {
                if (!ALPHANUMERIC_PATTERN.matcher(number).matches() || number.length() < 3 || number.length() > 10) {
                    throw new InvalidDataException("La Cedula de Extranjeria (CE) debe contener entre 3 y 10 caracteres alfanumericos.");
                }
            }
            case "PA" -> {
                if (!ALPHANUMERIC_PATTERN.matcher(number).matches() || number.length() < 5 || number.length() > 20) {
                    throw new InvalidDataException("El Pasaporte (PA) debe contener entre 5 y 20 caracteres alfanumericos.");
                }
            }
            default -> throw new InvalidDataException("Tipo de documento no soportado: " + type);
        }
    }

    /**
     * Valida la coherencia etaria entre el tipo de documento y la fecha de nacimiento.
     */
    public static void validateDocumentAgeCoherence(String documentType, LocalDate birthDate, Clock clock) {
        Objects.requireNonNull(birthDate, "La fecha de nacimiento no puede ser nula.");
        LocalDate today = (clock != null) ? LocalDate.now(clock) : LocalDate.now(COLOMBIA_ZONE);

        if (birthDate.isAfter(today)) {
            throw new InvalidDataException("La fecha de nacimiento no puede corresponder al futuro.");
        }

        int age = Period.between(birthDate, today).getYears();
        if (age > 125) {
            throw new InvalidDataException("La fecha de nacimiento no es valida (edad superior a 125 anos).");
        }

        String type = (documentType != null) ? documentType.trim().toUpperCase() : "";

        switch (type) {
            case "CC" -> {
                if (age < 18) {
                    throw new InvalidDataException(
                            "La Cedula de Ciudadania (CC) solo es valida para personas mayores de 18 anos. Edad actual: " + age + " anos."
                    );
                }
            }
            case "TI" -> {
                if (age < 7 || age > 17) {
                    throw new InvalidDataException(
                            "La Tarjeta de Identidad (TI) solo es valida para menores entre 7 y 17 anos cumplidos. Edad actual: " + age + " anos."
                    );
                }
            }
            case "RC" -> {
                if (age >= 7) {
                    throw new InvalidDataException(
                            "El Registro Civil (RC) solo es valido para ninos menores de 7 anos. Edad actual: " + age + " anos."
                    );
                }
            }
            case "CE", "PA" -> {
                // CE y PA admiten cualquier edad legal en Colombia
            }
            default -> throw new InvalidDataException("Tipo de documento no soportado para validacion de edad: " + documentType);
        }
    }

    /**
     * Valida que el nombre o apellido contenga solo caracteres alfabéticos válidos en español.
     */
    public static void validateNamesOrSurnames(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidDataException("El campo " + field + " es obligatorio.");
        }
        String normalized = value.trim();
        if (!NAMES_PATTERN.matcher(normalized).matches()) {
            throw new InvalidDataException("El campo " + field + " solo puede contener letras, espacios, tildes y guiones (longitud entre 2 y 60 caracteres).");
        }
    }

    /**
     * Valida el formato de teléfono celular para Colombia (10 dígitos iniciando por 3 o prefijo +57).
     */
    public static void validateColombianPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return; // Teléfono opcional
        }
        String normalized = phone.trim().replaceAll("\\s+", "");
        if (!COLOMBIA_MOBILE_PATTERN.matcher(normalized).matches()) {
            throw new InvalidDataException("El telefono debe ser un celular colombiano valido de 10 digitos (iniciando por 3) o con prefijo +573XXXXXXXXX.");
        }
    }

    /**
     * Calcula la edad en años a partir de la fecha de nacimiento.
     */
    public static int calculateAge(LocalDate birthDate, Clock clock) {
        Objects.requireNonNull(birthDate, "La fecha de nacimiento no puede ser nula.");
        LocalDate today = (clock != null) ? LocalDate.now(clock) : LocalDate.now(COLOMBIA_ZONE);
        return Period.between(birthDate, today).getYears();
    }
}
