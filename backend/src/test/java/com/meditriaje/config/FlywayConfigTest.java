package com.meditriaje.config;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class FlywayConfigTest {

    @Test
    void healthyHistoryDoesNotRepair() {
        MigrationInfo success = migration("V027__widen_qr_pin_hash.sql", MigrationState.SUCCESS, false);
        MigrationInfo pending = migration("V028__drop_legacy_pkg_censo_hospitalario.sql", MigrationState.PENDING, false);
        Flyway flyway = mockFlywayWith(success, pending);

        FlywayConfig.migrate(flyway);

        verify(flyway, never()).repair();
        verify(flyway).migrate();
    }

    @Test
    void emptyHistoryDoesNotRepair() {
        Flyway flyway = mockFlywayWith();

        FlywayConfig.migrate(flyway);

        verify(flyway, never()).repair();
        verify(flyway).migrate();
    }

    @Test
    void failedRepeatableMigrationTriggersRepairBeforeMigrate() {
        MigrationInfo failedRepeatable =
                migration("R__zz_recompile_and_grants.sql", MigrationState.FAILED, true);
        Flyway flyway = mockFlywayWith(failedRepeatable);

        FlywayConfig.migrate(flyway);

        InOrder order = inOrder(flyway);
        order.verify(flyway).repair();
        order.verify(flyway).migrate();
    }

    @Test
    void failedVersionedMigrationDoesNotAutoRepair() {
        MigrationInfo failedVersioned =
                migration("V026__eliminar_sedes_e_instituciones.sql", MigrationState.FAILED, false);
        Flyway flyway = mockFlywayWith(failedVersioned);

        FlywayConfig.migrate(flyway);

        verify(flyway, never()).repair();
        verify(flyway).migrate();
    }

    @Test
    void mixedFailedVersionedAndRepeatableDoesNotAutoRepair() {
        MigrationInfo failedRepeatable =
                migration("R__zz_recompile_and_grants.sql", MigrationState.FAILED, true);
        MigrationInfo failedVersioned =
                migration("V026__eliminar_sedes_e_instituciones.sql", MigrationState.FAILED, false);
        Flyway flyway = mockFlywayWith(failedRepeatable, failedVersioned);

        FlywayConfig.migrate(flyway);

        verify(flyway, never()).repair();
        verify(flyway).migrate();
    }

    private static Flyway mockFlywayWith(MigrationInfo... migrations) {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService infoService = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(infoService);
        when(infoService.all()).thenReturn(migrations);
        return flyway;
    }

    private static MigrationInfo migration(String script, MigrationState state, boolean repeatable) {
        MigrationInfo info = mock(MigrationInfo.class);
        when(info.getState()).thenReturn(state);
        if (state.isFailed()) {
            when(info.getScript()).thenReturn(script);
            when(info.isRepeatable()).thenReturn(repeatable);
        }
        return info;
    }
}
