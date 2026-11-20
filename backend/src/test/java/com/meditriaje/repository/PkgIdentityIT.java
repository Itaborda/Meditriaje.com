package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.model.CodigoVerificacion;
import com.meditriaje.model.MfaBackupCode;
import com.meditriaje.model.RefreshToken;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.plsql.OracleErrorTranslator;
import com.meditriaje.repository.plsql.PackageGateway;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * PKG_IDENTITY contra Oracle Free real, llamado como MEDITRIAJE_APP a través de los repositorios migrados
 * (usuarios y roles, refresh tokens, códigos de respaldo MFA y códigos de verificación).
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PkgIdentityIT {

    private static final String OWNER = "MT_PKG_IDENTITY";

    private static OracleContainer oracle;
    private static JdbcTemplate owner;
    private static UsuarioRepository usuarios;
    private static RefreshTokenRepository tokens;
    private static MfaBackupCodeRepository backupCodes;
    private static CodigoVerificacionRepository codigos;

    @BeforeAll
    static void migrate() {
        oracle = OracleTestDatabase.newContainer();
        oracle.start();
        DataSource ownerDs = OracleTestDatabase.provisionOwner(oracle, OWNER);
        OracleTestDatabase.flyway(ownerDs).migrate();
        owner = new JdbcTemplate(ownerDs);
        DataSource appDs = OracleTestDatabase.dataSource(
                oracle, OracleTestDatabase.APP_USER, OracleTestDatabase.APP_PASSWORD);
        PackageGateway gateway = new PackageGateway(appDs, OWNER, new OracleErrorTranslator());
        usuarios = new UsuarioRepository(gateway);
        tokens = new RefreshTokenRepository(gateway);
        backupCodes = new MfaBackupCodeRepository(gateway);
        codigos = new CodigoVerificacionRepository(gateway);
    }

    @AfterAll
    static void stop() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    private static Long newUser(String email) {
        return usuarios.crear(UUID.randomUUID().toString(), email, "hash-" + email, false);
    }

    @Test
    void createsAndFindsUsersByEmailIdAndPublicId() {
        String publicId = UUID.randomUUID().toString();
        Long id = usuarios.crear(publicId, "ana@identity.test", "h1", true);

        assertThat(usuarios.existePorEmail("ana@identity.test")).isTrue();
        assertThat(usuarios.existePorEmail("nadie@identity.test")).isFalse();
        Usuario byEmail = usuarios.buscarPorEmail("ana@identity.test").orElseThrow();
        assertThat(byEmail.id()).isEqualTo(id);
        assertThat(byEmail.estado()).isEqualTo("ACTIVO");
        assertThat(byEmail.intentosFallidos()).isZero();
        assertThat(byEmail.debeCambiarPassword()).isTrue();
        assertThat(byEmail.mfaHabilitado()).isFalse();
        assertThat(byEmail.createdAt()).isNotNull();
        assertThat(usuarios.buscarPorId(id)).map(Usuario::publicId).contains(publicId);
        assertThat(usuarios.buscarPorPublicId(publicId)).map(Usuario::email).contains("ana@identity.test");
        assertThat(usuarios.buscarPorId(-1L)).isEmpty();
        assertThat(usuarios.buscarPorPublicId("no-existe")).isEmpty();
    }

    @Test
    void assignsAndListsRolesInAlphabeticalOrder() {
        Long id = newUser("roles@identity.test");
        Long admin = usuarios.buscarRolIdPorNombre("ROLE_ADMINISTRADOR").orElseThrow();
        Long patient = usuarios.buscarRolIdPorNombre("ROLE_PACIENTE").orElseThrow();
        usuarios.asignarRol(id, patient);
        usuarios.asignarRol(id, admin);

        assertThat(usuarios.obtenerRoles(id)).containsExactly("ROLE_ADMINISTRADOR", "ROLE_PACIENTE");
        assertThat(usuarios.buscarRolIdPorNombre("ROLE_INEXISTENTE")).isEmpty();
    }

    @Test
    void updatesAttemptsPasswordStatusAndMfa() {
        Long id = newUser("cambios@identity.test");
        Instant lockedUntil = Instant.now().plus(15, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);

        usuarios.actualizarIntentosFallidos(id, 5, lockedUntil, "BLOQUEADO");
        Usuario locked = usuarios.buscarPorId(id).orElseThrow();
        assertThat(locked.intentosFallidos()).isEqualTo(5);
        assertThat(locked.estado()).isEqualTo("BLOQUEADO");
        assertThat(locked.bloqueadoHasta()).isEqualTo(lockedUntil);

        usuarios.restablecerIntentos(id);
        Usuario reset = usuarios.buscarPorId(id).orElseThrow();
        assertThat(reset.intentosFallidos()).isZero();
        assertThat(reset.bloqueadoHasta()).isNull();
        assertThat(reset.estado()).isEqualTo("ACTIVO");

        usuarios.actualizarPassword(id, "nuevo", true);
        assertThat(usuarios.buscarPorId(id).orElseThrow().passwordHash()).isEqualTo("nuevo");
        usuarios.actualizarEstado(id, "INACTIVO");
        assertThat(usuarios.buscarPorId(id).orElseThrow().estado()).isEqualTo("INACTIVO");

        usuarios.guardarMfaSecret(id, "SECRETO1");
        assertThat(usuarios.buscarPorId(id).orElseThrow().mfaSecret()).isEqualTo("SECRETO1");
        usuarios.activarMfa(id, "SECRETO2");
        Usuario mfa = usuarios.buscarPorId(id).orElseThrow();
        assertThat(mfa.mfaHabilitado()).isTrue();
        assertThat(mfa.mfaSecret()).isEqualTo("SECRETO2");
        assertThat(mfa.mfaConfiguradoAt()).isNotNull();
        usuarios.desactivarMfa(id);
        Usuario noMfa = usuarios.buscarPorId(id).orElseThrow();
        assertThat(noMfa.mfaHabilitado()).isFalse();
        assertThat(noMfa.mfaSecret()).isNull();
        assertThat(noMfa.mfaConfiguradoAt()).isNull();
    }

    @Test
    void refreshTokensAreCreatedFoundAndRevoked() {
        Long userId = newUser("tokens@identity.test");
        Instant expires = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Long t1 = tokens.crear(userId, "tok-1", expires);
        tokens.crear(userId, "tok-2", expires);
        tokens.crear(userId, "tok-3", expires);

        RefreshToken found = tokens.buscarPorTokenHash("tok-1").orElseThrow();
        assertThat(found.id()).isEqualTo(t1);
        assertThat(found.usuarioId()).isEqualTo(userId);
        assertThat(found.expiracion()).isEqualTo(expires);
        assertThat(found.revocado()).isFalse();
        assertThat(tokens.buscarPorTokenHash("tok-x")).isEmpty();

        tokens.revocar(t1);
        assertThat(tokens.buscarPorTokenHash("tok-1").orElseThrow().revocado()).isTrue();
        tokens.revocarPorTokenHash("tok-2");
        assertThat(tokens.buscarPorTokenHash("tok-2").orElseThrow().revocado()).isTrue();
        tokens.revocarTodosPorUsuario(userId);
        assertThat(tokens.buscarPorTokenHash("tok-3").orElseThrow().revocado()).isTrue();
    }

    @Test
    void backupCodesAreSavedConsumedCountedAndDeleted() {
        Long userId = newUser("mfa@identity.test");
        backupCodes.guardarLote(userId, List.of("c1", "c2", "c3"));

        assertThat(backupCodes.contarDisponibles(userId)).isEqualTo(3);
        assertThat(backupCodes.consumirCodigo(userId, "c2")).isTrue();
        assertThat(backupCodes.consumirCodigo(userId, "c2")).isFalse();
        assertThat(backupCodes.contarDisponibles(userId)).isEqualTo(2);
        List<MfaBackupCode> all = backupCodes.listarPorUsuario(userId);
        assertThat(all).extracting(MfaBackupCode::codeHash).containsExactly("c1", "c2", "c3");
        assertThat(all.get(1).usado()).isTrue();
        assertThat(all.get(1).usadoAt()).isNotNull();

        backupCodes.eliminarPorUsuario(userId);
        assertThat(backupCodes.listarPorUsuario(userId)).isEmpty();
        assertThat(backupCodes.contarDisponibles(userId)).isZero();
    }

    @Test
    void verificationCodesFollowTheirLifecycle() {
        Long userId = newUser("codigos@identity.test");
        Instant expires = Instant.now().plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        Long first = codigos.crear(new CodigoVerificacion(
                null, UUID.randomUUID().toString(), userId, "RECUPERACION_PASSWORD", "hash-1", expires, 0, 3, false,
                null));
        Long second = codigos.crear(new CodigoVerificacion(
                null, UUID.randomUUID().toString(), userId, "RECUPERACION_PASSWORD", "hash-2", expires, 0, 3, false,
                null));

        CodigoVerificacion byId = codigos.buscarPorId(first).orElseThrow();
        assertThat(byId.codigoHash()).isEqualTo("hash-1");
        assertThat(byId.fechaExpiracion()).isEqualTo(expires);
        assertThat(codigos.buscarPorId(-1L)).isEmpty();
        assertThat(codigos.buscarUltimoPendientePorUsuarioYTipo(userId, "RECUPERACION_PASSWORD"))
                .map(CodigoVerificacion::id).contains(second);

        codigos.incrementarIntentos(second);
        assertThat(codigos.buscarPorId(second).orElseThrow().intentosFallidos()).isEqualTo(1);
        codigos.marcarComoUsado(second);
        assertThat(codigos.buscarUltimoPendientePorUsuarioYTipo(userId, "RECUPERACION_PASSWORD"))
                .map(CodigoVerificacion::id).contains(first);
        codigos.invalidarCodigosPrevios(userId, "RECUPERACION_PASSWORD");
        assertThat(codigos.buscarUltimoPendientePorUsuarioYTipo(userId, "RECUPERACION_PASSWORD")).isEmpty();
        assertThat(owner.queryForObject(
                "SELECT COUNT(*) FROM CODIGO_VERIFICACION WHERE USUARIO_ID = ? AND USADO = 1", Integer.class, userId))
                .isEqualTo(2);
    }
}
