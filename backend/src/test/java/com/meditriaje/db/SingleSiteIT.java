package com.meditriaje.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WU1b (tarea 1.10): {@code TR_SEDE_UNICA} y {@code TR_INSTITUCION_UNICA} mantienen una sola sede y una
 * sola institución. Un segundo INSERT se rechaza con ORA-20060.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class SingleSiteIT {

    private static OracleContainer oracle;
    private static JdbcTemplate owner;

    @BeforeAll
    static void migrate() {
        oracle = OracleTestDatabase.newContainer();
        oracle.start();
        DataSource ds = OracleTestDatabase.provisionOwner(oracle, "MT_SEDE_UNICA");
        OracleTestDatabase.flywayUpTo(ds, "25").migrate();
        owner = new JdbcTemplate(ds);
    }

    @AfterAll
    static void stopContainer() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    @Test
    void freshDatabaseHasExactlyOneSiteAndOneInstitution() {
        assertThat(owner.queryForObject("SELECT COUNT(*) FROM SEDE", Integer.class)).isEqualTo(1);
        assertThat(owner.queryForObject("SELECT COUNT(*) FROM INSTITUCION", Integer.class)).isEqualTo(1);
    }

    @Test
    void secondSiteIsRejectedWithOra20060() {
        assertThatThrownBy(() -> owner.update("INSERT INTO SEDE (INSTITUCION_ID, PUBLIC_ID, NOMBRE, DIRECCION, CIUDAD, ESTADO) "
                + "SELECT INSTITUCION_ID, 'ss-site-2', 'Segunda', 'Calle 2', 'Cali', 'ACTIVO' FROM SEDE"))
                .hasMessageContaining("ORA-20060");
        assertThat(owner.queryForObject("SELECT COUNT(*) FROM SEDE", Integer.class)).isEqualTo(1);
    }

    @Test
    void secondInstitutionIsRejectedWithOra20060() {
        assertThatThrownBy(() -> owner.update("INSERT INTO INSTITUCION (PUBLIC_ID, NIT, RAZON_SOCIAL, ESTADO) "
                + "VALUES ('ss-inst-2', '900999999-9', 'Otra IPS', 'ACTIVO')"))
                .hasMessageContaining("ORA-20060");
        assertThat(owner.queryForObject("SELECT COUNT(*) FROM INSTITUCION", Integer.class)).isEqualTo(1);
    }

    @Test
    void updatesOfTheExistingRowsStillWork() {
        owner.update("UPDATE SEDE SET CIUDAD = 'Valledupar'");
        owner.update("UPDATE INSTITUCION SET RAZON_SOCIAL = 'IPS Ajustada'");
        assertThat(owner.queryForObject("SELECT CIUDAD FROM SEDE", String.class)).isEqualTo("Valledupar");
    }

    @Test
    void areaCodeUniquenessPerSiteStaysValid() {
        assertThat(owner.queryForObject(
                "SELECT COUNT(*) FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'UQ_AREA_SEDE_CODIGO' "
                        + "AND CONSTRAINT_TYPE = 'U' AND STATUS = 'ENABLED' AND VALIDATED = 'VALIDATED'", Integer.class)).isEqualTo(1);
        owner.update("INSERT INTO AREA_HOSPITALARIA (PUBLIC_ID, SEDE_ID, CODIGO, NOMBRE, TIPO) "
                + "VALUES ('ss-area-1', (SELECT ID FROM SEDE), 'URG', 'Urgencias', 'URGENCIAS')");
        assertThatThrownBy(() -> owner.update("INSERT INTO AREA_HOSPITALARIA (PUBLIC_ID, SEDE_ID, CODIGO, NOMBRE, TIPO) "
                + "VALUES ('ss-area-2', (SELECT ID FROM SEDE), 'URG', 'Otra', 'URGENCIAS')"))
                .hasMessageContaining("ORA-00001");
    }

    @Test
    void aReplacementSiteCanBeInsertedOnlyAfterTheOldOneIsGone() {
        // El trigger cuenta filas tras la sentencia: reemplazar la única sede (sin referencias) es válido,
        // pero una sentencia que deje dos filas se rechaza aunque la tabla estuviera vacía.
        owner.update("DELETE FROM AREA_HOSPITALARIA");
        owner.update("DELETE FROM SEDE");
        assertThatThrownBy(() -> owner.update("INSERT INTO SEDE (INSTITUCION_ID, PUBLIC_ID, NOMBRE, DIRECCION, CIUDAD, ESTADO) "
                + "SELECT (SELECT ID FROM INSTITUCION), 'ss-bulk-' || LEVEL, 'Masiva', 'Calle', 'Cali', 'ACTIVO' FROM DUAL CONNECT BY LEVEL <= 2"))
                .hasMessageContaining("ORA-20060");
        owner.update("INSERT INTO SEDE (INSTITUCION_ID, PUBLIC_ID, NOMBRE, DIRECCION, CIUDAD, ESTADO) "
                + "SELECT ID, 'ss-site-new', 'Nueva', 'Calle', 'Cali', 'ACTIVO' FROM INSTITUCION");
        assertThat(owner.queryForObject("SELECT COUNT(*) FROM SEDE", Integer.class)).isEqualTo(1);
    }
}
