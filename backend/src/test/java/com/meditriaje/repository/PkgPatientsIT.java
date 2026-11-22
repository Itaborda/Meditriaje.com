package com.meditriaje.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meditriaje.db.OracleTestDatabase;
import com.meditriaje.dto.PatientProfileResponse;
import com.meditriaje.dto.affiliation.LegalRepresentationResponse;
import com.meditriaje.dto.allergy.AllergyResponse;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.model.Allergy;
import com.meditriaje.model.ProvisionalIdentity;
import com.meditriaje.model.Patient;
import com.meditriaje.model.LegalRepresentation;
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
    private static PatientRepository pacientes;
    private static ConsentRepository consentimientos;
    private static LegalRepresentationRepository representaciones;
    private static ProvisionalIdentityRepository identidades;
    private static AllergyRepository alergias;

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
        pacientes = new PatientRepository(gateway);
        consentimientos = new ConsentRepository(gateway);
        representaciones = new LegalRepresentationRepository(gateway);
        identidades = new ProvisionalIdentityRepository(gateway);
        alergias = new AllergyRepository(gateway);
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

    private static Patient newPatient(String document) {
        Object[] user = newUser();
        String publicId = UUID.randomUUID().toString();
        Long id = pacientes.create((Long) user[0], publicId, "CC", document, "Ana", "Diaz",
                LocalDate.of(1990, 5, 17), "3000000000");
        return pacientes.findById(id).orElseThrow();
    }

    @Test
    void patientRoutinesRoundTrip() {
        Object[] user = newUser();
        String publicId = UUID.randomUUID().toString();
        Long id = pacientes.create((Long) user[0], publicId, "CC", "900100", "Ana", "Diaz",
                LocalDate.of(1990, 5, 17), null);

        Patient byId = pacientes.findById(id).orElseThrow();
        assertThat(byId.publicId()).isEqualTo(publicId);
        assertThat(byId.birthDate()).isEqualTo(LocalDate.of(1990, 5, 17));
        assertThat(byId.createdAt()).isNotNull();
        assertThat(pacientes.findByUserId((Long) user[0])).contains(byId);
        assertThat(pacientes.findByPublicId(publicId)).contains(byId);
        assertThat(pacientes.findByDocument("CC", "900100")).contains(byId);
        assertThat(pacientes.existsByDocument("CC", "900100")).isTrue();
        assertThat(pacientes.existsByDocument("CC", "no-existe")).isFalse();
        assertThat(pacientes.findByPublicId("no-existe")).isEmpty();

        PatientProfileResponse perfil = pacientes.findProfileByUserPublicId((String) user[1]).orElseThrow();
        assertThat(perfil.publicId()).isEqualTo(publicId);
        assertThat(perfil.email()).endsWith("@test.co");

        // Paciente presencial sin cuenta MediTriaje (USUARIO_ID nulo) -> búsqueda y vinculación posterior
        String walkInPub = UUID.randomUUID().toString();
        Long walkInId = pacientes.create(null, walkInPub, "CC", "900300", "Carlos", "Rojas",
                LocalDate.of(1988, 3, 12), "3009990000");
        Patient walkIn = pacientes.findById(walkInId).orElseThrow();
        assertThat(walkIn.userId()).isNull();
        assertThat(pacientes.searchByDocument("900300", null)).contains(walkIn);

        Object[] linkedUser = newUser();
        assertThat(pacientes.linkUserToPatient(walkInId, (Long) linkedUser[0])).isEqualTo(1);
        assertThat(pacientes.findByUserId((Long) linkedUser[0])).isPresent();
    }

    @Test
    void duplicatedDocumentIsAConflict() {
        newPatient("900200");

        assertThatThrownBy(() -> newPatient("900200")).isInstanceOf(OperationConflictException.class);
    }

    @Test
    void consentIsRegistered() {
        Object[] user = newUser();

        Long id = consentimientos.record((Long) user[0], "v1.0", true, "127.0.0.1");

        assertThat(id).isPositive();
        assertThat(owner.queryForObject("SELECT REVOCADO FROM CONSENTIMIENTO WHERE ID = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void legalRepresentationIsStoredAndListed() {
        Patient menor = newPatient("900301");
        Patient representante = newPatient("900302");

        LegalRepresentation saved = representaciones.save(new LegalRepresentation(null,
                UUID.randomUUID().toString(), menor.id(), representante.id(), "MADRE", null, true, null));
        List<LegalRepresentationResponse> list = representaciones.listByRepresentativeId(representante.id());

        assertThat(saved.id()).isNotNull();
        assertThat(saved.verified()).isTrue();
        assertThat(representaciones.findById(saved.id())).isPresent();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).minorName()).isEqualTo("Ana Diaz");
    }

    @Test
    void provisionalIdentityIsCreatedFoundAndReconciled() {
        Patient paciente = newPatient("900400");
        String episodePublicId = UUID.randomUUID().toString();
        owner.update("INSERT INTO EPISODIO_ATENCION (PUBLIC_ID, TIPO) VALUES (?, 'URGENCIA')", episodePublicId);
        Long episodeId = owner.queryForObject(
                "SELECT ID FROM EPISODIO_ATENCION WHERE PUBLIC_ID = ?", Long.class, episodePublicId);

        ProvisionalIdentity created = identidades.save(new ProvisionalIdentity(null,
                UUID.randomUUID().toString(), episodeId, "NN-IT-1", "Adulto", 40, "MASCULINO", "Consciente",
                "PROVISIONAL", null, null, null, null));

        assertThat(created.id()).isNotNull();
        ProvisionalIdentity byEpisode = identidades.findByEpisodeId(episodeId).orElseThrow();
        assertThat(byEpisode.apparentAge()).isEqualTo(40);
        assertThat(byEpisode.linkedPatientId()).isNull();
        assertThat(byEpisode.confirmedByUserId()).isNull();
        assertThat(identidades.findByCode("NN-IT-1")).isPresent();
        assertThat(identidades.findByCode("NN-NO")).isEmpty();
        assertThat(identidades.reconcileIdentity(created.id(), paciente.id(), paciente.userId(), Instant.now()))
                .isEqualTo(1);
        assertThat(identidades.reconcileIdentity(created.id(), paciente.id(), paciente.userId(), null))
                .isZero();
        ProvisionalIdentity reconciled = identidades.findByCode("NN-IT-1").orElseThrow();
        assertThat(reconciled.status()).isEqualTo("VINCULADA");
        assertThat(reconciled.linkedPatientId()).isEqualTo(paciente.id());
        assertThat(reconciled.confirmedByUserId()).isEqualTo(paciente.userId());
    }

    @Test
    void allergyRoutinesRoundTrip() {
        Patient paciente = newPatient("900500");

        Long id = alergias.create(new Allergy(null, null, paciente.id(), " Penicilina ", "Anafilaxia", "GRAVE",
                "ACTIVA", "PROFESIONAL", paciente.userId(), null, null, null, null, null, null));
        Allergy entity = alergias.listByPatientId(paciente.id()).get(0);

        assertThat(entity.id()).isEqualTo(id);
        assertThat(entity.substance()).isEqualTo("Penicilina");
        assertThat(entity.registeredByUserId()).isEqualTo(paciente.userId());
        assertThat(entity.attentionId()).isNull();
        assertThat(alergias.existsActiveBySubstance(paciente.id(), "  penicilina")).isTrue();
        assertThat(alergias.findEntityByPublicId(entity.publicId())).isPresent();
        AllergyResponse view = alergias.findByPublicId(entity.publicId()).orElseThrow();
        assertThat(view.patientPublicId()).isEqualTo(paciente.publicId());
        assertThatThrownBy(() -> alergias.create(
                        new Allergy(null, paciente.id(), "PENICILINA", "Shock", "GRAVE", null)))
                .isInstanceOf(OperationConflictException.class);

        assertThat(alergias.inactivate(id, paciente.userId(), " Descartada ", null)).isEqualTo(1);
        assertThat(alergias.inactivate(id, paciente.userId(), "Otra vez", null)).isZero();
        assertThat(alergias.listByPatientId(paciente.id())).isEmpty();
        assertThat(alergias.listByPatientId(paciente.id(), false)).hasSize(1);
        assertThat(alergias.listByPatientPublicId(paciente.publicId(), false)).isEmpty();
        List<AllergyResponse> all = alergias.listByPatientPublicId(paciente.publicId(), true);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).status()).isEqualTo("INACTIVA");
        assertThat(all.get(0).inactivationReason()).isEqualTo("Descartada");
    }
}
