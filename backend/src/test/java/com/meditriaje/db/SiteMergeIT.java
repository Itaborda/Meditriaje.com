package com.meditriaje.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WU1a: V023 (prechequeos, captura ANTES, preparación DDL) y V024 (fusión de hospital único, solo DML).
 *
 * <p>Cada escenario se ejecuta en su propio esquema propietario dentro de un contenedor Oracle Free.
 * El usuario privilegiado del contenedor hace el papel del ADMIN de ATP (ver {@link OracleTestDatabase}).</p>
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class SiteMergeIT {

    private static final List<String> SITE_KEYED_TABLES = List.of(
            "DISPONIBILIDAD_SLOT", "DISPENSACION", "EPISODIO_ATENCION", "AREA_HOSPITALARIA", "ALERTA_OPERATIVA");
    private static final String FIXTURE = "db/merge-fixture.sql";
    private static final int LEGACY_TABLE_COUNT = 54;
    private static final String BASIS_ROWS = "DETALLE LIKE 'BASE=%'";

    private static OracleContainer oracle;

    // ---- escenario: fusión (V001..V022 + datos de prueba, luego V023 + V024) ----
    private static JdbcTemplate merged;
    private static Map<String, Long> mergedCountsBefore;
    private static Map<String, Long> siteIds;
    private static Map<String, Long> institutionIds;

    // ---- escenario: falla de prechequeo ----
    private static String precheckMessage;
    private static String precheckFactsBefore;
    private static String precheckFactsAfter;
    private static boolean precheckLegacyIndexSurvived;
    private static boolean precheckSettingsTableExists;
    private static boolean precheckAlertColumnsExist;
    private static int precheckBeforeRows;
    private static List<String> precheckReport;
    private static int precheckSitesAfterRecovery;
    private static List<String> precheckCodesAfterRecovery;

    // ---- escenario: la falla de conciliación revierte todo ----
    private static String rollbackCountMessage;
    private static String rollbackChecksumMessage;
    private static String rollbackFactsBefore;
    private static String rollbackFactsAfterCount;
    private static String rollbackFactsAfterChecksum;
    private static int rollbackSitesAfterRecovery;

    // ---- escenario: solo V023 (reaplicada tras borrar su fila de historial) ----
    private static JdbcTemplate prepared;

    // ---- escenario: base de datos nueva y vacía ----
    private static JdbcTemplate fresh;

    @BeforeAll
    static void buildScenarios() {
        oracle = OracleTestDatabase.newContainer();
        oracle.start();
        buildMergeScenario();
        buildPrecheckScenario();
        buildRollbackScenario();
        buildPreparedScenario();
        buildFreshScenario();
    }

    @AfterAll
    static void stopContainer() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    // ------------------------------------------------------------------ constructores de escenarios

    private static void buildMergeScenario() {
        DataSource ds = OracleTestDatabase.provisionOwner(oracle, "MT_MERGE");
        OracleTestDatabase.flywayUpTo(ds, "22").migrate();
        OracleTestDatabase.runScript(ds, FIXTURE);
        merged = new JdbcTemplate(ds);
        mergedCountsBefore = tableCounts(merged);
        siteIds = idsByPublicId(merged, "SEDE");
        institutionIds = idsByPublicId(merged, "INSTITUCION");
        OracleTestDatabase.flyway(ds).migrate();
    }

    private static void buildPrecheckScenario() {
        DataSource ds = OracleTestDatabase.provisionOwner(oracle, "MT_PRECHECK");
        OracleTestDatabase.flywayUpTo(ds, "22").migrate();
        OracleTestDatabase.runScript(ds, FIXTURE);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        // Dos sedes comparten un código de 29 caracteres: recodificar 'CODIGO-<sedeId>' superaría VARCHAR2(30).
        String longCode = "A".repeat(29);
        jdbc.update("INSERT INTO AREA_HOSPITALARIA (PUBLIC_ID, SEDE_ID, CODIGO, NOMBRE, TIPO) VALUES "
                + "('it-area-long-1', (SELECT ID FROM SEDE WHERE PUBLIC_ID = 'it-site-2'), ?, 'Long 1', 'URGENCIAS')", longCode);
        jdbc.update("INSERT INTO AREA_HOSPITALARIA (PUBLIC_ID, SEDE_ID, CODIGO, NOMBRE, TIPO) VALUES "
                + "('it-area-long-2', (SELECT ID FROM SEDE WHERE PUBLIC_ID = 'it-site-3'), ?, 'Long 2', 'URGENCIAS')", longCode);
        precheckFactsBefore = facts(jdbc);
        Flyway flyway = OracleTestDatabase.flyway(ds);
        precheckMessage = migrateExpectingFailure(flyway);
        precheckFactsAfter = facts(jdbc);
        precheckLegacyIndexSurvived = indexExists(jdbc, "UQ_ALERTA_SEDE_TIPO_ACTIVA");
        precheckSettingsTableExists = tableExists(jdbc, "CONFIGURACION_HOSPITAL");
        precheckAlertColumnsExist = columnExists(jdbc, "ALERTA_OPERATIVA", "EPISODIO_ID");
        precheckBeforeRows = count(jdbc,
                "SELECT COUNT(*) FROM CONCILIACION_MIGRACION WHERE FASE = 'ANTES' AND NOMBRE_TABLA <> 'PRECHECK'");
        precheckReport = jdbc.queryForList(
                "SELECT DETALLE FROM CONCILIACION_MIGRACION WHERE NOMBRE_TABLA = 'PRECHECK' ORDER BY ID", String.class);
        // El operador corrige los datos, repara la entrada fallida del historial y reejecuta las mismas migraciones.
        jdbc.update("UPDATE AREA_HOSPITALARIA SET CODIGO = 'SHORT' WHERE PUBLIC_ID = 'it-area-long-2'");
        flyway.repair();
        flyway.migrate();
        precheckSitesAfterRecovery = count(jdbc, "SELECT COUNT(*) FROM SEDE");
        precheckCodesAfterRecovery = jdbc.queryForList(
                "SELECT CODIGO FROM AREA_HOSPITALARIA WHERE PUBLIC_ID LIKE 'it-area-long-%' ORDER BY PUBLIC_ID", String.class);
    }

    private static void buildRollbackScenario() {
        DataSource ds = OracleTestDatabase.provisionOwner(oracle, "MT_ROLLBACK");
        OracleTestDatabase.flywayUpTo(ds, "22").migrate();
        OracleTestDatabase.runScript(ds, FIXTURE);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        OracleTestDatabase.flywayUpTo(ds, "23").migrate();
        rollbackFactsBefore = facts(jdbc);

        Flyway flyway = OracleTestDatabase.flyway(ds);
        // Falla 1: el conteo ANTES de PACIENTE ya no coincide con la tabla real.
        jdbc.update("UPDATE CONCILIACION_MIGRACION SET CANTIDAD_FILAS = CANTIDAD_FILAS + 1 WHERE MIGRACION = 'V023' "
                + "AND FASE = 'ANTES' AND NOMBRE_TABLA = 'PACIENTE' AND " + BASIS_ROWS);
        rollbackCountMessage = migrateExpectingFailure(flyway);
        rollbackFactsAfterCount = facts(jdbc);
        jdbc.update("UPDATE CONCILIACION_MIGRACION SET CANTIDAD_FILAS = CANTIDAD_FILAS - 1 WHERE MIGRACION = 'V023' "
                + "AND FASE = 'ANTES' AND NOMBRE_TABLA = 'PACIENTE' AND " + BASIS_ROWS);

        // Falla 2: la suma de control ANTES de RECETA ya no coincide.
        flyway.repair();
        jdbc.update("UPDATE CONCILIACION_MIGRACION SET SUMA_CONTROL_PUBLIC_ID = SUMA_CONTROL_PUBLIC_ID + 1 "
                + "WHERE MIGRACION = 'V023' AND FASE = 'ANTES' AND NOMBRE_TABLA = 'RECETA' AND " + BASIS_ROWS);
        rollbackChecksumMessage = migrateExpectingFailure(flyway);
        rollbackFactsAfterChecksum = facts(jdbc);
        jdbc.update("UPDATE CONCILIACION_MIGRACION SET SUMA_CONTROL_PUBLIC_ID = SUMA_CONTROL_PUBLIC_ID - 1 "
                + "WHERE MIGRACION = 'V023' AND FASE = 'ANTES' AND NOMBRE_TABLA = 'RECETA' AND " + BASIS_ROWS);

        // Deshecha la alteración, los mismos datos se fusionan: la reversión dejó un estado limpio.
        flyway.repair();
        flyway.migrate();
        rollbackSitesAfterRecovery = count(jdbc, "SELECT COUNT(*) FROM SEDE");
    }

    private static void buildPreparedScenario() {
        DataSource ds = OracleTestDatabase.provisionOwner(oracle, "MT_PREPARED");
        OracleTestDatabase.flywayUpTo(ds, "22").migrate();
        OracleTestDatabase.runScript(ds, FIXTURE);
        prepared = new JdbcTemplate(ds);
        OracleTestDatabase.flywayUpTo(ds, "23").migrate();
        // Simula "flyway repair" de V023: la olvida y la aplica de nuevo sobre el esquema ya preparado.
        prepared.update("DELETE FROM \"flyway_schema_history\" WHERE \"version\" = '023'");
        OracleTestDatabase.flywayUpTo(ds, "23").migrate();
    }

    private static void buildFreshScenario() {
        DataSource ds = OracleTestDatabase.provisionOwner(oracle, "MT_FRESH");
        fresh = new JdbcTemplate(ds);
        OracleTestDatabase.flyway(ds).migrate();
    }

    // ------------------------------------------------------------------ la fusión conserva las filas

    @Test
    void legacySchemaHasFiftyFourTables() {
        assertThat(mergedCountsBefore).hasSize(LEGACY_TABLE_COUNT);
    }

    @Test
    void mergePreservesEveryRowOfEveryTable() {
        Map<String, Long> after = tableCounts(merged);
        assertThat(after.keySet()).isEqualTo(mergedCountsBefore.keySet());
        mergedCountsBefore.forEach((table, before) -> {
            if (table.equals("SEDE") || table.equals("INSTITUCION")) {
                return;
            }
            long expected = table.equals("CONTROL_SISTEMA") ? before + 1 : before; // + fila de evidencia de la fusión
            assertThat(after.get(table)).as("row count of " + table).isEqualTo(expected);
        });
    }

    @Test
    void everyTableKeepsItsChecksum() {
        List<Map<String, Object>> rows = merged.queryForList(
                "SELECT b.NOMBRE_TABLA, b.SUMA_CONTROL_PUBLIC_ID AS B, a.SUMA_CONTROL_PUBLIC_ID AS A "
                        + "FROM CONCILIACION_MIGRACION b JOIN CONCILIACION_MIGRACION a "
                        + "ON a.NOMBRE_TABLA = b.NOMBRE_TABLA AND a.FASE = 'DESPUES' AND a.DETALLE LIKE 'BASE=%' "
                        + "WHERE b.FASE = 'ANTES' AND b.DETALLE LIKE 'BASE=%' "
                        + "AND b.NOMBRE_TABLA NOT IN ('SEDE', 'INSTITUCION')");
        assertThat(rows).hasSize(LEGACY_TABLE_COUNT - 2);
        for (Map<String, Object> row : rows) {
            assertThat(row.get("A")).as("checksum of " + row.get("NOMBRE_TABLA")).isEqualTo(row.get("B"));
        }
    }

    @Test
    void reconciliationCapturesFiftyFourTablesBeforeAndAfter() {
        assertThat(count(merged, "SELECT COUNT(*) FROM CONCILIACION_MIGRACION WHERE MIGRACION = 'V023' "
                + "AND FASE = 'ANTES' AND " + BASIS_ROWS)).isEqualTo(LEGACY_TABLE_COUNT);
        assertThat(count(merged, "SELECT COUNT(*) FROM CONCILIACION_MIGRACION WHERE MIGRACION = 'V024' "
                + "AND FASE = 'DESPUES' AND " + BASIS_ROWS)).isEqualTo(LEGACY_TABLE_COUNT);
        for (String table : SITE_KEYED_TABLES) {
            Long perSiteSum = merged.queryForObject("SELECT NVL(SUM(CANTIDAD_FILAS), 0) FROM CONCILIACION_MIGRACION "
                    + "WHERE FASE = 'ANTES' AND NOMBRE_TABLA = ? AND DETALLE LIKE 'SEDE_ID=%'", Long.class, table);
            assertThat(perSiteSum).as("per-site BEFORE sum of " + table).isEqualTo(mergedCountsBefore.get(table));
        }
    }

    // ------------------------------------------------------------------ exactamente una sede / institución

    @Test
    void exactlyOneSiteAndOneInstitutionRemain() {
        assertThat(count(merged, "SELECT COUNT(*) FROM SEDE")).isEqualTo(1);
        assertThat(count(merged, "SELECT COUNT(*) FROM INSTITUCION")).isEqualTo(1);
    }

    @Test
    void canonicalSiteIsLowestActiveSiteOfOldestActiveInstitution() {
        assertThat(merged.queryForObject("SELECT PUBLIC_ID FROM SEDE", String.class)).isEqualTo("it-site-2");
        assertThat(merged.queryForObject("SELECT PUBLIC_ID FROM INSTITUCION", String.class)).isEqualTo("it-inst-1");
        assertThat(merged.queryForObject("SELECT INSTITUCION_ID FROM SEDE", Long.class))
                .isEqualTo(institutionIds.get("it-inst-1"));
    }

    @Test
    void legacyDefaultSiteRuleStillResolvesTheCanonicalSite() {
        // DefaultSiteResolver (slice 0): SEDE ACTIVO de menor ID. Tras la fusión es la única fila.
        assertThat(merged.queryForObject("SELECT MIN(ID) FROM SEDE WHERE ESTADO = 'ACTIVO'", Long.class))
                .isEqualTo(siteIds.get("it-site-2"));
        assertThat(count(fresh, "SELECT COUNT(*) FROM SEDE WHERE ESTADO = 'ACTIVO'")).isEqualTo(1);
    }

    @Test
    void noRowReferencesAMergedSiteAndNoForeignKeyIsBroken() {
        long canonical = siteIds.get("it-site-2");
        for (String table : SITE_KEYED_TABLES) {
            assertThat(count(merged, "SELECT COUNT(*) FROM " + table + " WHERE SEDE_ID <> " + canonical))
                    .as(table + " rows on a non-canonical site").isZero();
        }
        assertThat(count(merged, "SELECT COUNT(*) FROM USER_CONSTRAINTS WHERE CONSTRAINT_TYPE = 'R' "
                + "AND (STATUS <> 'ENABLED' OR VALIDATED <> 'VALIDATED')")).isZero();
    }

    @Test
    void hospitalSettingsHoldsTheCanonicalIdentity() {
        Map<String, Object> row = merged.queryForMap("SELECT ID, NIT, RAZON_SOCIAL, DIRECCION, CIUDAD FROM CONFIGURACION_HOSPITAL");
        assertThat(((Number) row.get("ID")).intValue()).isEqualTo(1);
        assertThat(row.get("NIT")).isEqualTo("900000001-1");
        assertThat(row.get("RAZON_SOCIAL")).isEqualTo("Hospital Canonical");
        assertThat(row.get("DIRECCION")).isEqualTo("Calle 2 No 3-4");
        assertThat(row.get("CIUDAD")).isEqualTo("Medellin");
        assertThatThrownBy(() -> merged.update("INSERT INTO CONFIGURACION_HOSPITAL (ID, PUBLIC_ID, NIT, RAZON_SOCIAL, DIRECCION, CIUDAD) "
                + "VALUES (2, 'it-settings-2', 'x', 'x', 'x', 'x')")).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ORA-02290");
    }

    // ------------------------------------------------------------------ códigos de área

    @Test
    void collidingAreaCodesAreRecodedWithTheOldSiteId() {
        long site3 = siteIds.get("it-site-3");
        long site4 = siteIds.get("it-site-4");
        List<String> codes = merged.queryForList("SELECT CODIGO FROM AREA_HOSPITALARIA ORDER BY PUBLIC_ID", String.class);
        // it-area-1..6: URG (la canónica lo conserva), URG (sede 3), URG (sede 4), UCI-1 (único), OBS (la sede menor lo conserva), OBS (sede 4)
        assertThat(codes).containsExactly("URG", "URG-" + site3, "URG-" + site4, "UCI-1", "OBS", "OBS-" + site4);
        assertThat(count(merged, "SELECT COUNT(*) FROM (SELECT SEDE_ID, CODIGO FROM AREA_HOSPITALARIA "
                + "GROUP BY SEDE_ID, CODIGO HAVING COUNT(*) > 1)")).isZero();
    }

    // ------------------------------------------------------------------ alertas

    @Test
    void duplicateActiveAlertsAreResolvedNotDeleted() {
        assertThat(count(merged, "SELECT COUNT(*) FROM ALERTA_OPERATIVA")).isEqualTo(5);
        assertThat(merged.queryForList("SELECT PUBLIC_ID FROM ALERTA_OPERATIVA WHERE ESTADO = 'ACTIVA' "
                + "AND TIPO_ALERTA = 'SATURACION_URGENCIAS'", String.class)).containsExactly("it-alert-3");
        assertThat(merged.queryForList("SELECT PUBLIC_ID FROM ALERTA_OPERATIVA WHERE ESTADO = 'RESUELTA' "
                + "AND MOTIVO_RECONOCIMIENTO = 'MERGED_SITE' ORDER BY PUBLIC_ID", String.class))
                .containsExactly("it-alert-1", "it-alert-2");
        // La única alerta activa de su tipo y la reconocida no se tocan.
        assertThat(merged.queryForObject("SELECT ESTADO FROM ALERTA_OPERATIVA WHERE PUBLIC_ID = 'it-alert-4'", String.class))
                .isEqualTo("ACTIVA");
        assertThat(merged.queryForObject("SELECT ESTADO FROM ALERTA_OPERATIVA WHERE PUBLIC_ID = 'it-alert-5'", String.class))
                .isEqualTo("RECONOCIDA");
    }

    @Test
    void legacyAlertUniqueIndexIsGoneAndNewColumnsExist() {
        assertThat(indexExists(merged, "UQ_ALERTA_SEDE_TIPO_ACTIVA")).isFalse();
        assertThat(columnExists(merged, "ALERTA_OPERATIVA", "EPISODIO_ID")).isTrue();
        assertThat(columnExists(merged, "ALERTA_OPERATIVA", "NIVEL_TRIAJE")).isTrue();
    }

    // ------------------------------------------------------------------ la inmutabilidad se mantiene

    @Test
    void dispensationRowsAreRepointedAndStillImmutable() {
        long canonical = siteIds.get("it-site-2");
        assertThat(merged.queryForList("SELECT SEDE_ID FROM DISPENSACION", Long.class)).containsOnly(canonical).hasSize(2);
        assertThatThrownBy(() -> merged.update("UPDATE DISPENSACION SET OBSERVACIONES = 'tamper'"))
                .hasMessageContaining("ORA-20031");
        assertThatThrownBy(() -> merged.update("DELETE FROM DISPENSACION")).hasMessageContaining("ORA-20030");
    }

    // ------------------------------------------------------------------ evidencia

    @Test
    void evidenceRowIsWrittenLastAndMapsMergedIds() {
        String evidence = merged.queryForObject(
                "SELECT VALOR FROM CONTROL_SISTEMA WHERE CLAVE = 'SITE_MERGE_EVIDENCE'", String.class);
        assertThat(evidence).contains("\"canonicalSiteId\":" + siteIds.get("it-site-2"))
                .contains("\"canonicalInstitutionId\":" + institutionIds.get("it-inst-1"))
                .contains(String.valueOf(siteIds.get("it-site-4")))
                .contains(String.valueOf(institutionIds.get("it-inst-2")));
        Integer evidenceIsLast = merged.queryForObject(
                "SELECT CASE WHEN (SELECT APLICADO_EN FROM CONTROL_SISTEMA WHERE CLAVE = 'SITE_MERGE_EVIDENCE') >= "
                        + "(SELECT MAX(CAPTURADO_AT) FROM CONCILIACION_MIGRACION WHERE MIGRACION = 'V024' AND FASE = 'DESPUES') "
                        + "THEN 1 ELSE 0 END FROM DUAL", Integer.class);
        assertThat(evidenceIsLast).isEqualTo(1);
        assertThat(count(merged, "SELECT COUNT(*) FROM CONCILIACION_MIGRACION WHERE FASE = 'MAPA_FUSION' "
                + "AND NOMBRE_TABLA = 'SEDE'")).isEqualTo(3);
        assertThat(count(merged, "SELECT COUNT(*) FROM CONCILIACION_MIGRACION WHERE FASE = 'MAPA_FUSION' "
                + "AND NOMBRE_TABLA = 'INSTITUCION'")).isEqualTo(2);
    }

    // ------------------------------------------------------------------ los prechequeos fallan de forma explícita

    @Test
    void precheckRaisesMinus20990WhenRecodedAreaCodeWouldOverflow() {
        assertThat(precheckMessage).contains("ORA-20990");
    }

    @Test
    void precheckLeavesTheSchemaUntouched() {
        assertThat(precheckFactsAfter).isEqualTo(precheckFactsBefore);
        assertThat(precheckLegacyIndexSurvived).isTrue();
        assertThat(precheckSettingsTableExists).isFalse();
        assertThat(precheckAlertColumnsExist).isFalse();
        assertThat(precheckBeforeRows).isZero();
    }

    @Test
    void precheckWritesACollisionReport() {
        assertThat(precheckReport).anyMatch(d -> d.startsWith("DUPLICATE_ACTIVE_ALERT") && d.contains("SATURACION_URGENCIAS"));
        assertThat(precheckReport).anyMatch(d -> d.startsWith("AREA_CODE_COLLISION") && d.contains("URG"));
        assertThat(precheckReport).anyMatch(d -> d.startsWith("AREA_CODE_OVERFLOW"));
    }

    @Test
    void migrationsRerunAfterRepairOnceTheDataIsFixed() {
        assertThat(precheckSitesAfterRecovery).isEqualTo(1);
        assertThat(precheckCodesAfterRecovery).hasSize(2).contains("SHORT");
    }

    // ------------------------------------------------------------------ una conciliación fallida revierte todo

    @Test
    void rowCountMismatchRaisesMinus20991AndRollsBackTheWholeMerge() {
        assertThat(rollbackCountMessage).contains("ORA-20991");
        assertThat(rollbackFactsAfterCount).isEqualTo(rollbackFactsBefore);
    }

    @Test
    void checksumMismatchRaisesMinus20991AndRollsBackTheWholeMerge() {
        assertThat(rollbackChecksumMessage).contains("ORA-20991");
        assertThat(rollbackFactsAfterChecksum).isEqualTo(rollbackFactsBefore);
    }

    @Test
    void afterTheRollbackTheSameDataMergesOnceTheMismatchIsGone() {
        assertThat(rollbackSitesAfterRecovery).isEqualTo(1);
    }

    // ------------------------------------------------------------------ preparación de V023

    @Test
    void v023DropsTheLegacyAlertIndexSoDuplicateActiveAlertsCanBeStored() {
        assertThat(indexExists(prepared, "UQ_ALERTA_SEDE_TIPO_ACTIVA")).isFalse();
        // Dos alertas ACTIVA TRIAGE_OVERDUE de episodios distintos en la misma sede ya no colisionan.
        prepared.update("INSERT INTO ALERTA_OPERATIVA (PUBLIC_ID, SEDE_ID, TIPO_ALERTA, NIVEL_SEVERIDAD, MENSAJE, ESTADO, EPISODIO_ID, NIVEL_TRIAJE) "
                + "VALUES ('it-overdue-1', (SELECT ID FROM SEDE WHERE PUBLIC_ID = 'it-site-3'), 'TRIAGE_OVERDUE', 'CRITICA', 'm', 'ACTIVA', "
                + "(SELECT ID FROM EPISODIO_ATENCION WHERE PUBLIC_ID = 'it-episode-2'), 'II')");
        prepared.update("INSERT INTO ALERTA_OPERATIVA (PUBLIC_ID, SEDE_ID, TIPO_ALERTA, NIVEL_SEVERIDAD, MENSAJE, ESTADO, EPISODIO_ID, NIVEL_TRIAJE) "
                + "VALUES ('it-overdue-2', (SELECT ID FROM SEDE WHERE PUBLIC_ID = 'it-site-3'), 'TRIAGE_OVERDUE', 'CRITICA', 'm', 'ACTIVA', "
                + "(SELECT ID FROM EPISODIO_ATENCION WHERE PUBLIC_ID = 'it-episode-3'), 'III')");
        assertThat(count(prepared, "SELECT COUNT(*) FROM ALERTA_OPERATIVA WHERE TIPO_ALERTA = 'TRIAGE_OVERDUE' AND ESTADO = 'ACTIVA'"))
                .isEqualTo(2);
        prepared.update("DELETE FROM ALERTA_OPERATIVA WHERE PUBLIC_ID IN ('it-overdue-1', 'it-overdue-2')");
    }

    @Test
    void v023AddsAlertEpisodeForeignKey() {
        assertThatThrownBy(() -> prepared.update("INSERT INTO ALERTA_OPERATIVA (PUBLIC_ID, SEDE_ID, TIPO_ALERTA, NIVEL_SEVERIDAD, MENSAJE, ESTADO, EPISODIO_ID) "
                + "VALUES ('it-bad-fk', (SELECT ID FROM SEDE WHERE PUBLIC_ID = 'it-site-3'), 'TRIAGE_OVERDUE', 'ALTA', 'm', 'ACTIVA', -1)"))
                .hasMessageContaining("ORA-02291");
    }

    @Test
    void v023CreatesTheSingletonSettingsTableWithoutPopulatingIt() {
        assertThat(tableExists(prepared, "CONFIGURACION_HOSPITAL")).isTrue();
        assertThat(count(prepared, "SELECT COUNT(*) FROM CONFIGURACION_HOSPITAL")).isZero();
        assertThatThrownBy(() -> prepared.update("INSERT INTO CONFIGURACION_HOSPITAL (ID, PUBLIC_ID, NIT, RAZON_SOCIAL, DIRECCION, CIUDAD) "
                + "VALUES (7, 'it-settings-7', 'x', 'x', 'x', 'x')")).hasMessageContaining("ORA-02290");
    }

    @Test
    void v023CapturesBeforeRowsOncePerTableEvenWhenRerun() {
        assertThat(count(prepared, "SELECT COUNT(*) FROM CONCILIACION_MIGRACION WHERE MIGRACION = 'V023' "
                + "AND FASE = 'ANTES' AND " + BASIS_ROWS)).isEqualTo(LEGACY_TABLE_COUNT);
        assertThat(count(prepared, "SELECT COUNT(DISTINCT NOMBRE_TABLA) FROM CONCILIACION_MIGRACION WHERE MIGRACION = 'V023' "
                + "AND FASE = 'ANTES' AND " + BASIS_ROWS)).isEqualTo(LEGACY_TABLE_COUNT);
        assertThat(count(prepared, "SELECT COUNT(*) FROM CONCILIACION_MIGRACION WHERE FASE = 'ANTES' AND DETALLE LIKE 'SEDE_ID=%'"))
                .isGreaterThanOrEqualTo(SITE_KEYED_TABLES.size());
        Map<String, Long> live = tableCounts(prepared);
        prepared.queryForList("SELECT NOMBRE_TABLA, CANTIDAD_FILAS FROM CONCILIACION_MIGRACION "
                + "WHERE FASE = 'ANTES' AND " + BASIS_ROWS).forEach(row ->
                assertThat(((Number) row.get("CANTIDAD_FILAS")).longValue()).as(String.valueOf(row.get("NOMBRE_TABLA")))
                        .isEqualTo(live.get(String.valueOf(row.get("NOMBRE_TABLA")))));
    }

    @Test
    void v023CreatesObjectsWithSpanishNamesOnly() {
        // Tablas y columnas de WU1a en español (tarea 1.6); los nombres en inglés ya no existen.
        assertThat(tableExists(prepared, "HOSPITAL_SETTINGS")).isFalse();
        assertThat(tableExists(prepared, "MIGRATION_RECONCILIATION")).isFalse();
        for (String column : List.of("ID", "PUBLIC_ID", "NIT", "RAZON_SOCIAL", "DIRECCION", "CIUDAD", "TELEFONO",
                "CREATED_AT", "UPDATED_AT")) {
            assertThat(columnExists(prepared, "CONFIGURACION_HOSPITAL", column)).as("CONFIGURACION_HOSPITAL." + column).isTrue();
        }
        for (String column : List.of("ID", "MIGRACION", "NOMBRE_TABLA", "FASE", "CANTIDAD_FILAS",
                "SUMA_CONTROL_PUBLIC_ID", "DETALLE", "CAPTURADO_AT")) {
            assertThat(columnExists(prepared, "CONCILIACION_MIGRACION", column)).as("CONCILIACION_MIGRACION." + column).isTrue();
        }
        assertThat(columnExists(prepared, "ALERTA_OPERATIVA", "CARE_EPISODE_ID")).isFalse();
        assertThat(columnExists(prepared, "ALERTA_OPERATIVA", "TRIAGE_LEVEL")).isFalse();
        for (String constraint : List.of("PK_CONFIGURACION_HOSPITAL", "UQ_CONFIG_HOSPITAL_PUBLIC_ID",
                "CK_CONFIG_HOSPITAL_ID", "PK_CONCILIACION_MIGRACION", "CK_CONCILIACION_MIGRACION_FASE",
                "FK_ALERTA_EPISODIO")) {
            assertThat(count(prepared, "SELECT COUNT(*) FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = '" + constraint + "'"))
                    .as(constraint).isEqualTo(1);
        }
        assertThat(indexExists(prepared, "IX_CONCILIACION_MIGRACION_BUSQUEDA")).isTrue();
        assertThat(indexExists(prepared, "IX_ALERTA_EPISODIO")).isTrue();
        assertThat(indexExists(prepared, "IX_MIGRATION_RECON_LOOKUP")).isFalse();
        assertThat(indexExists(prepared, "IX_ALERTA_CARE_EPISODE")).isFalse();
    }

    @Test
    void v023DoesNotMergeAnything() {
        assertThat(count(prepared, "SELECT COUNT(*) FROM SEDE")).isEqualTo(4);
        assertThat(count(prepared, "SELECT COUNT(*) FROM INSTITUCION")).isEqualTo(3);
    }

    // ------------------------------------------------------------------ base de datos nueva

    @Test
    void freshDatabaseEndsWithOneSiteOneInstitutionAndSettings() {
        assertThat(count(fresh, "SELECT COUNT(*) FROM SEDE")).isEqualTo(1);
        assertThat(count(fresh, "SELECT COUNT(*) FROM INSTITUCION")).isEqualTo(1);
        assertThat(count(fresh, "SELECT COUNT(*) FROM CONFIGURACION_HOSPITAL")).isEqualTo(1);
        assertThat(count(fresh, "SELECT COUNT(*) FROM SEDE WHERE ESTADO = 'ACTIVO'")).isEqualTo(1);
    }

    // ------------------------------------------------------------------ utilidades

    private static String migrateExpectingFailure(Flyway flyway) {
        try {
            flyway.migrate();
        } catch (RuntimeException e) {
            return String.valueOf(e.getMessage());
        }
        return "NO FAILURE";
    }

    private static int count(JdbcTemplate jdbc, String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private static boolean indexExists(JdbcTemplate jdbc, String name) {
        return count(jdbc, "SELECT COUNT(*) FROM USER_INDEXES WHERE INDEX_NAME = '" + name + "'") > 0;
    }

    private static boolean tableExists(JdbcTemplate jdbc, String name) {
        return count(jdbc, "SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME = '" + name + "'") > 0;
    }

    private static boolean columnExists(JdbcTemplate jdbc, String table, String column) {
        return count(jdbc, "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = '" + table
                + "' AND COLUMN_NAME = '" + column + "'") > 0;
    }

    private static Map<String, Long> idsByPublicId(JdbcTemplate jdbc, String table) {
        Map<String, Long> ids = new TreeMap<>();
        jdbc.queryForList("SELECT PUBLIC_ID, ID FROM " + table).forEach(
                row -> ids.put((String) row.get("PUBLIC_ID"), ((Number) row.get("ID")).longValue()));
        return ids;
    }

    /** Conteos independientes de las tablas de dominio heredadas (todo salvo Flyway y la contabilidad de migración). */
    private static Map<String, Long> tableCounts(JdbcTemplate jdbc) {
        List<String> tables = jdbc.queryForList(
                "SELECT TABLE_NAME FROM USER_TABLES WHERE UPPER(TABLE_NAME) <> 'FLYWAY_SCHEMA_HISTORY' "
                        + "AND TABLE_NAME NOT IN ('CONCILIACION_MIGRACION', 'CONFIGURACION_HOSPITAL') ORDER BY TABLE_NAME",
                String.class);
        Map<String, Long> counts = new TreeMap<>();
        for (String table : tables) {
            counts.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM \"" + table + "\"", Long.class));
        }
        return counts;
    }

    /** Huella plana de todo lo que la fusión puede tocar, para probar que una reversión no dejó rastro. */
    private static String facts(JdbcTemplate jdbc) {
        List<String> parts = new ArrayList<>();
        parts.add("sites=" + jdbc.queryForList("SELECT PUBLIC_ID || ':' || ESTADO FROM SEDE ORDER BY ID", String.class));
        parts.add("institutions=" + jdbc.queryForList("SELECT PUBLIC_ID || ':' || ESTADO FROM INSTITUCION ORDER BY ID", String.class));
        for (String table : SITE_KEYED_TABLES) {
            parts.add(table + "=" + jdbc.queryForList(
                    "SELECT PUBLIC_ID || '@' || SEDE_ID FROM " + table + " ORDER BY PUBLIC_ID", String.class));
        }
        parts.add("areaCodes=" + jdbc.queryForList("SELECT CODIGO FROM AREA_HOSPITALARIA ORDER BY PUBLIC_ID", String.class));
        parts.add("alerts=" + jdbc.queryForList(
                "SELECT PUBLIC_ID || ':' || ESTADO || ':' || NVL(MOTIVO_RECONOCIMIENTO, '-') FROM ALERTA_OPERATIVA ORDER BY PUBLIC_ID",
                String.class));
        parts.add("settings=" + (tableExists(jdbc, "CONFIGURACION_HOSPITAL")
                ? count(jdbc, "SELECT COUNT(*) FROM CONFIGURACION_HOSPITAL") : 0));
        parts.add("evidence=" + count(jdbc, "SELECT COUNT(*) FROM CONTROL_SISTEMA WHERE CLAVE = 'SITE_MERGE_EVIDENCE'"));
        parts.add("afterRows=" + (tableExists(jdbc, "CONCILIACION_MIGRACION")
                ? count(jdbc, "SELECT COUNT(*) FROM CONCILIACION_MIGRACION WHERE FASE IN ('DESPUES', 'MAPA_FUSION')") : 0));
        return String.join("\n", parts);
    }
}
