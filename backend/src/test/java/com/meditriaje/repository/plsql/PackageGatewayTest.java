package com.meditriaje.repository.plsql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;

/** Pruebas unitarias de la pasarela a paquetes PL/SQL (sin base de datos). */
class PackageGatewayTest {

    private static final String OWNER = "MEDITRIAJE_OWNER";
    private static final RowMapper<String> MAPPER = (rs, row) -> rs.getString("NOMBRE");

    private static final ProcedureSpec LIST_ITEMS = ProcedureSpec.procedure("PKG_DEMO", "list_items")
            .in("p_status", Types.VARCHAR)
            .out("o_total", Types.NUMERIC)
            .cursor("o_items", MAPPER)
            .build();
    private static final ProcedureSpec OTHER = ProcedureSpec.procedure("PKG_DEMO", "other")
            .in("p_id", Types.NUMERIC)
            .build();

    private final SqlParameterSource input = new MapSqlParameterSource("p_status", "ACTIVO");
    private final AtomicInteger compilations = new AtomicInteger();
    private SimpleJdbcCall call;
    private PackageGateway gateway;

    @BeforeEach
    void setUp() {
        call = mock(SimpleJdbcCall.class);
        Function<ProcedureSpec, SimpleJdbcCall> factory = spec -> {
            compilations.incrementAndGet();
            return call;
        };
        gateway = new PackageGateway(OWNER, ex -> new IllegalStateException("traducida", ex), factory);
    }

    @Test
    @DisplayName("compila una sola vez por procedimiento y reutiliza la llamada")
    void compilesOncePerProcedure() {
        when(call.execute(any(SqlParameterSource.class))).thenReturn(Map.of());

        gateway.execute(LIST_ITEMS, input);
        gateway.execute(LIST_ITEMS, input);
        assertThat(compilations).hasValue(1);

        gateway.execute(OTHER, new MapSqlParameterSource("p_id", 1));
        assertThat(compilations).hasValue(2);
        verify(call, times(3)).execute(any(SqlParameterSource.class));
    }

    @Test
    @DisplayName("execute devuelve el mapa de salida del procedimiento")
    void executeReturnsOutputMap() {
        Map<String, Object> out = Map.of("o_total", 7);
        when(call.execute(input)).thenReturn(out);

        assertThat(gateway.execute(LIST_ITEMS, input)).isSameAs(out);
    }

    @Test
    @DisplayName("list lee el cursor REF_CURSOR por nombre de parámetro")
    void listReadsTheCursor() {
        Map<String, Object> out = new HashMap<>();
        out.put("o_items", List.of("a", "b"));
        when(call.execute(input)).thenReturn(out);

        List<String> items = gateway.list(LIST_ITEMS, input, "o_items");

        assertThat(items).containsExactly("a", "b");
    }

    @Test
    @DisplayName("list falla con un mensaje claro si el cursor no está en el resultado")
    void listFailsWhenCursorMissing() {
        when(call.execute(input)).thenReturn(Map.of());

        assertThatThrownBy(() -> gateway.list(LIST_ITEMS, input, "o_items"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("o_items")
                .hasMessageContaining("PKG_DEMO.list_items");
    }

    @Test
    @DisplayName("single devuelve vacío, un elemento, o falla si hay más de uno")
    void singleHandlesZeroOneAndMany() {
        when(call.execute(input))
                .thenReturn(Map.of("o_items", List.of()))
                .thenReturn(Map.of("o_items", List.of("a")))
                .thenReturn(Map.of("o_items", List.of("a", "b")));

        assertThat(gateway.<String>single(LIST_ITEMS, input, "o_items")).isEmpty();
        assertThat(gateway.<String>single(LIST_ITEMS, input, "o_items")).isEqualTo(Optional.of("a"));
        assertThatThrownBy(() -> gateway.single(LIST_ITEMS, input, "o_items"))
                .isInstanceOf(IncorrectResultSizeDataAccessException.class);
    }

    @Test
    @DisplayName("traduce las excepciones de acceso a datos con el traductor de errores")
    void translatesDataAccessExceptions() {
        DataAccessException failure = new UncategorizedSQLException(
                "CallableStatementCallback",
                "{call X}",
                new SQLException("ORA-20301: TRIAGE_TARGET_INVALID", "72000", 20301));
        when(call.execute(input)).thenThrow(failure);

        assertThatThrownBy(() -> gateway.execute(LIST_ITEMS, input))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("traducida")
                .hasCause(failure);
    }

    @Test
    @DisplayName("callFunction devuelve el valor de retorno declarado de una función")
    void callFunctionReturnsTheReturnValue() {
        ProcedureSpec function = ProcedureSpec.function("PKG_COMMON", "get_single_site_id", "result", Types.NUMERIC)
                .build();
        when(call.execute(any(SqlParameterSource.class))).thenReturn(Map.of("result", 42));

        Integer value = gateway.callFunction(function);

        assertThat(value).isEqualTo(42);
    }

    @Test
    @DisplayName("callFunction rechaza un procedimiento que no es función")
    void callFunctionRejectsProcedures() {
        assertThatThrownBy(() -> gateway.callFunction(OTHER, input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PKG_DEMO.other");
    }

    @Test
    @DisplayName("las excepciones que no son de acceso a datos se propagan sin traducir")
    void otherExceptionsPropagate() {
        when(call.execute(input)).thenThrow(new IllegalArgumentException("parámetro inválido"));

        assertThatThrownBy(() -> gateway.execute(LIST_ITEMS, input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("parámetro inválido");
    }

    @Test
    @DisplayName("la compilación usa esquema, paquete, procedimiento y parámetros declarados explícitamente")
    void compiledCallUsesExplicitDeclarations() throws SQLException {
        DataSource dataSource = oracleLikeDataSource();
        PackageGateway real = new PackageGateway(dataSource, OWNER, new OracleErrorTranslator());

        SimpleJdbcCall compiled = real.compile(LIST_ITEMS);

        assertThat(compiled.getSchemaName()).isEqualTo(OWNER);
        assertThat(compiled.getCatalogName()).isEqualTo("PKG_DEMO");
        assertThat(compiled.getProcedureName()).isEqualTo("list_items");
        assertThat(compiled.getCallString()).isEqualTo("{call MEDITRIAJE_OWNER.PKG_DEMO.list_items(?, ?, ?)}");
    }

    @Test
    @DisplayName("una función se compila con withFunctionName y retorno declarado")
    void functionIsCompiledAsFunction() throws SQLException {
        PackageGateway real = new PackageGateway(oracleLikeDataSource(), OWNER, new OracleErrorTranslator());
        ProcedureSpec function = ProcedureSpec.function("PKG_COMMON", "add_business_days", "o_result", Types.DATE)
                .in("p_start", Types.DATE)
                .in("p_days", Types.NUMERIC)
                .build();

        SimpleJdbcCall compiled = real.compile(function);

        assertThat(compiled.isFunction()).isTrue();
        assertThat(compiled.getCallString())
                .isEqualTo("{? = call MEDITRIAJE_OWNER.PKG_COMMON.add_business_days(?, ?)}");
    }

    @Test
    @DisplayName("exige un esquema propietario configurado (falla rápido)")
    void requiresOwnerSchema() {
        DataSource dataSource = mock(DataSource.class);
        assertThatThrownBy(() -> new PackageGateway(dataSource, " ", new OracleErrorTranslator()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner-schema");
        assertThatThrownBy(() -> new PackageGateway(dataSource, null, new OracleErrorTranslator()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DataSource oracleLikeDataSource() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("Oracle");
        // El controlador de Oracle soporta esquemas (no catálogos) en llamadas a procedimientos.
        when(metaData.supportsSchemasInProcedureCalls()).thenReturn(true);
        return dataSource;
    }
}
