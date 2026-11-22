package com.meditriaje.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway startup configuration that safely self-heals failed repeatable migrations
 * (for example {@code R__zz_recompile_and_grants.sql} after cleaning up invalid schema objects)
 * without ever masking or auto-repairing failed versioned migrations.
 */
@Configuration
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true")
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    /** Returns the Flyway migration strategy used when Flyway is enabled at startup. */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return FlywayConfig::migrate;
    }

    static void migrate(Flyway flyway) {
        MigrationInfoService infoService = flyway.info();
        MigrationInfo[] all = infoService != null ? infoService.all() : null;
        if (all != null && all.length > 0) {
            List<MigrationInfo> failed = Arrays.stream(all)
                    .filter(info -> info != null && info.getState() != null && info.getState().isFailed())
                    .toList();
            if (!failed.isEmpty()) {
                String scripts = failed.stream()
                        .map(FlywayConfig::describeMigration)
                        .collect(Collectors.joining(", "));
                boolean onlyRepeatable = failed.stream().allMatch(MigrationInfo::isRepeatable);
                if (onlyRepeatable) {
                    log.warn(
                            "Detected failed repeatable Flyway migration(s) [{}]; running repair() before migrate().",
                            scripts);
                    flyway.repair();
                } else {
                    log.error(
                            "Detected failed versioned Flyway migration(s) [{}]; skipping automatic repair().",
                            scripts);
                }
            }
        }
        flyway.migrate();
    }

    private static String describeMigration(MigrationInfo info) {
        String script = info.getScript();
        if (script != null && !script.isBlank()) {
            return script;
        }
        String description = info.getDescription();
        return (description != null && !description.isBlank()) ? description : "unknown";
    }
}
