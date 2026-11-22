package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.admin.ProfessionalResponse;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.model.CareAssignment;
import com.meditriaje.model.Specialty;
import com.meditriaje.model.Professional;
import com.meditriaje.repository.plsql.OracleErrorTranslator;
import com.meditriaje.repository.plsql.PackageGateway;
import java.time.Duration;
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
 * PKG_STAFF contra Oracle Free real, llamado como MEDITRIAJE_APP a través de los repositorios migrados
 * (profesionales, especialidades y asignaciones asistenciales).
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PkgStaffIT {

    private static final String OWNER = "MT_PKG_STAFF";

    private static OracleContainer oracle;
    private static JdbcTemplate owner;
    private static UserRepository usuarios;
    private static ProfessionalRepository profesionales;
    private static SpecialtyRepository especialidades;
    private static CareAssignmentRepository asignaciones;

    @BeforeAll
    static void migrate() {
        oracle = OracleTestDatabase.newContainer().withStartupTimeout(Duration.ofMinutes(3));
        oracle.start();
        DataSource ownerDs = OracleTestDatabase.provisionOwner(oracle, OWNER);
        OracleTestDatabase.flyway(ownerDs).migrate();
        owner = new JdbcTemplate(ownerDs);
        DataSource appDs = OracleTestDatabase.dataSource(
                oracle, OracleTestDatabase.APP_USER, OracleTestDatabase.APP_PASSWORD);
        PackageGateway gateway = new PackageGateway(appDs, OWNER, new OracleErrorTranslator());
        usuarios = new UserRepository(gateway);
        profesionales = new ProfessionalRepository(gateway);
        especialidades = new SpecialtyRepository(gateway);
        asignaciones = new CareAssignmentRepository(gateway);
    }

    @AfterAll
    static void stop() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    private static Long newUser(String email) {
        return usuarios.create(UUID.randomUUID().toString(), email, "hash", false);
    }

    private static Long newEpisode() {
        String publicId = UUID.randomUUID().toString();
        owner.update("INSERT INTO EPISODIO_ATENCION (PUBLIC_ID, TIPO) VALUES (?, 'URGENCIA')",
                publicId);
        return owner.queryForObject("SELECT ID FROM EPISODIO_ATENCION WHERE PUBLIC_ID = ?", Long.class, publicId);
    }

    @Test
    void specialtiesAreCreatedUpdatedFilteredAndPaged() {
        Specialty created = especialidades.create("esp-staff-1", "Zeta Staff Uno", 30);
        especialidades.create("esp-staff-2", "Zeta Staff Dos", 20);
        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo("ACTIVO");

        Specialty byId = especialidades.findById(created.id()).orElseThrow();
        assertThat(byId.name()).isEqualTo("Zeta Staff Uno");
        assertThat(byId.slotDurationMinutes()).isEqualTo(30);
        assertThat(especialidades.findByPublicId("no-existe")).isEmpty();

        assertThat(especialidades.existsByName("  zeta staff uno ")).isTrue();
        assertThat(especialidades.existsByNameAndNotPublicId("Zeta Staff Uno", "esp-staff-1")).isFalse();
        assertThat(especialidades.existsByNameAndNotPublicId("Zeta Staff Uno", "esp-staff-2")).isTrue();

        especialidades.update("esp-staff-1", "Zeta Staff Uno Editada", 45);
        assertThat(especialidades.findByPublicId("esp-staff-1").orElseThrow().slotDurationMinutes()).isEqualTo(45);

        long activasAntes = especialidades.count("activo");
        long todas = especialidades.count(null);
        especialidades.changeStatus("esp-staff-1", "INACTIVO");
        especialidades.changeStatus("esp-staff-2", "INACTIVO");
        assertThat(especialidades.count("ACTIVO")).isEqualTo(activasAntes - 2);
        assertThat(especialidades.count(" ")).isEqualTo(todas);

        // Orden por nombre e ID, con desplazamiento page * size.
        assertThat(especialidades.list(0, 10, "inactivo")).extracting(Specialty::publicId)
                .containsExactly("esp-staff-2", "esp-staff-1");
        assertThat(especialidades.list(1, 1, "INACTIVO")).extracting(Specialty::publicId)
                .containsExactly("esp-staff-1");
        assertThat(especialidades.list(0, 1000, null)).hasSize((int) todas);
    }

    @Test
    void professionalsAreCreatedFoundValidatedAndListed() {
        Specialty esp = especialidades.create("esp-prof", "Zeta Staff Profesionales", 20);
        Long u1 = newUser("prof1@staff.test");
        Long u2 = newUser("prof2@staff.test");

        Long id1 = profesionales.create(new Professional(
                u1, "prof-1", esp.id(), null, "1001", "RM-ST-1", "Bruno", "Zapata", "3001"));
        profesionales.create(new Professional(
                u2, "prof-2", esp.id(), "CE", "2002", "RM-ST-2", "Ana", "Zapata", null));

        Professional p1 = profesionales.findByPublicId("prof-1").orElseThrow();
        assertThat(p1.id()).isEqualTo(id1);
        assertThat(p1.documentType()).isEqualTo("CC");
        assertThat(p1.createdAt()).isNotNull();
        assertThat(profesionales.findById(id1)).map(Professional::publicId).contains("prof-1");
        assertThat(profesionales.findByUserId(u2)).map(Professional::medicalLicense).contains("RM-ST-2");
        assertThat(profesionales.findById(-1L)).isEmpty();

        assertThat(profesionales.existsByMedicalLicense(" rm-st-1 ")).isTrue();
        assertThat(profesionales.existsByMedicalLicenseAndNotPublicId("RM-ST-1", "prof-1")).isFalse();
        assertThat(profesionales.existsByMedicalLicenseAndNotPublicId("RM-ST-1", "prof-2")).isTrue();
        assertThat(profesionales.existsByDocument("cc", "1001")).isTrue();
        assertThat(profesionales.existsByDocumentAndNotPublicId("CC", "1001", "prof-1")).isFalse();
        assertThat(profesionales.existsByDocumentAndNotPublicId("CC", "1001", "prof-2")).isTrue();

        profesionales.update(new Professional(
                null, u1, "prof-1", esp.id(), "CC", "1001", "RM-ST-1", "Bruno", "Zapata Ruiz", "3999", null, null));
        Professional updated = profesionales.findByPublicId("prof-1").orElseThrow();
        assertThat(updated.lastNames()).isEqualTo("Zapata Ruiz");
        assertThat(updated.phone()).isEqualTo("3999");
        assertThat(updated.updatedAt()).isNotNull();

        // Orden por apellidos, nombres e ID.
        List<ProfessionalResponse> page = profesionales.list(0, 10, "esp-prof", "activo");
        assertThat(page).extracting(ProfessionalResponse::publicId).containsExactly("prof-2", "prof-1");
        assertThat(page.get(0).email()).isEqualTo("prof2@staff.test");
        assertThat(page.get(0).specialtyName()).isEqualTo("Zeta Staff Profesionales");
        assertThat(profesionales.list(1, 1, " esp-prof ", null)).extracting(ProfessionalResponse::publicId)
                .containsExactly("prof-1");
        assertThat(profesionales.count("esp-prof", null)).isEqualTo(2);
        assertThat(profesionales.count("esp-prof", "INACTIVO")).isZero();
        assertThat(profesionales.count(null, null)).isGreaterThanOrEqualTo(2);

        assertThatThrownBy(() -> profesionales.create(new Professional(
                newUser("prof3@staff.test"), "prof-3", esp.id(), "CC", "3003", "RM-ST-1", "Otro", "Duplicado", null)))
                .isInstanceOf(OperationConflictException.class);
    }

    @Test
    void assignmentsAreCreatedDeactivatedAndListedByStartDate() {
        Specialty esp = especialidades.create("esp-asig", "Zeta Staff Asignaciones", 20);
        Long userId = newUser("asig@staff.test");
        Long profId = profesionales.create(new Professional(
                userId, "prof-asig", esp.id(), "CC", "4004", "RM-ST-A", "Carla", "Mora", null));
        Long episodeId = newEpisode();
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);

        CareAssignment first = asignaciones.save(new CareAssignment(
                null, "asig-1", episodeId, profId, "MEDICO_TRATANTE", userId, start, null, true, null));
        assertThat(first.id()).isNotNull();
        assertThat(asignaciones.hasActiveAssignment(profId, episodeId)).isTrue();

        assertThat(asignaciones.inactivatePreviousAssignmentByRoleFunction(episodeId, "MEDICO_TRATANTE")).isEqualTo(1);
        assertThat(asignaciones.inactivatePreviousAssignmentByRoleFunction(episodeId, "MEDICO_TRATANTE")).isZero();
        assertThat(asignaciones.hasActiveAssignment(profId, episodeId)).isFalse();

        asignaciones.save(new CareAssignment(
                null, "asig-2", episodeId, profId, "INTERCONSULTOR", userId, start.plusSeconds(60), null, true, null));

        List<CareAssignment> list = asignaciones.listByEpisodeId(episodeId);
        assertThat(list).extracting(CareAssignment::publicId).containsExactly("asig-1", "asig-2");
        assertThat(list.get(0).active()).isFalse();
        assertThat(list.get(0).endDate()).isNotNull();
        assertThat(list.get(0).startDate()).isEqualTo(start);
        assertThat(list.get(1).active()).isTrue();
        assertThat(list.get(1).createdAt()).isNotNull();
        assertThat(asignaciones.listByEpisodeId(-1L)).isEmpty();
    }
}
