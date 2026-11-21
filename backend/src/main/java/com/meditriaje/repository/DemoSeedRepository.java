package com.meditriaje.repository;

import com.meditriaje.repository.plsql.PackageGateway;
import com.meditriaje.repository.plsql.ProcedureSpec;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Objects;

/**
 * Repositorio de apoyo para el sembrador de demostración ({@code DemoDataSeeder}).
 * Delega las consultas de aislamiento y ajuste de slots en {@code PKG_OPERATIONS} mediante {@link PackageGateway}.
 */
@Repository
public class DemoSeedRepository {

    static final String PACKAGE = "PKG_OPERATIONS";

    static final ProcedureSpec COUNT_USERS_OUTSIDE_DOMAIN =
            ProcedureSpec.function(PACKAGE, "count_users_outside_domain", "result", Types.NUMERIC)
                    .in("p_domain", Types.VARCHAR)
                    .build();

    static final ProcedureSpec COUNT_USERS_IN_DOMAIN =
            ProcedureSpec.function(PACKAGE, "count_users_in_domain", "result", Types.NUMERIC)
                    .in("p_domain", Types.VARCHAR)
                    .build();

    static final ProcedureSpec SHIFT_SLOT_TO_PAST_FOR_DEMO =
            ProcedureSpec.procedure(PACKAGE, "shift_slot_to_past_for_demo")
                    .in("p_slot_public_id", Types.VARCHAR)
                    .build();

    private final PackageGateway gateway;

    public DemoSeedRepository(PackageGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway no puede ser nulo");
    }

    public int countUsersOutsideDomain(String dominio) {
        Number count = gateway.callFunction(
                COUNT_USERS_OUTSIDE_DOMAIN,
                new MapSqlParameterSource().addValue("p_domain", dominio, Types.VARCHAR));
        return count != null ? count.intValue() : 0;
    }

    public int countUsersInDomain(String dominio) {
        Number count = gateway.callFunction(
                COUNT_USERS_IN_DOMAIN,
                new MapSqlParameterSource().addValue("p_domain", dominio, Types.VARCHAR));
        return count != null ? count.intValue() : 0;
    }

    public void moveSlotToPastForDemo(String slotPublicId) {
        gateway.execute(
                SHIFT_SLOT_TO_PAST_FOR_DEMO,
                new MapSqlParameterSource().addValue("p_slot_public_id", slotPublicId, Types.VARCHAR));
    }
}
