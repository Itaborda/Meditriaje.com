package com.meditriaje.demo;

import com.meditriaje.repository.CitaRepository;
import com.meditriaje.repository.DemoSeedRepository;
import com.meditriaje.repository.DisponibilidadSlotRepository;
import com.meditriaje.repository.EspecialidadRepository;
import com.meditriaje.repository.MedicamentoRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.service.AdminCatalogService;
import com.meditriaje.service.AdminProfessionalService;
import com.meditriaje.service.AllergyService;
import com.meditriaje.service.AppointmentService;
import com.meditriaje.service.AuthService;
import com.meditriaje.service.ClinicalAttentionService;
import com.meditriaje.service.DispensationService;
import com.meditriaje.service.FollowUpService;
import com.meditriaje.service.PrescriptionService;
import com.meditriaje.service.SlotGeneratorService;
import com.meditriaje.service.TriajeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    @Mock
    private Environment environment;

    @Mock
    private DemoSeedRepository demoSeedRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private ProfesionalRepository profesionalRepository;

    @Mock
    private EspecialidadRepository especialidadRepository;

    @Mock
    private DisponibilidadSlotRepository slotRepository;

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private MedicamentoRepository medicamentoRepository;

    @Mock
    private AdminCatalogService adminCatalogService;

    @Mock
    private AdminProfessionalService adminProfessionalService;

    @Mock
    private AuthService authService;

    @Mock
    private SlotGeneratorService slotGeneratorService;

    @Mock
    private TriajeService triajeService;

    @Mock
    private AppointmentService appointmentService;

    @Mock
    private ClinicalAttentionService clinicalAttentionService;

    @Mock
    private PrescriptionService prescriptionService;

    @Mock
    private AllergyService allergyService;

    @Mock
    private FollowUpService followUpService;

    @Mock
    private DispensationService dispensationService;

    private DemoDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DemoDataSeeder(
                environment,
                demoSeedRepository,
                passwordEncoder,
                usuarioRepository,
                pacienteRepository,
                profesionalRepository,
                especialidadRepository,
                slotRepository,
                citaRepository,
                medicamentoRepository,
                adminCatalogService,
                adminProfessionalService,
                authService,
                slotGeneratorService,
                triajeService,
                appointmentService,
                clinicalAttentionService,
                prescriptionService,
                allergyService,
                followUpService,
                dispensationService
        );
    }

    @Test
    @DisplayName("Cuando DEMO_SEED es falso o no esta configurado, no ejecuta ninguna consulta ni siembra")
    void inactivoPorDefecto() {
        when(environment.getProperty("DEMO_SEED")).thenReturn("false");

        assertThatCode(() -> seeder.run()).doesNotThrowAnyException();

        verifyNoInteractions(demoSeedRepository);
    }

    @Test
    @DisplayName("Cuando DEMO_SEED=true pero DEMO_PASSWORD esta vacio o tiene menos de 12 caracteres, lanza excepcion")
    void fallaSiPasswordDemoInvalida() {
        when(environment.getProperty("DEMO_SEED")).thenReturn("true");
        when(environment.getProperty("DEMO_PASSWORD")).thenReturn("corta123");

        assertThatThrownBy(() -> seeder.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_PASSWORD");

        verifyNoInteractions(demoSeedRepository);
    }

    @Test
    @DisplayName("Cuando la base de datos contiene usuarios ajenos a @demo.meditriaje.test, aborta tajantemente por seguridad")
    void abortaSiHayUsuariosNoDemo() {
        when(environment.getProperty("DEMO_SEED")).thenReturn("true");
        when(environment.getProperty("DEMO_PASSWORD")).thenReturn("PasswordSeguro2026!");
        when(environment.getProperty("DEMO_RESET")).thenReturn("false");

        when(demoSeedRepository.contarUsuariosFueraDeDominio("@demo.meditriaje.test")).thenReturn(2);

        assertThatThrownBy(() -> seeder.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ABORTADO POR SEGURIDAD")
                .hasMessageContaining("no pertenecen al dominio reservado");
    }

    @Test
    @DisplayName("Cuando los usuarios demo ya fueron sembrados (>=17), es idempotente y no vuelve a sembrar")
    void idempotenteSiYaSembrado() {
        when(environment.getProperty("DEMO_SEED")).thenReturn("true");
        when(environment.getProperty("DEMO_PASSWORD")).thenReturn("PasswordSeguro2026!");
        when(environment.getProperty("DEMO_RESET")).thenReturn("false");

        when(demoSeedRepository.contarUsuariosFueraDeDominio("@demo.meditriaje.test")).thenReturn(0);
        when(demoSeedRepository.contarUsuariosEnDominio("@demo.meditriaje.test")).thenReturn(17);

        assertThatCode(() -> seeder.run()).doesNotThrowAnyException();

        verify(authService, never()).registrarPaciente(any(), anyString());
    }
}
