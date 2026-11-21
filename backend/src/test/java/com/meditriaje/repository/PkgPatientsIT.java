package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.PacientePerfilResponse;
import com.meditriaje.dto.affiliation.RepresentacionLegalResponse;
import com.meditriaje.dto.allergy.AlergiaResponse;
import com.meditriaje.exception.ConflictoOperacionException;
import com.meditriaje.model.Alergia;
import com.meditriaje.model.IdentidadProvisional;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.RepresentacionLegal;
import com.meditriaje.repository.plsql.OracleErrorTranslator;
import com.meditriaje.repository.plsql.PackageGateway;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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

/** PKG_PATIENTS contra Oracle Free real, llamado como el usuario de aplicación (MEDITRIAJE_APP). */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PkgPatientsIT {

    private static final String OWNER = "MT_PKG_PATIENTS";

    private static OracleContainer oracle;
    private static JdbcTemplate owner;
    private static PacienteRepository pacientes;
    private static ConsentimientoRepository consentimientos;
    private static RepresentacionLegalRepository representaciones;
    private static IdentidadProvisionalRepository identidades;
    private static AlergiaRepository alergias;

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
        pacientes = new PacienteRepository(gateway);
        consentimientos = new ConsentimientoRepository(gateway);
        representaciones = new RepresentacionLegalRepository(gateway);
        identidades = new IdentidadProvisionalRepository(gateway);
        alergias = new AlergiaRepository(gateway);
    }

    @AfterAll
    static void stop() {
        if (oracle != null) {
            oracle.stop();
        }
    }

    /** Crea un usuario (preparación con el propietario) y devuelve su ID y PUBLIC_ID. */
    private static Object[] newUser() {
        String publicId = UUID.randomUUID().toString();
        owner.update("INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH) VALUES (?, ?, 'x')",
                publicId, publicId.substring(0, 8) + "@test.co");
        Long id = owner.queryForObject("SELECT ID FROM USUARIO WHERE PUBLIC_ID = ?", Long.class, publicId);
        return new Object[] {id, publicId};
    }

    private static Paciente newPatient(String document) {
        Object[] user = newUser();
        String publicId = UUID.randomUUID().toString();
        Long id = pacientes.crear((Long) user[0], publicId, "CC", document, "Ana", "Diaz",
                LocalDate.of(1990, 5, 17), "3000000000");
        return pacientes.buscarPorId(id).orElseThrow();
    }

    @Test
    void patientRoutinesRoundTrip() {
        Object[] user = newUser();
        String publicId = UUID.randomUUID().toString();
        Long id = pacientes.crear((Long) user[0], publicId, "CC", "900100", "Ana", "Diaz",
                LocalDate.of(1990, 5, 17), null);

        Paciente byId = pacientes.buscarPorId(id).orElseThrow();
        assertThat(byId.publicId()).isEqualTo(publicId);
        assertThat(byId.fechaNacimiento()).isEqualTo(LocalDate.of(1990, 5, 17));
        assertThat(byId.createdAt()).isNotNull();
        assertThat(pacientes.buscarPorUsuarioId((Long) user[0])).contains(byId);
        assertThat(pacientes.buscarPorPublicId(publicId)).contains(byId);
        assertThat(pacientes.buscarPorDocumento("CC", "900100")).contains(byId);
        assertThat(pacientes.existePorDocumento("CC", "900100")).isTrue();
        assertThat(pacientes.existePorDocumento("CC", "no-existe")).isFalse();
        assertThat(pacientes.buscarPorPublicId("no-existe")).isEmpty();

        PacientePerfilResponse perfil = pacientes.buscarPerfilPorUsuarioPublicId((String) user[1]).orElseThrow();
        assertThat(perfil.publicId()).isEqualTo(publicId);
        assertThat(perfil.email()).endsWith("@test.co");
    }

    @Test
    void duplicatedDocumentIsAConflict() {
        newPatient("900200");

        assertThatThrownBy(() -> newPatient("900200")).isInstanceOf(ConflictoOperacionException.class);
    }

    @Test
    void consentIsRegistered() {
        Object[] user = newUser();

        Long id = consentimientos.registrar((Long) user[0], "v1.0", true, "127.0.0.1");

        assertThat(id).isPositive();
        assertThat(owner.queryForObject("SELECT REVOCADO FROM CONSENTIMIENTO WHERE ID = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void legalRepresentationIsStoredAndListed() {
        Paciente menor = newPatient("900301");
        Paciente representante = newPatient("900302");

        RepresentacionLegal saved = representaciones.guardar(new RepresentacionLegal(null,
                UUID.randomUUID().toString(), menor.id(), representante.id(), "MADRE", null, true, null));
        List<RepresentacionLegalResponse> list = representaciones.listarPorRepresentanteId(representante.id());

        assertThat(saved.id()).isNotNull();
        assertThat(saved.verificado()).isTrue();
        assertThat(representaciones.buscarPorId(saved.id())).isPresent();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).menorNombre()).isEqualTo("Ana Diaz");
    }

    @Test
    void provisionalIdentityIsCreatedFoundAndReconciled() {
        Paciente paciente = newPatient("900400");
        String episodePublicId = UUID.randomUUID().toString();
        owner.update("INSERT INTO EPISODIO_ATENCION (PUBLIC_ID, TIPO, SEDE_ID) "
                + "VALUES (?, 'URGENCIA', (SELECT MIN(ID) FROM SEDE))", episodePublicId);
        Long episodeId = owner.queryForObject(
                "SELECT ID FROM EPISODIO_ATENCION WHERE PUBLIC_ID = ?", Long.class, episodePublicId);

        IdentidadProvisional created = identidades.guardar(new IdentidadProvisional(null,
                UUID.randomUUID().toString(), episodeId, "NN-IT-1", "Adulto", 40, "MASCULINO", "Consciente",
                "PROVISIONAL", null, null, null, null));

        assertThat(created.id()).isNotNull();
        IdentidadProvisional byEpisode = identidades.buscarPorEpisodioId(episodeId).orElseThrow();
        assertThat(byEpisode.edadAparente()).isEqualTo(40);
        assertThat(byEpisode.vinculadoPacienteId()).isNull();
        assertThat(byEpisode.confirmadoPorUsuarioId()).isNull();
        assertThat(identidades.buscarPorCodigo("NN-IT-1")).isPresent();
        assertThat(identidades.buscarPorCodigo("NN-NO")).isEmpty();
        assertThat(identidades.reconciliarIdentidad(created.id(), paciente.id(), paciente.usuarioId(), Instant.now()))
                .isEqualTo(1);
        assertThat(identidades.reconciliarIdentidad(created.id(), paciente.id(), paciente.usuarioId(), null))
                .isZero();
        IdentidadProvisional reconciled = identidades.buscarPorCodigo("NN-IT-1").orElseThrow();
        assertThat(reconciled.estado()).isEqualTo("VINCULADA");
        assertThat(reconciled.vinculadoPacienteId()).isEqualTo(paciente.id());
        assertThat(reconciled.confirmadoPorUsuarioId()).isEqualTo(paciente.usuarioId());
    }

    @Test
    void allergyRoutinesRoundTrip() {
        Paciente paciente = newPatient("900500");

        Long id = alergias.crear(new Alergia(null, null, paciente.id(), " Penicilina ", "Anafilaxia", "GRAVE",
                "ACTIVA", "PROFESIONAL", paciente.usuarioId(), null, null, null, null, null, null));
        Alergia entity = alergias.listarPorPacienteId(paciente.id()).get(0);

        assertThat(entity.id()).isEqualTo(id);
        assertThat(entity.sustancia()).isEqualTo("Penicilina");
        assertThat(entity.registradaPorUsuarioId()).isEqualTo(paciente.usuarioId());
        assertThat(entity.atencionId()).isNull();
        assertThat(alergias.existeActivaPorSustancia(paciente.id(), "  penicilina")).isTrue();
        assertThat(alergias.buscarEntidadPorPublicId(entity.publicId())).isPresent();
        AlergiaResponse view = alergias.buscarPorPublicId(entity.publicId()).orElseThrow();
        assertThat(view.pacientePublicId()).isEqualTo(paciente.publicId());
        assertThatThrownBy(() -> alergias.crear(
                        new Alergia(null, paciente.id(), "PENICILINA", "Shock", "GRAVE", null)))
                .isInstanceOf(ConflictoOperacionException.class);

        assertThat(alergias.inactivar(id, paciente.usuarioId(), " Descartada ", null)).isEqualTo(1);
        assertThat(alergias.inactivar(id, paciente.usuarioId(), "Otra vez", null)).isZero();
        assertThat(alergias.listarPorPacienteId(paciente.id())).isEmpty();
        assertThat(alergias.listarPorPacienteId(paciente.id(), false)).hasSize(1);
        assertThat(alergias.listarPorPacientePublicId(paciente.publicId(), false)).isEmpty();
        List<AlergiaResponse> all = alergias.listarPorPacientePublicId(paciente.publicId(), true);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).estado()).isEqualTo("INACTIVA");
        assertThat(all.get(0).motivoInactivacion()).isEqualTo("Descartada");
    }
}
