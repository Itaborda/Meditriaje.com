package com.meditriaje.repository.plsql;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;

/**
 * Descripción inmutable de un procedimiento o función de un paquete PL/SQL. Se declara una sola vez por
 * repositorio (constantes estáticas). El orden de declaración DEBE coincidir con la firma PL/SQL: como no se
 * leen metadatos del diccionario, los parámetros se enlazan por posición.
 *
 * @param packageName nombre del paquete (por ejemplo {@code PKG_COMMON})
 * @param procedureName nombre del procedimiento o de la función
 * @param returnName clave del valor de retorno si es función; {@code null} si es procedimiento
 * @param returnType tipo JDBC del retorno (se ignora en procedimientos)
 * @param parameters parámetros declarados, en el orden de la firma
 */
public record ProcedureSpec(
        String packageName, String procedureName, String returnName, int returnType, List<Param> parameters) {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_$#]{0,127}$");

    /** Dirección de un parámetro. */
    public enum Direction {
        IN,
        OUT,
        CURSOR
    }

    /** Parámetro declarado. {@code rowMapper} solo aplica a los cursores. */
    public record Param(String name, int sqlType, Direction direction, RowMapper<?> rowMapper) {}

    public ProcedureSpec {
        requireIdentifier(packageName, "package name");
        requireIdentifier(procedureName, "procedure name");
        if (returnName != null) {
            requireIdentifier(returnName, "return name");
        }
        parameters = List.copyOf(parameters);
    }

    /** Inicia la declaración de un procedimiento. */
    public static Builder procedure(String packageName, String procedureName) {
        requireIdentifier(packageName, "package name");
        requireIdentifier(procedureName, "procedure name");
        return new Builder(packageName, procedureName, null, Types.NULL);
    }

    /** Inicia la declaración de una función; {@code returnName} es la clave del retorno en el mapa de salida. */
    public static Builder function(String packageName, String functionName, String returnName, int returnType) {
        requireIdentifier(packageName, "package name");
        requireIdentifier(functionName, "function name");
        requireIdentifier(returnName, "return name");
        return new Builder(packageName, functionName, returnName, returnType);
    }

    public boolean isFunction() {
        return returnName != null;
    }

    /** Parámetros de Spring JDBC; en una función el valor de retorno va primero. */
    public List<SqlParameter> toSqlParameters() {
        List<SqlParameter> result = new ArrayList<>();
        if (isFunction()) {
            result.add(new SqlOutParameter(returnName, returnType));
        }
        for (Param param : parameters) {
            result.add(
                    switch (param.direction()) {
                        case IN -> new SqlParameter(param.name(), param.sqlType());
                        case OUT -> new SqlOutParameter(param.name(), param.sqlType());
                        case CURSOR -> new SqlOutParameter(param.name(), Types.REF_CURSOR, param.rowMapper());
                    });
        }
        return result;
    }

    /** Nombre calificado para mensajes de diagnóstico. */
    public String qualifiedName() {
        return packageName + "." + procedureName;
    }

    private static void requireIdentifier(String value, String label) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": '" + value + "'");
        }
    }

    /** Constructor fluido de {@link ProcedureSpec}. */
    public static final class Builder {
        private final String packageName;
        private final String procedureName;
        private final String returnName;
        private final int returnType;
        private final List<Param> parameters = new ArrayList<>();

        private Builder(String packageName, String procedureName, String returnName, int returnType) {
            this.packageName = packageName;
            this.procedureName = procedureName;
            this.returnName = returnName;
            this.returnType = returnType;
        }

        /** Parámetro de entrada ({@code p_...}). */
        public Builder in(String name, int sqlType) {
            return add(new Param(name, sqlType, Direction.IN, null));
        }

        /** Parámetro escalar de salida ({@code o_...}). */
        public Builder out(String name, int sqlType) {
            return add(new Param(name, sqlType, Direction.OUT, null));
        }

        /** Parámetro de salida {@code SYS_REFCURSOR}; cada fila se convierte con {@code rowMapper}. */
        public Builder cursor(String name, RowMapper<?> rowMapper) {
            if (rowMapper == null) {
                throw new IllegalArgumentException("A cursor needs a RowMapper: " + name);
            }
            return add(new Param(name, Types.REF_CURSOR, Direction.CURSOR, rowMapper));
        }

        public ProcedureSpec build() {
            return new ProcedureSpec(packageName, procedureName, returnName, returnType, parameters);
        }

        private Builder add(Param param) {
            requireIdentifier(param.name(), "parameter name");
            boolean repeated =
                    parameters.stream().anyMatch(existing -> existing.name().equalsIgnoreCase(param.name()));
            if (repeated) {
                throw new IllegalArgumentException("Duplicated parameter name: " + param.name());
            }
            parameters.add(param);
            return this;
        }
    }
}
