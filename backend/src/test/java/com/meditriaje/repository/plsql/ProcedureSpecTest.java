package com.meditriaje.repository.plsql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;

/** Declaración explícita de parámetros: el orden y los tipos deben coincidir con la firma PL/SQL. */
class ProcedureSpecTest {

    private static final RowMapper<String> MAPPER = (rs, row) -> "x";

    @Test
    @DisplayName("conserva el orden de declaración de los parámetros")
    void keepsDeclarationOrder() {
        ProcedureSpec spec = ProcedureSpec.procedure("PKG_DEMO", "find_item")
                .in("p_public_id", Types.VARCHAR)
                .in("p_page", Types.NUMERIC)
                .out("o_total", Types.NUMERIC)
                .cursor("o_items", MAPPER)
                .build();

        assertThat(spec.parameters())
                .extracting(ProcedureSpec.Param::name)
                .containsExactly("p_public_id", "p_page", "o_total", "o_items");
        assertThat(spec.packageName()).isEqualTo("PKG_DEMO");
        assertThat(spec.procedureName()).isEqualTo("find_item");
        assertThat(spec.isFunction()).isFalse();
    }

    @Test
    @DisplayName("el cursor se declara como SqlOutParameter REF_CURSOR con su RowMapper")
    void cursorIsRefCursorOutParameter() {
        ProcedureSpec spec = ProcedureSpec.procedure("PKG_DEMO", "list_items")
                .cursor("o_items", MAPPER)
                .build();

        List<SqlParameter> sqlParameters = spec.toSqlParameters();

        assertThat(sqlParameters).hasSize(1);
        assertThat(sqlParameters.get(0)).isInstanceOf(SqlOutParameter.class);
        SqlOutParameter cursor = (SqlOutParameter) sqlParameters.get(0);
        assertThat(cursor.getName()).isEqualTo("o_items");
        assertThat(cursor.getSqlType()).isEqualTo(Types.REF_CURSOR);
        assertThat(cursor.getRowMapper()).isSameAs(MAPPER);
    }

    @Test
    @DisplayName("los parámetros de entrada y salida escalares conservan su tipo JDBC")
    void scalarParametersKeepTheirType() {
        ProcedureSpec spec = ProcedureSpec.procedure("PKG_DEMO", "create_item")
                .in("p_name", Types.VARCHAR)
                .out("o_version", Types.NUMERIC)
                .build();

        List<SqlParameter> sqlParameters = spec.toSqlParameters();

        assertThat(sqlParameters.get(0)).isNotInstanceOf(SqlOutParameter.class);
        assertThat(sqlParameters.get(0).getSqlType()).isEqualTo(Types.VARCHAR);
        assertThat(sqlParameters.get(1)).isInstanceOf(SqlOutParameter.class);
        assertThat(sqlParameters.get(1).getSqlType()).isEqualTo(Types.NUMERIC);
    }

    @Test
    @DisplayName("una función declara primero su valor de retorno")
    void functionDeclaresReturnValueFirst() {
        ProcedureSpec spec = ProcedureSpec.function("PKG_COMMON", "add_business_days", "o_result", Types.DATE)
                .in("p_start", Types.DATE)
                .in("p_days", Types.NUMERIC)
                .build();

        assertThat(spec.isFunction()).isTrue();
        List<SqlParameter> sqlParameters = spec.toSqlParameters();
        assertThat(sqlParameters.get(0)).isInstanceOf(SqlOutParameter.class);
        assertThat(sqlParameters.get(0).getName()).isEqualTo("o_result");
        assertThat(sqlParameters).extracting(SqlParameter::getName).containsExactly("o_result", "p_start", "p_days");
    }

    @Test
    @DisplayName("rechaza nombres de parámetro repetidos")
    void rejectsDuplicatedParameterNames() {
        ProcedureSpec.Builder builder =
                ProcedureSpec.procedure("PKG_DEMO", "dup").in("p_id", Types.NUMERIC);

        assertThatThrownBy(() -> builder.in("p_id", Types.NUMERIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("p_id");
    }

    @Test
    @DisplayName("rechaza identificadores que no sean nombres PL/SQL simples (sin SQL embebido)")
    void rejectsNonIdentifierNames() {
        assertThatThrownBy(() -> ProcedureSpec.procedure("PKG_DEMO; DROP TABLE X", "p"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProcedureSpec.procedure("PKG_DEMO", "p q"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProcedureSpec.procedure("PKG_DEMO", "p").in("p x", Types.NUMERIC))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProcedureSpec.procedure("", "p")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la lista de parámetros es inmutable")
    void parametersAreImmutable() {
        ProcedureSpec spec = ProcedureSpec.procedure("PKG_DEMO", "p")
                .in("p_id", Types.NUMERIC)
                .build();
        assertThatThrownBy(() -> spec.parameters().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("dos especificaciones con la misma firma son iguales (clave de caché)")
    void equalSpecsShareCacheKey() {
        ProcedureSpec first = ProcedureSpec.procedure("PKG_DEMO", "p")
                .in("p_id", Types.NUMERIC)
                .build();
        ProcedureSpec second = ProcedureSpec.procedure("PKG_DEMO", "p")
                .in("p_id", Types.NUMERIC)
                .build();
        ProcedureSpec other = ProcedureSpec.procedure("PKG_DEMO", "p")
                .in("p_id", Types.VARCHAR)
                .build();

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(other);
    }
}
