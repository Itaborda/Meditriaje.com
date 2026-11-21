package com.meditriaje.service;

import com.meditriaje.dto.PatientProfileResponse;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Servicio de gestión de información de pacientes y consultas de perfil (HU-09).
 */
@Service
public class PatientService {

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = Objects.requireNonNull(patientRepository, "PacienteRepository no puede ser nulo");
    }

    /**
     * Retorna el perfil del paciente autenticado a partir de su publicId de usuario.
     */
    @Transactional(readOnly = true)
    public PatientProfileResponse getMyProfile(String usuarioPublicId) {
        if (usuarioPublicId == null || usuarioPublicId.isBlank()) {
            throw new IllegalArgumentException("El ID publico de usuario no puede ser nulo ni vacio");
        }
        return patientRepository.findProfileByUserPublicId(usuarioPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado"));
    }
}
