package com.meditriaje.service;

import com.meditriaje.dto.PatientProfileResponse;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    private PatientService pacienteService;

    @BeforeEach
    void setUp() {
        pacienteService = new PatientService(patientRepository);
    }

    @Test
    void getMyProfile_patientExists_returnsProfile() {
        String usuarioPublicId = "usr-pub-123";
        PatientProfileResponse perfilEsperado = new PatientProfileResponse(
                "pac-pub-456",
                "CC",
                "1020304050",
                "Carlos",
                "Perez",
                LocalDate.of(1995, 5, 20),
                "3001234567",
                "carlos.perez@example.com"
        );

        when(patientRepository.findProfileByUserPublicId(usuarioPublicId))
                .thenReturn(Optional.of(perfilEsperado));

        PatientProfileResponse resultado = pacienteService.getMyProfile(usuarioPublicId);

        assertThat(resultado).isNotNull();
        assertThat(resultado.publicId()).isEqualTo("pac-pub-456");
        assertThat(resultado.firstNames()).isEqualTo("Carlos");
        assertThat(resultado.email()).isEqualTo("carlos.perez@example.com");
    }

    @Test
    void getMyProfile_patientNotExists_throwsResourceNotFound() {
        String usuarioPublicId = "usr-inexistente";
        when(patientRepository.findProfileByUserPublicId(usuarioPublicId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> pacienteService.getMyProfile(usuarioPublicId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("recurso solicitado no fue encontrado");
    }

    @Test
    void getMyProfile_idInvalid_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> pacienteService.getMyProfile(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> pacienteService.getMyProfile("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
