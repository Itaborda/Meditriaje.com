package com.meditriaje.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.oracle.OracleContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Utilidad compartida para las pruebas de integración sobre Oracle Free (Testcontainers).
 *
 * <p>El usuario privilegiado del contenedor (SYS) hace el papel del usuario ADMIN de ATP: crea el
 * propietario del esquema con los mismos privilegios que MEDITRIAJE_OWNER en producción y el usuario
 * de ejecución MEDITRIAJE_APP sin privilegios propios (las migraciones le conceden lo necesario).
 * Cada escenario obtiene su propio esquema propietario, de modo que varios estados de migración
 * conviven en un mismo contenedor.</p>
 */
public final class OracleTestDatabase {

    public static final String IMAGE = "gvenzl/oracle-free:23-slim-faststart";
    public static final String APP_USER = "MEDITRIAJE_APP";
    public static final String APP_PASSWORD = "App_Pass1";
    public static final String OWNER_PASSWORD = "Owner_Pass1";
    /** SYS hace el papel del usuario ADMIN de ATP. */
    private static final String ADMIN_USER = "sys";

    private OracleTestDatabase() {
    }

    public static OracleContainer newContainer() {
        return new OracleContainer(IMAGE).withPassword("testPassword1").withReuse(false);
    }

    public static DataSource dataSource(OracleContainer container, String user, String password) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("oracle.jdbc.OracleDriver");
        ds.setUrl(container.getJdbcUrl());
        ds.setUsername(user);
        ds.setPassword(password);
        return ds;
    }

    private static JdbcTemplate admin(OracleContainer container) {
        DriverManagerDataSource ds = (DriverManagerDataSource) dataSource(container, ADMIN_USER, container.getPassword());
        Properties properties = new Properties();
        properties.setProperty("internal_logon", "sysdba");
        ds.setConnectionProperties(properties);
        return new JdbcTemplate(ds);
    }

    /** Crea una sola vez el usuario de ejecución; las migraciones le conceden los permisos. */
    public static void ensureAppUser(OracleContainer container) {
        JdbcTemplate admin = admin(container);
        Integer exists = admin.queryForObject(
                "SELECT COUNT(*) FROM ALL_USERS WHERE USERNAME = ?", Integer.class, APP_USER);
        if (exists != null && exists == 0) {
            admin.execute("CREATE USER " + APP_USER + " IDENTIFIED BY \"" + APP_PASSWORD + "\"");
            admin.execute("GRANT CREATE SESSION TO " + APP_USER);
        }
    }

    /** Crea un propietario de esquema con los mismos privilegios que MEDITRIAJE_OWNER en ATP. */
    public static DataSource provisionOwner(OracleContainer container, String owner) {
        ensureAppUser(container);
        JdbcTemplate admin = admin(container);
        admin.execute("CREATE USER " + owner + " IDENTIFIED BY \"" + OWNER_PASSWORD + "\"");
        admin.execute("GRANT CONNECT, RESOURCE, CREATE SESSION, CREATE VIEW TO " + owner);
        admin.execute("ALTER USER " + owner + " QUOTA UNLIMITED ON USERS");
        return dataSource(container, owner, OWNER_PASSWORD);
    }

    public static Flyway flyway(DataSource ownerDataSource) {
        return Flyway.configure()
                .dataSource(ownerDataSource)
                .locations("classpath:database/migrations")
                .outOfOrder(false)
                .validateOnMigrate(true)
                .load();
    }

    public static Flyway flywayUpTo(DataSource ownerDataSource, String targetVersion) {
        return Flyway.configure()
                .dataSource(ownerDataSource)
                .locations("classpath:database/migrations")
                .repeatableSqlMigrationPrefix("NO_REPEATABLES__")
                .outOfOrder(false)
                .validateOnMigrate(true)
                .target(MigrationVersion.fromVersion(targetVersion))
                .load();
    }

    /** Ejecuta un script SQL del classpath (sentencias separadas por punto y coma). */
    public static void runScript(DataSource ownerDataSource, String classpathLocation) {
        try (Connection connection = ownerDataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(classpathLocation));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not run " + classpathLocation, e);
        }
    }
}
