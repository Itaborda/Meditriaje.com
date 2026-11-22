package com.meditriaje.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verificación de V026 (eliminación de columnas {@code SEDE_ID} y tablas {@code SEDE}/{@code INSTITUCION}):
 * valida tanto una base migrada desde datos multi-sede (V022 + fixture -> V026) como una base limpia
 * (V001 -> V026).
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class DropSitesMigrationIT {

    private static final String MERGED_OWNER = "MT_DROP_SITES_MERGED";
    private static final String FRESH_OWNER = "MT_DROP_SITES_FRESH";

    private static OracleContainer oracle;
    private static JdbcTemplate merged;
    private static JdbcTemplate fresh;
    private static JdbcTemplate appUser;

    @BeforeAll
    static void migrate() {
        oracle = OracleTestDatabase.newContainer().withStartupTimeout(Duration.ofMinutes(3));
        oracle.start();

        DataSource mergedDs = OracleTestDatabase.provisionOwner(oracle, MERGED_OWNER);
        OracleTestDatabase.flywayUpTo(mergedDs, "22").migrate();
        OracleTestDatabase.runScript(mergedDs, "db/merge-fixture.sql");
        OracleTestDatabase.flyway(mergedDs).migrate();
        merged = new JdbcTemplate(mergedDs);

        DataSource freshDs = OracleTestDatabase.provisionOwner(oracle, FRESH_OWNER);
        OracleTestDatabase.flyway(freshDs).migrate();
        fresh = new JdbcTemplate(freshDs);

        DataSource appDs = OracleTestDatabase.dataSource(
                oracle, OracleTestDatabase.APP_USER, OracleTestDatabase.APP_PASSWORD);
        appUser = new JdbcTemplate(appDs);
    }

    @AfterAll
    static void stopContainer() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    @Test
    void legacyTablesTriggersAndSedeIdColumnsAreGoneInBothSchemas() {
        for (JdbcTemplate schema : List.of(merged, fresh)) {
            assertThat(count(schema, "SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME IN ('SEDE', 'INSTITUCION')"))
                    .isZero();
            assertThat(count(schema,
                    "SELECT COUNT(*) FROM USER_TRIGGERS WHERE TRIGGER_NAME IN ('TR_SEDE_UNICA', 'TR_INSTITUCION_UNICA')"))
                    .isZero();
            assertThat(count(schema, "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE COLUMN_NAME = 'SEDE_ID'"))
                    .isZero();
            assertThat(count(schema,
                    "SELECT COUNT(*) FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'UQ_AREA_SEDE_CODIGO'"))
                    .isZero();
            assertThat(count(schema,
                    "SELECT COUNT(*) FROM USER_INDEXES WHERE INDEX_NAME IN ("
                            + "'UQ_AREA_SEDE_CODIGO', 'IX_SLOT_SEDE', 'IX_DISPENSACION_SEDE', "
                            + "'IX_EPISODIO_SEDE_ESTADO', 'IX_AREA_SEDE', 'IDX_ALERTA_SEDE_ESTADO')"))
                    .isZero();
        }
    }

    @Test
    void singleHospitalConstraintsAndReplacementIndexesAreActive() {
        for (JdbcTemplate schema : List.of(merged, fresh)) {
            assertThat(count(schema,
                    "SELECT COUNT(*) FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'UQ_AREA_CODIGO' "
                            + "AND CONSTRAINT_TYPE = 'U' AND STATUS = 'ENABLED' AND VALIDATED = 'VALIDATED'"))
                    .isEqualTo(1);
            assertThat(count(schema,
                    "SELECT COUNT(*) FROM USER_INDEXES WHERE INDEX_NAME IN ("
                            + "'IX_SLOT_BUSQUEDA', 'IX_EPISODIO_ESTADO', 'IDX_ALERTA_ESTADO')"))
                    .isEqualTo(3);
            assertThat(count(schema,
                    "SELECT COUNT(*) FROM USER_CONSTRAINTS WHERE CONSTRAINT_TYPE = 'R' "
                            + "AND (STATUS <> 'ENABLED' OR VALIDATED <> 'VALIDATED')"))
                    .isZero();
            assertThat(count(schema, "SELECT COUNT(*) FROM USER_OBJECTS WHERE STATUS = 'INVALID'"))
                    .isZero();
        }

        // Unicidad global de código de área en esquema limpio sin SEDE_ID
        fresh.update("INSERT INTO AREA_HOSPITALARIA (PUBLIC_ID, CODIGO, NOMBRE, TIPO) "
                + "VALUES ('drop-area-1', 'URG-DROP', 'Urgencias', 'URGENCIAS')");
        assertThatThrownBy(() -> fresh.update("INSERT INTO AREA_HOSPITALARIA (PUBLIC_ID, CODIGO, NOMBRE, TIPO) "
                + "VALUES ('drop-area-2', 'URG-DROP', 'Otra', 'URGENCIAS')"))
                .hasMessageContaining("ORA-00001");
    }

    @Test
    void hospitalConfigurationSingletonPreservesCanonicalDataAndRowCountsSurvive() {
        assertThat(count(fresh, "SELECT COUNT(*) FROM CONFIGURACION_HOSPITAL")).isEqualTo(1);

        Map<String, Object> cfg = merged.queryForMap(
                "SELECT ID, NIT, RAZON_SOCIAL, DIRECCION, CIUDAD FROM CONFIGURACION_HOSPITAL");
        assertThat(((Number) cfg.get("ID")).intValue()).isEqualTo(1);
        assertThat(cfg.get("NIT")).isEqualTo("900000001-1");
        assertThat(cfg.get("RAZON_SOCIAL")).isEqualTo("Hospital Canonical");
        assertThat(cfg.get("DIRECCION")).isEqualTo("Calle 2 No 3-4");
        assertThat(cfg.get("CIUDAD")).isEqualTo("Medellin");

        assertThatThrownBy(() -> merged.update(
                "INSERT INTO CONFIGURACION_HOSPITAL (ID, PUBLIC_ID, NIT, RAZON_SOCIAL, DIRECCION, CIUDAD) "
                        + "VALUES (2, 'drop-cfg-2', 'x', 'x', 'x', 'x')"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ORA-02290");

        assertThat(count(merged, "SELECT COUNT(*) FROM AREA_HOSPITALARIA")).isEqualTo(6);
        assertThat(count(merged, "SELECT COUNT(*) FROM ALERTA_OPERATIVA")).isEqualTo(5);
        assertThat(count(merged, "SELECT COUNT(*) FROM EPISODIO_ATENCION")).isEqualTo(3);
        assertThat(count(merged, "SELECT COUNT(*) FROM DISPONIBILIDAD_SLOT")).isEqualTo(3);
        assertThat(count(merged, "SELECT COUNT(*) FROM DISPENSACION")).isEqualTo(2);
    }

    @Test
    void dispensationImmutabilityAndAlertUniquenessRemainEnforcedPostV026() {
        assertThatThrownBy(() -> merged.update("UPDATE DISPENSACION SET OBSERVACIONES = 'tamper'"))
                .hasMessageContaining("ORA-20031");
        assertThatThrownBy(() -> merged.update("DELETE FROM DISPENSACION"))
                .hasMessageContaining("ORA-20030");

        merged.update("INSERT INTO ALERTA_OPERATIVA (PUBLIC_ID, TIPO_ALERTA, NIVEL_SEVERIDAD, MENSAJE, ESTADO) "
                + "VALUES ('drop-al-1', 'ALERTA_POST_V026', 'ALTA', 'Primera alerta', 'ACTIVA')");
        assertThatThrownBy(() -> merged.update(
                "INSERT INTO ALERTA_OPERATIVA (PUBLIC_ID, TIPO_ALERTA, NIVEL_SEVERIDAD, MENSAJE, ESTADO) "
                        + "VALUES ('drop-al-2', 'ALERTA_POST_V026', 'ALTA', 'Duplicada', 'ACTIVA')"))
                .hasMessageContaining("ORA-00001")
                .hasMessageContaining("UQ_ALERTA_OPERATIVA_ACTIVA");
    }

    @Test
    void appUserCanReadHospitalConfigAndHasExecuteOnAllFifteenPackages() {
        for (String ownerSchema : List.of(MERGED_OWNER, FRESH_OWNER)) {
            assertThat(appUser.queryForObject(
                    "SELECT COUNT(*) FROM " + ownerSchema + ".CONFIGURACION_HOSPITAL", Integer.class))
                    .isEqualTo(1);
        }

        for (JdbcTemplate schema : List.of(merged, fresh)) {
            Integer packages = schema.queryForObject(
                    "SELECT COUNT(*) FROM USER_OBJECTS WHERE OBJECT_TYPE = 'PACKAGE' AND OBJECT_NAME LIKE 'PKG\\_%' ESCAPE '\\'",
                    Integer.class);
            Integer granted = schema.queryForObject(
                    "SELECT COUNT(*) FROM USER_TAB_PRIVS WHERE GRANTEE = ? AND PRIVILEGE = 'EXECUTE' "
                            + "AND TABLE_NAME LIKE 'PKG\\_%' ESCAPE '\\'",
                    Integer.class, OracleTestDatabase.APP_USER);
            assertThat(packages).isEqualTo(15);
            assertThat(granted).isEqualTo(15);
        }
    }

    private static int count(JdbcTemplate jdbc, String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
