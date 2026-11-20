package com.meditriaje.repository.plsql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * {@link PackageGateway} contra Oracle Free real: el usuario de aplicación (MEDITRIAJE_APP, sin privilegios propios)
 * llama a {@code PKG_COMMON} del esquema propietario gracias al GRANT EXECUTE de {@code R__zz_recompile_and_grants}.
 * Cubre función, cursor REF_CURSOR, parámetros IN/OUT y la traducción de errores -20xxx.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PackageGatewayIT {

    private static final String OWNER = "MT_GATEWAY";

    private static final RowMapper<String> SITE_NAME = (rs, row) -> rs.getString("NOMBRE");

    private static final ProcedureSpec GET_SINGLE_SITE_ID =
            ProcedureSpec.function("PKG_COMMON", "get_single_site_id", "result", Types.NUMERIC).build();
    private static final ProcedureSpec GET_SINGLE_SITE = ProcedureSpec.procedure("PKG_COMMON", "get_single_site")
            .cursor("o_site", SITE_NAME)
            .build();
    private static final ProcedureSpec NEW_PUBLIC_ID =
            ProcedureSpec.function("PKG_COMMON", "new_public_id", "result", Types.VARCHAR).build();
    private static final ProcedureSpec RAISE_ERROR = ProcedureSpec.procedure("PKG_COMMON", "raise_error")
            .in("p_code", Types.NUMERIC)
            .in("p_message", Types.VARCHAR)
            .build();
    // Paquete de prueba creado solo en este esquema para ejercitar IN y OUT escalares en una misma llamada.
    private static final ProcedureSpec ECHO = ProcedureSpec.procedure("PKG_GATEWAY_TEST", "echo")
            .in("p_text", Types.VARCHAR)
            .in("p_number", Types.NUMERIC)
            .out("o_text", Types.VARCHAR)
            .out("o_number", Types.NUMERIC)
            .build();

    private static OracleContainer oracle;
    private static JdbcTemplate owner;
    private static PackageGateway gateway;

    @BeforeAll
    static void migrate() {
        oracle = OracleTestDatabase.newContainer();
        oracle.start();
        DataSource ownerDs = OracleTestDatabase.provisionOwner(oracle, OWNER);
        OracleTestDatabase.flyway(ownerDs).migrate();
        owner = new JdbcTemplate(ownerDs);
        owner.execute("""
                CREATE OR REPLACE PACKAGE PKG_GATEWAY_TEST AS
                    PROCEDURE echo(p_text IN VARCHAR2, p_number IN NUMBER, o_text OUT VARCHAR2, o_number OUT NUMBER);
                    FUNCTION twice(p_number IN NUMBER) RETURN NUMBER;
                END PKG_GATEWAY_TEST;""");
        owner.execute("""
                CREATE OR REPLACE PACKAGE BODY PKG_GATEWAY_TEST AS
                    PROCEDURE echo(p_text IN VARCHAR2, p_number IN NUMBER, o_text OUT VARCHAR2, o_number OUT NUMBER) IS
                    BEGIN
                        o_text := UPPER(p_text);
                        o_number := p_number + 1;
                    END echo;
                    FUNCTION twice(p_number IN NUMBER) RETURN NUMBER IS
                    BEGIN
                        RETURN p_number * 2;
                    END twice;
                END PKG_GATEWAY_TEST;""");
        owner.execute("GRANT EXECUTE ON PKG_GATEWAY_TEST TO " + OracleTestDatabase.APP_USER);

        DataSource appDs =
                OracleTestDatabase.dataSource(oracle, OracleTestDatabase.APP_USER, OracleTestDatabase.APP_PASSWORD);
        gateway = new PackageGateway(appDs, OWNER, new OracleErrorTranslator());
    }

    @AfterAll
    static void stopContainer() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    @Test
    void functionReturnsTheSingleSiteId() {
        Number siteId = gateway.callFunction(GET_SINGLE_SITE_ID);

        assertThat(siteId.longValue()).isEqualTo(owner.queryForObject("SELECT ID FROM SEDE", Long.class));
    }

    @Test
    void refCursorIsMappedWithTheDeclaredRowMapper() {
        List<String> names = gateway.list(GET_SINGLE_SITE, new MapSqlParameterSource(), "o_site");

        assertThat(names).containsExactly(owner.queryForObject("SELECT NOMBRE FROM SEDE", String.class));
    }

    @Test
    void functionWithoutParametersReturnsAUuid() {
        String publicId = gateway.callFunction(NEW_PUBLIC_ID);

        assertThat(publicId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void inAndOutScalarParametersAreBoundInSignatureOrder() {
        Map<String, Object> out = gateway.execute(
                ECHO, new MapSqlParameterSource().addValue("p_text", "hola").addValue("p_number", 41));

        assertThat(out.get("o_text")).isEqualTo("HOLA");
        assertThat(((Number) out.get("o_number")).intValue()).isEqualTo(42);
    }

    @Test
    void businessRuleErrorIsTranslatedWithTheMessageOfThePackage() {
        assertThatThrownBy(() -> gateway.execute(RAISE_ERROR, error(-20300, "La receta ya fue anulada.")))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("La receta ya fue anulada.");
    }

    @Test
    void notFoundAndAccessDeniedAreTranslated() {
        assertThatThrownBy(() -> gateway.execute(RAISE_ERROR, error(-20100, "Paciente no encontrado.")))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Paciente no encontrado.");
        assertThatThrownBy(() -> gateway.execute(RAISE_ERROR, error(-20400, "Acceso denegado.")))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessage("Acceso denegado.");
    }

    @Test
    void outOfRangeCodeBecomesAnUntranslatedInternalError() {
        assertThatThrownBy(() -> gateway.execute(RAISE_ERROR, error(-1, "x")))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ORA-20999")
                .hasMessageContaining("INVALID_ERROR_CODE");
    }

    @Test
    void appUserHasExecuteOnEveryPackageOfTheOwner() {
        Integer packages = owner.queryForObject(
                "SELECT COUNT(*) FROM USER_OBJECTS WHERE OBJECT_TYPE = 'PACKAGE' AND OBJECT_NAME LIKE 'PKG\\_%' ESCAPE '\\'"
                        + " AND OBJECT_NAME <> 'PKG_GATEWAY_TEST'",
                Integer.class);
        Integer granted = owner.queryForObject(
                "SELECT COUNT(*) FROM USER_TAB_PRIVS WHERE GRANTEE = ? AND PRIVILEGE = 'EXECUTE'"
                        + " AND TABLE_NAME LIKE 'PKG\\_%' ESCAPE '\\' AND TABLE_NAME <> 'PKG_GATEWAY_TEST'",
                Integer.class,
                OracleTestDatabase.APP_USER);
        assertThat(packages).isGreaterThanOrEqualTo(1);
        assertThat(granted).isEqualTo(packages);
    }

    @Test
    void everyPackageIsValidAndHasMoreThanOneSubprogram() {
        assertThat(owner.queryForObject(
                        "SELECT COUNT(*) FROM USER_OBJECTS WHERE STATUS = 'INVALID'", Integer.class))
                .isZero();
        List<Map<String, Object>> tooSmall = owner.queryForList(
                "SELECT OBJECT_NAME, COUNT(*) AS N FROM USER_PROCEDURES WHERE OBJECT_TYPE = 'PACKAGE'"
                        + " AND PROCEDURE_NAME IS NOT NULL GROUP BY OBJECT_NAME HAVING COUNT(*) < 2");
        assertThat(tooSmall).isEmpty();
    }

    @Test
    void repeatableMigrationsCanRunAgain() {
        // La marca ${flyway:timestamp} fuerza que R__zz se reaplique en cada migrate; debe ser idempotente.
        DataSource ownerDs = owner.getDataSource();
        assertThat(OracleTestDatabase.flyway(ownerDs).migrate().success).isTrue();
        assertThat(owner.queryForObject(
                        "SELECT STATUS FROM USER_OBJECTS WHERE OBJECT_NAME = 'PKG_COMMON' AND OBJECT_TYPE = 'PACKAGE BODY'",
                        String.class))
                .isEqualTo("VALID");
    }

    private static MapSqlParameterSource error(int code, String message) {
        return new MapSqlParameterSource().addValue("p_code", code).addValue("p_message", message);
    }
}
