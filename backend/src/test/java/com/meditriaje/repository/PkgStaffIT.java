package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.admin.ProfesionalResponse;
import com.meditriaje.exception.ConflictoOperacionException;
import com.meditriaje.model.AsignacionAsistencial;
import com.meditriaje.model.Especialidad;
import com.meditriaje.model.Profesional;
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
    private static UsuarioRepository usuarios;
    private static ProfesionalRepository profesionales;
    private static EspecialidadRepository especialidades;
    private static AsignacionAsistencialRepository asignaciones;

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
        usuarios = new UsuarioRepository(gateway);
        profesionales = new ProfesionalRepository(gateway);
        especialidades = new EspecialidadRepository(gateway);
        asignaciones = new AsignacionAsistencialRepository(gateway);
    }

    @AfterAll
    static void stop() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    private static Long newUser(String email) {
        return usuarios.crear(UUID.randomUUID().toString(), email, "hash", false);
    }

    private static Long newEpisode() {
        String publicId = UUID.randomUUID().toString();
        owner.update("INSERT INTO EPISODIO_ATENCION (PUBLIC_ID, TIPO) VALUES (?, 'URGENCIA')",
                publicId);
        return owner.queryForObject("SELECT ID FROM EPISODIO_ATENCION WHERE PUBLIC_ID = ?", Long.class, publicId);
    }

    @Test
    void specialtiesAreCreatedUpdatedFilteredAndPaged() {
        Especialidad created = especialidades.crear("esp-staff-1", "Zeta Staff Uno", 30);
        especialidades.crear("esp-staff-2", "Zeta Staff Dos", 20);
        assertThat(created.id()).isNotNull();
        assertThat(created.estado()).isEqualTo("ACTIVO");

        Especialidad byId = especialidades.buscarPorId(created.id()).orElseThrow();
        assertThat(byId.nombre()).isEqualTo("Zeta Staff Uno");
        assertThat(byId.duracionSlotMin()).isEqualTo(30);
        assertThat(especialidades.buscarPorPublicId("no-existe")).isEmpty();

        assertThat(especialidades.existePorNombre("  zeta staff uno ")).isTrue();
        assertThat(especialidades.existePorNombreYNoPublicId("Zeta Staff Uno", "esp-staff-1")).isFalse();
        assertThat(especialidades.existePorNombreYNoPublicId("Zeta Staff Uno", "esp-staff-2")).isTrue();

        especialidades.actualizar("esp-staff-1", "Zeta Staff Uno Editada", 45);
        assertThat(especialidades.buscarPorPublicId("esp-staff-1").orElseThrow().duracionSlotMin()).isEqualTo(45);

        long activasAntes = especialidades.contar("activo");
        long todas = especialidades.contar(null);
        especialidades.cambiarEstado("esp-staff-1", "INACTIVO");
        especialidades.cambiarEstado("esp-staff-2", "INACTIVO");
        assertThat(especialidades.contar("ACTIVO")).isEqualTo(activasAntes - 2);
        assertThat(especialidades.contar(" ")).isEqualTo(todas);

        // Orden por nombre e ID, con desplazamiento page * size.
        assertThat(especialidades.listar(0, 10, "inactivo")).extracting(Especialidad::publicId)
                .containsExactly("esp-staff-2", "esp-staff-1");
        assertThat(especialidades.listar(1, 1, "INACTIVO")).extracting(Especialidad::publicId)
                .containsExactly("esp-staff-1");
        assertThat(especialidades.listar(0, 1000, null)).hasSize((int) todas);
    }

    @Test
    void professionalsAreCreatedFoundValidatedAndListed() {
        Especialidad esp = especialidades.crear("esp-prof", "Zeta Staff Profesionales", 20);
        Long u1 = newUser("prof1@staff.test");
        Long u2 = newUser("prof2@staff.test");

        Long id1 = profesionales.crear(new Profesional(
                u1, "prof-1", esp.id(), null, "1001", "RM-ST-1", "Bruno", "Zapata", "3001"));
        profesionales.crear(new Profesional(
                u2, "prof-2", esp.id(), "CE", "2002", "RM-ST-2", "Ana", "Zapata", null));

        Profesional p1 = profesionales.buscarPorPublicId("prof-1").orElseThrow();
        assertThat(p1.id()).isEqualTo(id1);
        assertThat(p1.tipoDocumento()).isEqualTo("CC");
        assertThat(p1.createdAt()).isNotNull();
        assertThat(profesionales.buscarPorId(id1)).map(Profesional::publicId).contains("prof-1");
        assertThat(profesionales.buscarPorUsuarioId(u2)).map(Profesional::registroMedico).contains("RM-ST-2");
        assertThat(profesionales.buscarPorId(-1L)).isEmpty();

        assertThat(profesionales.existePorRegistroMedico(" rm-st-1 ")).isTrue();
        assertThat(profesionales.existePorRegistroMedicoYNoPublicId("RM-ST-1", "prof-1")).isFalse();
        assertThat(profesionales.existePorRegistroMedicoYNoPublicId("RM-ST-1", "prof-2")).isTrue();
        assertThat(profesionales.existePorDocumento("cc", "1001")).isTrue();
        assertThat(profesionales.existePorDocumentoYNoPublicId("CC", "1001", "prof-1")).isFalse();
        assertThat(profesionales.existePorDocumentoYNoPublicId("CC", "1001", "prof-2")).isTrue();

        profesionales.actualizar(new Profesional(
                null, u1, "prof-1", esp.id(), "CC", "1001", "RM-ST-1", "Bruno", "Zapata Ruiz", "3999", null, null));
        Profesional updated = profesionales.buscarPorPublicId("prof-1").orElseThrow();
        assertThat(updated.apellidos()).isEqualTo("Zapata Ruiz");
        assertThat(updated.telefono()).isEqualTo("3999");
        assertThat(updated.updatedAt()).isNotNull();

        // Orden por apellidos, nombres e ID.
        List<ProfesionalResponse> page = profesionales.listar(0, 10, "esp-prof", "activo");
        assertThat(page).extracting(ProfesionalResponse::publicId).containsExactly("prof-2", "prof-1");
        assertThat(page.get(0).email()).isEqualTo("prof2@staff.test");
        assertThat(page.get(0).especialidadNombre()).isEqualTo("Zeta Staff Profesionales");
        assertThat(profesionales.listar(1, 1, " esp-prof ", null)).extracting(ProfesionalResponse::publicId)
                .containsExactly("prof-1");
        assertThat(profesionales.contar("esp-prof", null)).isEqualTo(2);
        assertThat(profesionales.contar("esp-prof", "INACTIVO")).isZero();
        assertThat(profesionales.contar(null, null)).isGreaterThanOrEqualTo(2);

        assertThatThrownBy(() -> profesionales.crear(new Profesional(
                newUser("prof3@staff.test"), "prof-3", esp.id(), "CC", "3003", "RM-ST-1", "Otro", "Duplicado", null)))
                .isInstanceOf(ConflictoOperacionException.class);
    }

    @Test
    void assignmentsAreCreatedDeactivatedAndListedByStartDate() {
        Especialidad esp = especialidades.crear("esp-asig", "Zeta Staff Asignaciones", 20);
        Long userId = newUser("asig@staff.test");
        Long profId = profesionales.crear(new Profesional(
                userId, "prof-asig", esp.id(), "CC", "4004", "RM-ST-A", "Carla", "Mora", null));
        Long episodeId = newEpisode();
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);

        AsignacionAsistencial first = asignaciones.guardar(new AsignacionAsistencial(
                null, "asig-1", episodeId, profId, "MEDICO_TRATANTE", userId, start, null, true, null));
        assertThat(first.id()).isNotNull();
        assertThat(asignaciones.tieneAsignacionActiva(profId, episodeId)).isTrue();

        assertThat(asignaciones.inactivarAsignacionPreviaPorFuncion(episodeId, "MEDICO_TRATANTE")).isEqualTo(1);
        assertThat(asignaciones.inactivarAsignacionPreviaPorFuncion(episodeId, "MEDICO_TRATANTE")).isZero();
        assertThat(asignaciones.tieneAsignacionActiva(profId, episodeId)).isFalse();

        asignaciones.guardar(new AsignacionAsistencial(
                null, "asig-2", episodeId, profId, "INTERCONSULTOR", userId, start.plusSeconds(60), null, true, null));

        List<AsignacionAsistencial> list = asignaciones.listarPorEpisodioId(episodeId);
        assertThat(list).extracting(AsignacionAsistencial::publicId).containsExactly("asig-1", "asig-2");
        assertThat(list.get(0).activo()).isFalse();
        assertThat(list.get(0).fechaFin()).isNotNull();
        assertThat(list.get(0).fechaInicio()).isEqualTo(start);
        assertThat(list.get(1).activo()).isTrue();
        assertThat(list.get(1).creadoAt()).isNotNull();
        assertThat(asignaciones.listarPorEpisodioId(-1L)).isEmpty();
    }
}
