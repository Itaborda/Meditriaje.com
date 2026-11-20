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
 * WU1b (tareas 1.7/1.8): tras migrar hasta V025, el trigger {@code TR_DISPENSACION_INMUTABILIDAD}
 * vuelve a ser estricto. V023 lo relajó solo para que V024 reasignara {@code SEDE_ID}; V025 debe
 * restaurar el comportamiento de V013 incluso para la sesión del propietario del esquema.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class DispensationImmutabilityIT {

    private static OracleContainer oracle;
    private static JdbcTemplate owner;

    @BeforeAll
    static void migrate() {
        oracle = OracleTestDatabase.newContainer();
        oracle.start();
        DataSource ds = OracleTestDatabase.provisionOwner(oracle, "MT_DISPENSACION");
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

    @Test
    void ownerSessionCannotRepointSedeIdToAnotherSite() {
        // Se necesita una segunda sede para que el UPDATE cambie realmente SEDE_ID. TR_SEDE_UNICA
        // (V025) impide crearla, por eso se deshabilita solo durante este bloque si ya existe.
        boolean singleSiteGuard = owner.queryForObject(
                "SELECT COUNT(*) FROM USER_TRIGGERS WHERE TRIGGER_NAME = 'TR_SEDE_UNICA'", Integer.class) > 0;
        if (singleSiteGuard) {
            owner.execute("ALTER TRIGGER TR_SEDE_UNICA DISABLE");
        }
        try {
            owner.update("INSERT INTO SEDE (INSTITUCION_ID, PUBLIC_ID, NOMBRE, DIRECCION, CIUDAD, ESTADO) "
                    + "SELECT INSTITUCION_ID, 'it-site-extra', 'Extra', 'Calle 9', 'Bogota', 'ACTIVO' FROM SEDE WHERE ROWNUM = 1");
            assertThatThrownBy(() -> owner.update("UPDATE DISPENSACION SET SEDE_ID = "
                    + "(SELECT ID FROM SEDE WHERE PUBLIC_ID = 'it-site-extra') WHERE PUBLIC_ID = 'it-disp-1'"))
                    .hasMessageContaining("ORA-20031");
        } finally {
            owner.update("DELETE FROM SEDE WHERE PUBLIC_ID = 'it-site-extra'");
            if (singleSiteGuard) {
                owner.execute("ALTER TRIGGER TR_SEDE_UNICA ENABLE");
            }
        }
    }

    @Test
    void ownerSessionCannotUpdateAnyColumn() {
        assertThatThrownBy(() -> owner.update("UPDATE DISPENSACION SET SEDE_ID = SEDE_ID"))
                .hasMessageContaining("ORA-20031");
        assertThatThrownBy(() -> owner.update("UPDATE DISPENSACION SET OBSERVACIONES = 'alterada'"))
                .hasMessageContaining("ORA-20031");
    }

    @Test
    void deleteIsRejectedWithOra20030() {
        assertThatThrownBy(() -> owner.update("DELETE FROM DISPENSACION")).hasMessageContaining("ORA-20030");
    }

    @Test
    void dispensationRowsKeepTheirCanonicalSiteAfterTheMerge() {
        assertThat(owner.queryForList("SELECT DISTINCT SEDE_ID FROM DISPENSACION", Long.class)).hasSize(1);
        assertThat(owner.queryForObject("SELECT COUNT(*) FROM DISPENSACION", Integer.class)).isEqualTo(2);
    }
}
