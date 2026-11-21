package com.meditriaje.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import javax.sql.DataSource;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WU1b (tarea 1.9): {@code UQ_ALERTA_OPERATIVA_ACTIVA} garantiza una alerta ACTIVA por tipo para las
 * alertas de sede y una por (tipo, episodio) para las alertas ligadas a un episodio, sin depender de
 * la sede (que tras la fusión es única).
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class AlertUniquenessIT {

    private static final String INSERT = "INSERT INTO ALERTA_OPERATIVA (PUBLIC_ID, TIPO_ALERTA, NIVEL_SEVERIDAD, "
            + "MENSAJE, ESTADO, EPISODIO_ID) VALUES (?, ?, 'ALTA', 'm', ?, "
            + "(SELECT ID FROM EPISODIO_ATENCION WHERE PUBLIC_ID = ?))";

    private static OracleContainer oracle;
    private static JdbcTemplate owner;

    @BeforeAll
    static void migrate() {
        oracle = OracleTestDatabase.newContainer();
        oracle.start();
        DataSource ds = OracleTestDatabase.provisionOwner(oracle, "MT_ALERTAS");
        OracleTestDatabase.flywayUpTo(ds, "22").migrate();
        OracleTestDatabase.runScript(ds, "db/merge-fixture.sql");
        OracleTestDatabase.flyway(ds).migrate();
        owner = new JdbcTemplate(ds);
    }

    @AfterAll
    static void stopContainer() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    private static void insert(String publicId, String type, String state, String episodePublicId) {
        owner.update(
                INSERT,
                new Object[]{publicId, type, state, episodePublicId},
                new int[]{Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR});
    }

    @Test
    void twoActiveAlertsOfTheSameTypeOnDifferentEpisodesCoexist() {
        insert("uq-a1", "TRIAJE_VENCIDO", "ACTIVA", "it-episode-2");
        insert("uq-a2", "TRIAJE_VENCIDO", "ACTIVA", "it-episode-3");
        assertThat(owner.queryForObject(
                "SELECT COUNT(*) FROM ALERTA_OPERATIVA WHERE TIPO_ALERTA = 'TRIAJE_VENCIDO' AND ESTADO = 'ACTIVA'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void sameTypeAndSameEpisodeIsRejectedWithOra00001() {
        insert("uq-b1", "OTRO_CON_EPISODIO", "ACTIVA", "it-episode-2");
        assertThatThrownBy(() -> insert("uq-b2", "OTRO_CON_EPISODIO", "ACTIVA", "it-episode-2"))
                .hasMessageContaining("ORA-00001")
                .hasMessageContaining("UQ_ALERTA_OPERATIVA_ACTIVA");
    }

    @Test
    void siteLevelAlertIsUniquePerType() {
        insert("uq-c1", "SATURACION_NUEVA", "ACTIVA", null);
        assertThatThrownBy(() -> insert("uq-c2", "SATURACION_NUEVA", "ACTIVA", null))
                .hasMessageContaining("ORA-00001");
    }

    @Test
    void onlyActiveAlertsCountTowardsUniqueness() {
        insert("uq-d1", "TIPO_CICLO", "ACTIVA", null);
        owner.update("UPDATE ALERTA_OPERATIVA SET ESTADO = 'RESUELTA' WHERE PUBLIC_ID = 'uq-d1'");
        insert("uq-d2", "TIPO_CICLO", "ACTIVA", null);
        insert("uq-d3", "TIPO_CICLO", "RECONOCIDA", null);
        assertThat(owner.queryForObject(
                "SELECT COUNT(*) FROM ALERTA_OPERATIVA WHERE TIPO_ALERTA = 'TIPO_CICLO'", Integer.class)).isEqualTo(3);
    }

    @Test
    void legacySiteScopedIndexIsReplaced() {
        assertThat(owner.queryForObject(
                "SELECT COUNT(*) FROM USER_INDEXES WHERE INDEX_NAME = 'UQ_ALERTA_SEDE_TIPO_ACTIVA'", Integer.class)).isZero();
        assertThat(owner.queryForObject(
                "SELECT COUNT(*) FROM USER_INDEXES WHERE INDEX_NAME = 'UQ_ALERTA_OPERATIVA_ACTIVA' AND UNIQUENESS = 'UNIQUE'",
                Integer.class)).isEqualTo(1);
    }
}
