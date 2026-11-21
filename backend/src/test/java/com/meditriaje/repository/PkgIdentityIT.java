package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.model.VerificationCode;
import com.meditriaje.model.MfaBackupCode;
import com.meditriaje.model.RefreshToken;
import com.meditriaje.model.User;
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
    private static UserRepository usuarios;
    private static RefreshTokenRepository tokens;
    private static MfaBackupCodeRepository backupCodes;
    private static VerificationCodeRepository codigos;

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
        usuarios = new UserRepository(gateway);
        tokens = new RefreshTokenRepository(gateway);
        backupCodes = new MfaBackupCodeRepository(gateway);
        codigos = new VerificationCodeRepository(gateway);
    }

    @AfterAll
    static void stop() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    private static Long newUser(String email) {
        return usuarios.create(UUID.randomUUID().toString(), email, "hash-" + email, false);
    }

    @Test
    void createsAndFindsUsersByEmailIdAndPublicId() {
        String publicId = UUID.randomUUID().toString();
        Long id = usuarios.create(publicId, "ana@identity.test", "h1", true);

        assertThat(usuarios.existsByEmail("ana@identity.test")).isTrue();
        assertThat(usuarios.existsByEmail("nadie@identity.test")).isFalse();
        User byEmail = usuarios.findByEmail("ana@identity.test").orElseThrow();
        assertThat(byEmail.id()).isEqualTo(id);
        assertThat(byEmail.status()).isEqualTo("ACTIVO");
        assertThat(byEmail.failedAttempts()).isZero();
        assertThat(byEmail.mustChangePassword()).isTrue();
        assertThat(byEmail.mfaEnabled()).isFalse();
        assertThat(byEmail.createdAt()).isNotNull();
        assertThat(usuarios.findById(id)).map(User::publicId).contains(publicId);
        assertThat(usuarios.findByPublicId(publicId)).map(User::email).contains("ana@identity.test");
        assertThat(usuarios.findById(-1L)).isEmpty();
        assertThat(usuarios.findByPublicId("no-existe")).isEmpty();
    }

    @Test
    void assignsAndListsRolesInAlphabeticalOrder() {
        Long id = newUser("roles@identity.test");
        Long admin = usuarios.findRoleIdByName("ROLE_ADMINISTRADOR").orElseThrow();
        Long patient = usuarios.findRoleIdByName("ROLE_PACIENTE").orElseThrow();
        usuarios.assignRole(id, patient);
        usuarios.assignRole(id, admin);

        assertThat(usuarios.getRoles(id)).containsExactly("ROLE_ADMINISTRADOR", "ROLE_PACIENTE");
        assertThat(usuarios.findRoleIdByName("ROLE_INEXISTENTE")).isEmpty();
    }

    @Test
    void updatesAttemptsPasswordStatusAndMfa() {
        Long id = newUser("cambios@identity.test");
        Instant lockedUntil = Instant.now().plus(15, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);

        usuarios.updateFailedAttempts(id, 5, lockedUntil, "BLOQUEADO");
        User locked = usuarios.findById(id).orElseThrow();
        assertThat(locked.failedAttempts()).isEqualTo(5);
        assertThat(locked.status()).isEqualTo("BLOQUEADO");
        assertThat(locked.lockedUntil()).isEqualTo(lockedUntil);

        usuarios.resetAttempts(id);
        User reset = usuarios.findById(id).orElseThrow();
        assertThat(reset.failedAttempts()).isZero();
        assertThat(reset.lockedUntil()).isNull();
        assertThat(reset.status()).isEqualTo("ACTIVO");

        usuarios.updatePassword(id, "nuevo", true);
        assertThat(usuarios.findById(id).orElseThrow().passwordHash()).isEqualTo("nuevo");
        usuarios.updateStatus(id, "INACTIVO");
        assertThat(usuarios.findById(id).orElseThrow().status()).isEqualTo("INACTIVO");

        usuarios.saveMfaSecret(id, "SECRETO1");
        assertThat(usuarios.findById(id).orElseThrow().mfaSecret()).isEqualTo("SECRETO1");
        usuarios.enableMfa(id, "SECRETO2");
        User mfa = usuarios.findById(id).orElseThrow();
        assertThat(mfa.mfaEnabled()).isTrue();
        assertThat(mfa.mfaSecret()).isEqualTo("SECRETO2");
        assertThat(mfa.mfaConfiguredAt()).isNotNull();
        usuarios.disableMfa(id);
        User noMfa = usuarios.findById(id).orElseThrow();
        assertThat(noMfa.mfaEnabled()).isFalse();
        assertThat(noMfa.mfaSecret()).isNull();
        assertThat(noMfa.mfaConfiguredAt()).isNull();
    }

    @Test
    void refreshTokensAreCreatedFoundAndRevoked() {
        Long userId = newUser("tokens@identity.test");
        Instant expires = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Long t1 = tokens.create(userId, "tok-1", expires);
        tokens.create(userId, "tok-2", expires);
        tokens.create(userId, "tok-3", expires);

        RefreshToken found = tokens.findByTokenHash("tok-1").orElseThrow();
        assertThat(found.id()).isEqualTo(t1);
        assertThat(found.userId()).isEqualTo(userId);
        assertThat(found.expiresAt()).isEqualTo(expires);
        assertThat(found.revoked()).isFalse();
        assertThat(tokens.findByTokenHash("tok-x")).isEmpty();

        tokens.revoke(t1);
        assertThat(tokens.findByTokenHash("tok-1").orElseThrow().revoked()).isTrue();
        tokens.revokeByTokenHash("tok-2");
        assertThat(tokens.findByTokenHash("tok-2").orElseThrow().revoked()).isTrue();
        tokens.revokeAllByUser(userId);
        assertThat(tokens.findByTokenHash("tok-3").orElseThrow().revoked()).isTrue();
    }

    @Test
    void backupCodesAreSavedConsumedCountedAndDeleted() {
        Long userId = newUser("mfa@identity.test");
        backupCodes.saveBatch(userId, List.of("c1", "c2", "c3"));

        assertThat(backupCodes.countAvailable(userId)).isEqualTo(3);
        assertThat(backupCodes.consumeCode(userId, "c2")).isTrue();
        assertThat(backupCodes.consumeCode(userId, "c2")).isFalse();
        assertThat(backupCodes.countAvailable(userId)).isEqualTo(2);
        List<MfaBackupCode> all = backupCodes.listByUser(userId);
        assertThat(all).extracting(MfaBackupCode::codeHash).containsExactly("c1", "c2", "c3");
        assertThat(all.get(1).used()).isTrue();
        assertThat(all.get(1).usedAt()).isNotNull();

        backupCodes.deleteByUser(userId);
        assertThat(backupCodes.listByUser(userId)).isEmpty();
        assertThat(backupCodes.countAvailable(userId)).isZero();
    }

    @Test
    void verificationCodesFollowTheirLifecycle() {
        Long userId = newUser("codigos@identity.test");
        Instant expires = Instant.now().plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        Long first = codigos.create(new VerificationCode(
                null, UUID.randomUUID().toString(), userId, "RECUPERACION_PASSWORD", "hash-1", expires, 0, 3, false,
                null));
        Long second = codigos.create(new VerificationCode(
                null, UUID.randomUUID().toString(), userId, "RECUPERACION_PASSWORD", "hash-2", expires, 0, 3, false,
                null));

        VerificationCode byId = codigos.findById(first).orElseThrow();
        assertThat(byId.codeHash()).isEqualTo("hash-1");
        assertThat(byId.expirationDate()).isEqualTo(expires);
        assertThat(codigos.findById(-1L)).isEmpty();
        assertThat(codigos.findLatestPendingByUserAndType(userId, "RECUPERACION_PASSWORD"))
                .map(VerificationCode::id).contains(second);

        codigos.incrementAttempts(second);
        assertThat(codigos.findById(second).orElseThrow().failedAttempts()).isEqualTo(1);
        codigos.markAsUsed(second);
        assertThat(codigos.findLatestPendingByUserAndType(userId, "RECUPERACION_PASSWORD"))
                .map(VerificationCode::id).contains(first);
        codigos.invalidatePreviousCodes(userId, "RECUPERACION_PASSWORD");
        assertThat(codigos.findLatestPendingByUserAndType(userId, "RECUPERACION_PASSWORD")).isEmpty();
        assertThat(owner.queryForObject(
                "SELECT COUNT(*) FROM CODIGO_VERIFICACION WHERE USUARIO_ID = ? AND USADO = 1", Integer.class, userId))
                .isEqualTo(2);
    }
}
