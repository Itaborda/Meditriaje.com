package com.meditriaje.repository.plsql;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Component;

/**
 * Única clase de la aplicación que invoca procedimientos y funciones de la base de datos (paquetes
 * {@code PKG_<MODULO>}). Los repositorios no contienen SQL: declaran un {@link ProcedureSpec}, construyen los
 * parámetros y llaman a esta pasarela.
 *
 * <p>Cada procedimiento se compila una sola vez ({@link SimpleJdbcCall} en caché) con parámetros declarados
 * explícitamente y sin consultar el diccionario de datos, porque el usuario de la aplicación no es el dueño de los
 * objetos. Las llamadas se califican con el esquema propietario ({@code meditriaje.db.owner-schema}, por defecto
 * {@code MEDITRIAJE_OWNER}).
 *
 * <p>Los errores de aplicación lanzados en los paquetes ({@code RAISE_APPLICATION_ERROR -20000..-20999}) se
 * traducen con {@link OracleErrorTranslator} a las excepciones de negocio existentes. El COMMIT lo controla la
 * transacción de Java ({@code @Transactional}), nunca esta clase ni los paquetes.
 */
@Component
public class PackageGateway {

    private final DataSource dataSource;
    private final String ownerSchema;
    private final Function<DataAccessException, RuntimeException> errorTranslator;
    private final Function<ProcedureSpec, SimpleJdbcCall> callFactory;
    private final ConcurrentMap<ProcedureSpec, SimpleJdbcCall> calls = new ConcurrentHashMap<>();

    @Autowired
    public PackageGateway(
            DataSource dataSource,
            @Value("${meditriaje.db.owner-schema:MEDITRIAJE_OWNER}") String ownerSchema,
            OracleErrorTranslator errorTranslator) {
        this.dataSource = dataSource;
        this.ownerSchema = requireOwnerSchema(ownerSchema);
        this.errorTranslator = errorTranslator::translate;
        this.callFactory = this::newCall;
    }

    /** Constructor de pruebas: permite sustituir la fábrica de llamadas compiladas y el traductor. */
    PackageGateway(
            String ownerSchema,
            Function<DataAccessException, RuntimeException> errorTranslator,
            Function<ProcedureSpec, SimpleJdbcCall> callFactory) {
        this.dataSource = null;
        this.ownerSchema = requireOwnerSchema(ownerSchema);
        this.errorTranslator = errorTranslator;
        this.callFactory = callFactory;
    }

    /** Ejecuta el procedimiento o función y devuelve el mapa de salida (escalares y cursores ya mapeados). */
    public Map<String, Object> execute(ProcedureSpec spec, SqlParameterSource input) {
        SimpleJdbcCall call = calls.computeIfAbsent(spec, callFactory);
        try {
            return call.execute(input);
        } catch (DataAccessException failure) {
            throw errorTranslator.apply(failure);
        }
    }

    /** Ejecuta un procedimiento o función sin parámetros de entrada. */
    public Map<String, Object> execute(ProcedureSpec spec) {
        return execute(spec, EmptySqlParameterSource.INSTANCE);
    }

    /** Ejecuta una función y devuelve su valor de retorno. */
    @SuppressWarnings("unchecked")
    public <T> T callFunction(ProcedureSpec spec, SqlParameterSource input) {
        if (!spec.isFunction()) {
            throw new IllegalArgumentException(spec.qualifiedName() + " is not declared as a function");
        }
        return (T) execute(spec, input).get(spec.returnName());
    }

    /** Ejecuta una función sin parámetros de entrada y devuelve su valor de retorno. */
    public <T> T callFunction(ProcedureSpec spec) {
        return callFunction(spec, EmptySqlParameterSource.INSTANCE);
    }

    /** Ejecuta el procedimiento y devuelve las filas del cursor indicado. */
    @SuppressWarnings("unchecked")
    public <T> List<T> list(ProcedureSpec spec, SqlParameterSource input, String cursorParam) {
        Map<String, Object> out = execute(spec, input);
        Object rows = out.get(cursorParam);
        if (rows == null) {
            throw new IllegalStateException(
                    "Cursor '" + cursorParam + "' was not returned by " + spec.qualifiedName());
        }
        return (List<T>) rows;
    }

    /** Ejecuta el procedimiento y devuelve a lo sumo una fila del cursor; más de una es un error. */
    public <T> Optional<T> single(ProcedureSpec spec, SqlParameterSource input, String cursorParam) {
        List<T> rows = list(spec, input, cursorParam);
        if (rows.size() > 1) {
            throw new IncorrectResultSizeDataAccessException(1, rows.size());
        }
        return rows.stream().findFirst();
    }

    /** Compila una llamada sin guardarla en caché (visible para pruebas). */
    SimpleJdbcCall compile(ProcedureSpec spec) {
        return newCall(spec);
    }

    private SimpleJdbcCall newCall(ProcedureSpec spec) {
        SimpleJdbcCall call =
                new SimpleJdbcCall(dataSource).withSchemaName(ownerSchema).withCatalogName(spec.packageName());
        if (spec.isFunction()) {
            call.withFunctionName(spec.procedureName()).withReturnValue();
        } else {
            call.withProcedureName(spec.procedureName());
        }
        call.withoutProcedureColumnMetaDataAccess()
                .declareParameters(spec.toSqlParameters().toArray(new SqlParameter[0]));
        call.compile();
        return call;
    }

    private static String requireOwnerSchema(String ownerSchema) {
        if (ownerSchema == null || ownerSchema.isBlank()) {
            throw new IllegalArgumentException("meditriaje.db.owner-schema must not be blank");
        }
        return ownerSchema.trim();
    }
}
