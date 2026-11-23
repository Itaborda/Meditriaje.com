package com.meditriaje.service;

import com.meditriaje.dto.hospital.*;
import com.meditriaje.exception.InvalidDataException;
import com.meditriaje.exception.UnauthorizedAccessException;
import com.meditriaje.exception.OperationConflictException;
import com.meditriaje.exception.ResourceNotFoundException;
import com.meditriaje.model.*;
import com.meditriaje.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de Gestión Hospitalaria, Camas, Movimientos y Egresos (Fase H, ADR-024).
 * Gobierna asignaciones concurrentes, transferencias longitudinales, quirófanos y egresos.
 */
@Service
public class HospitalService {

    private static final Set<String> VALID_AREA_TYPES = Set.of(
            "URGENCIAS", "OBSERVACION", "HOSPITALIZACION_GENERAL", "UCI_ADULTOS",
            "UCI_PEDIATRICA", "QUIROFANO", "RECUPERACION", "SALA_PARTOS"
    );
    private static final Set<String> VALID_ROOM_TYPES = Set.of(
            "INDIVIDUAL", "COMPARTIDA", "AISLAMIENTO", "QUIROFANO", "SALA_COMUN"
    );

    private final HospitalAreaRepository areaRepository;
    private final HospitalRoomRepository roomRepository;
    private final HospitalBedRepository bedRepository;
    private final BedOccupancyRepository occupancyRepository;
    private final PatientMovementRepository movementRepository;
    private final HospitalProcedureRepository procedureRepository;
    private final HospitalDischargeRepository dischargeRepository;
    private final CareEpisodeRepository episodeRepository;
    private final SiteRepository siteRepository;
    private final UserRepository userRepository;
    private final ProfessionalRepository professionalRepository;
    private final AuditService auditService;
    private final Clock clock;

    public HospitalService(
            HospitalAreaRepository areaRepository,
            HospitalRoomRepository roomRepository,
            HospitalBedRepository bedRepository,
            BedOccupancyRepository occupancyRepository,
            PatientMovementRepository movementRepository,
            HospitalProcedureRepository procedureRepository,
            HospitalDischargeRepository dischargeRepository,
            CareEpisodeRepository episodeRepository,
            SiteRepository siteRepository,
            UserRepository userRepository,
            ProfessionalRepository professionalRepository,
            AuditService auditService,
            Clock clock
    ) {
        this.areaRepository = Objects.requireNonNull(areaRepository);
        this.roomRepository = Objects.requireNonNull(roomRepository);
        this.bedRepository = Objects.requireNonNull(bedRepository);
        this.occupancyRepository = Objects.requireNonNull(occupancyRepository);
        this.movementRepository = Objects.requireNonNull(movementRepository);
        this.procedureRepository = Objects.requireNonNull(procedureRepository);
        this.dischargeRepository = Objects.requireNonNull(dischargeRepository);
        this.episodeRepository = Objects.requireNonNull(episodeRepository);
        this.siteRepository = Objects.requireNonNull(siteRepository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.professionalRepository = Objects.requireNonNull(professionalRepository);
        this.auditService = Objects.requireNonNull(auditService);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Asigna una cama disponible a un episodio hospitalario o de urgencias (H01, H02).
     */
    @Transactional
    public BedDetailResponse assignBed(
            String episodePublicId,
            AssignBedRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        CareEpisode episodio = episodeRepository.findByPublicId(episodePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Episodio no encontrado: " + episodePublicId));

        if ("EGRESADO".equals(episodio.status()) || "CANCELADO".equals(episodio.status())) {
            throw new OperationConflictException("No es posible asignar cama a un episodio cerrado o egresado.");
        }

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado."));

        HospitalBed cama = bedRepository.findByPublicId(request.bedPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Cama no encontrada: " + request.bedPublicId()));

        if (!"DISPONIBLE".equals(cama.status())) {
            throw new OperationConflictException("La cama seleccionada no está disponible (Estado actual: " + cama.status() + ").");
        }

        HospitalRoom habDestino = roomRepository.findById(cama.roomId())
                .orElseThrow(() -> new IllegalStateException("Habitación de destino no encontrada"));

        HospitalArea areaDestino = areaRepository.findById(habDestino.areaId())
                .orElseThrow(() -> new IllegalStateException("Área de destino no encontrada"));

        if (!Objects.equals(areaDestino.siteId(), episodio.siteId())) {
            throw new OperationConflictException("La cama seleccionada pertenece a una sede diferente a la del episodio.");
        }

        // Reserva atómica compare-and-set: DISPONIBLE -> OCUPADA (O-03)
        boolean reservada = bedRepository.reserveBedIfAvailable(cama.id());
        if (!reservada) {
            throw new OperationConflictException("Conflicto de concurrencia: La cama seleccionada ya no está disponible.");
        }

        Instant ahora = Instant.now(clock);

        // Si el episodio ya tenía otra cama asignada, se finaliza la anterior y se traslada
        Optional<BedOccupancy> ocupacionPrevia = occupancyRepository.findActiveByEpisodeId(episodio.id());
        Long areaOrigenId = null;
        Long camaOrigenId = null;

        if (ocupacionPrevia.isPresent()) {
            BedOccupancy prev = ocupacionPrevia.get();
            occupancyRepository.finishOccupancy(prev.id(), ahora);
            bedRepository.updateStatus(prev.bedId(), "LIMPIEZA");
            camaOrigenId = prev.bedId();

            HospitalBed camaPrev = bedRepository.findById(prev.bedId()).orElse(null);
            if (camaPrev != null) {
                HospitalRoom habPrev = roomRepository.findById(camaPrev.roomId()).orElse(null);
                if (habPrev != null) {
                    areaOrigenId = habPrev.areaId();
                }
            }
        }

        // Crear nueva ocupación protegida por índice único condicional
        String ocupacionPubId = UUID.randomUUID().toString();
        BedOccupancy nuevaOcupacion = new BedOccupancy(
                null,
                ocupacionPubId,
                episodio.id(),
                cama.id(),
                ahora,
                null,
                "ACTIVA",
                usuario.id(),
                request.assignmentReason(),
                ahora
        );
        try {
            occupancyRepository.save(nuevaOcupacion);
        } catch (DataIntegrityViolationException | OperationConflictException ex) {
            // Revertir la reserva de la cama si la base de datos detecta ocupación concurrente
            bedRepository.updateStatus(cama.id(), "DISPONIBLE");
            throw new OperationConflictException("Conflicto de concurrencia: La cama o el episodio ya tienen una ocupación activa.");
        }

        // Registrar movimiento intrahospitalario
        String movPubId = UUID.randomUUID().toString();
        PatientMovement movimiento = new PatientMovement(
                null,
                movPubId,
                episodio.id(),
                areaOrigenId,
                habDestino.areaId(),
                camaOrigenId,
                cama.id(),
                ahora,
                request.assignmentReason() != null ? request.assignmentReason() : "Asignación de cama inicial",
                usuario.id(),
                ahora
        );
        movementRepository.save(movimiento);

        // Actualizar estado del episodio si aplica
        if ("REGISTRADO".equals(episodio.status()) || "EN_TRIAJE".equals(episodio.status())) {
            episodeRepository.updateStatus(episodio.id(), "OBSERVACION");
        }

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.ASIGNACION_CAMA_REGISTRADA,
                "CAMA_HOSPITALARIA",
                cama.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));

        return bedRepository.findDetailByPublicId(cama.publicId())
                .orElseThrow(() -> new IllegalStateException("Error al recuperar detalle de cama asignada"));
    }

    /**
     * Traslada longitudinalmente a un paciente entre camas y áreas hospitalarias (H03).
     */
    @Transactional
    public MovementResponse transferPatient(
            String episodePublicId,
            TransferPatientRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        CareEpisode episodio = episodeRepository.findByPublicId(episodePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Episodio no encontrado: " + episodePublicId));

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado."));

        BedOccupancy ocupacionActual = occupancyRepository.findActiveByEpisodeId(episodio.id())
                .orElseThrow(() -> new OperationConflictException("El paciente no tiene una cama activa para trasladar."));

        HospitalBed camaDestino = bedRepository.findByPublicId(request.destinationBedPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Cama destino no encontrada: " + request.destinationBedPublicId()));

        if (camaDestino.id().equals(ocupacionActual.bedId())) {
            throw new OperationConflictException("La cama de destino es idéntica a la cama de origen.");
        }

        if (!"DISPONIBLE".equals(camaDestino.status())) {
            throw new OperationConflictException("La cama de destino no está disponible (Estado: " + camaDestino.status() + ").");
        }

        HospitalRoom habDestino = roomRepository.findById(camaDestino.roomId())
                .orElseThrow(() -> new IllegalStateException("Habitación destino no encontrada"));

        HospitalArea areaDestino = areaRepository.findById(habDestino.areaId())
                .orElseThrow(() -> new IllegalStateException("Área de destino no encontrada"));

        if (!Objects.equals(areaDestino.siteId(), episodio.siteId())) {
            throw new OperationConflictException("La cama de destino pertenece a una sede diferente a la del episodio.");
        }

        // Reserva atómica compare-and-set: DISPONIBLE -> OCUPADA sobre cama destino (O-03)
        boolean reservada = bedRepository.reserveBedIfAvailable(camaDestino.id());
        if (!reservada) {
            throw new OperationConflictException("La cama de destino ya no está disponible (conflicto de concurrencia).");
        }

        Instant ahora = Instant.now(clock);

        // Finalizar ocupación previa y pasar cama a LIMPIEZA
        occupancyRepository.finishOccupancy(ocupacionActual.id(), ahora);
        bedRepository.updateStatus(ocupacionActual.bedId(), "LIMPIEZA");

        // Crear nueva ocupación
        BedOccupancy nuevaOcupacion = new BedOccupancy(
                null,
                UUID.randomUUID().toString(),
                episodio.id(),
                camaDestino.id(),
                ahora,
                null,
                "ACTIVA",
                usuario.id(),
                request.transferReason(),
                ahora
        );
        try {
            occupancyRepository.save(nuevaOcupacion);
        } catch (DataIntegrityViolationException | OperationConflictException ex) {
            bedRepository.updateStatus(camaDestino.id(), "DISPONIBLE");
            throw new OperationConflictException("Conflicto de concurrencia: La cama de destino o el episodio ya cuentan con ocupación activa.");
        }

        // Obtener áreas de origen y destino
        HospitalBed camaOrigen = bedRepository.findById(ocupacionActual.bedId()).orElse(null);
        HospitalRoom habOrigen = camaOrigen != null ? roomRepository.findById(camaOrigen.roomId()).orElse(null) : null;

        Long areaOrigenId = habOrigen != null ? habOrigen.areaId() : null;
        Long areaDestinoId = habDestino.areaId();

        String movPubId = UUID.randomUUID().toString();
        PatientMovement mov = new PatientMovement(
                null,
                movPubId,
                episodio.id(),
                areaOrigenId,
                areaDestinoId,
                camaOrigen != null ? camaOrigen.id() : null,
                camaDestino.id(),
                ahora,
                request.transferReason(),
                usuario.id(),
                ahora
        );
        movementRepository.save(mov);

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.MOVIMIENTO_PACIENTE_REGISTRADO,
                "MOVIMIENTO_PACIENTE",
                movPubId,
                AuditResult.EXITO,
                ipOrigen
        ));

        List<MovementResponse> lista = movementRepository.listByEpisodeId(episodio.id());
        return lista.stream()
                .filter(m -> m.publicId().equals(movPubId))
                .findFirst()
                .orElse(new MovementResponse(
                        movPubId,
                        episodio.publicId(),
                        habOrigen != null ? "Área Previa" : "Ingreso",
                        "Área Destino",
                        camaOrigen != null ? camaOrigen.code() : "—",
                        camaDestino.code(),
                        ahora,
                        request.transferReason(),
                        usuario.email()
                ));
    }

    /**
     * Actualiza el estado operativo de una cama (ej. de LIMPIEZA a DISPONIBLE) (H01).
     */
    @Transactional
    public void changeBedStatus(
            String camaPublicId,
            String nuevoEstado,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        HospitalBed cama = bedRepository.findByPublicId(camaPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Cama no encontrada: " + camaPublicId));

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        if ("OCUPADA".equals(cama.status()) && !"OCUPADA".equals(nuevoEstado)) {
            Optional<BedOccupancy> act = occupancyRepository.findActiveByBedId(cama.id());
            if (act.isPresent()) {
                throw new OperationConflictException("No se puede cambiar el estado de una cama con un paciente activo asignado.");
            }
        }

        bedRepository.updateStatus(cama.id(), nuevoEstado);

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.ESTADO_CAMA_ACTUALIZADO,
                "CAMA_HOSPITALARIA",
                cama.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));
    }

    /**
     * Registra un procedimiento clínico, acto quirúrgico o internación en quirófano (H04).
     */
    @Transactional
    public ProcedureResponse registerProcedure(
            String episodePublicId,
            RegisterProcedureRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        CareEpisode episodio = episodeRepository.findByPublicId(episodePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Episodio no encontrado: " + episodePublicId));

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado."));

        Professional profesional = professionalRepository.findByPublicId(request.professionalPublicId())
                .orElseThrow(() -> new ResourceNotFoundException("Profesional quirúrgico no encontrado: " + request.professionalPublicId()));

        Long salaId = null;
        if (request.roomPublicId() != null && !request.roomPublicId().isBlank()) {
            HospitalRoom sala = roomRepository.findByPublicId(request.roomPublicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Sala/Quirófano no encontrado: " + request.roomPublicId()));
            salaId = sala.id();
        }

        Instant ahora = Instant.now(clock);
        String procPubId = UUID.randomUUID().toString();
        HospitalProcedure proc = new HospitalProcedure(
                null,
                procPubId,
                episodio.id(),
                request.procedureType(),
                request.description(),
                profesional.id(),
                salaId,
                "PROGRAMADO",
                ahora,
                null,
                request.notes(),
                ahora
        );
        procedureRepository.save(proc);

        episodeRepository.updateStatus(episodio.id(), "QUIROFANO");

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.PROCEDIMIENTO_HOSPITALARIO_REGISTRADO,
                "PROCEDIMIENTO_HOSPITALARIO",
                procPubId,
                AuditResult.EXITO,
                ipOrigen
        ));

        List<ProcedureResponse> list = procedureRepository.listByEpisodeId(episodio.id());
        return list.stream()
                .filter(p -> p.publicId().equals(procPubId))
                .findFirst()
                .orElse(new ProcedureResponse(
                        procPubId,
                        episodio.publicId(),
                        request.procedureType(),
                        request.description(),
                        profesional.firstNames() + " " + profesional.lastNames(),
                        "Quirófano",
                        "PROGRAMADO",
                        ahora,
                        null,
                        request.notes(),
                        ahora
                ));
    }

    /**
     * Transiciona el estado de un procedimiento quirúrgico (H04).
     */
    @Transactional
    public void updateProcedureStatus(
            String procedimientoPublicId,
            UpdateProcedureStatusRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        HospitalProcedure proc = procedureRepository.findByPublicId(procedimientoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Procedimiento no encontrado: " + procedimientoPublicId));

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Instant ahora = Instant.now(clock);
        Instant finAt = ("FINALIZADO".equals(request.newStatus()) || "CANCELADO".equals(request.newStatus())) ? ahora : null;

        procedureRepository.updateStatus(proc.id(), request.newStatus(), finAt, request.notes());

        if ("RECUPERACION".equals(request.newStatus())) {
            episodeRepository.updateStatus(proc.episodeId(), "RECUPERACION");
        } else if ("FINALIZADO".equals(request.newStatus())) {
            episodeRepository.updateStatus(proc.episodeId(), "HOSPITALIZADO");
        }

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.PROCEDIMIENTO_ESTADO_ACTUALIZADO,
                "PROCEDIMIENTO_HOSPITALARIO",
                proc.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));
    }

    /**
     * Registra el egreso médico definitivo y libera la cama hospitalaria (H05).
     */
    @Transactional
    public void registerHospitalDischarge(
            String episodePublicId,
            RegisterDischargeRequest request,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        Set<String> roles = authorities != null
                ? authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet())
                : Set.of();

        if (!roles.contains("ROLE_PROFESIONAL")) {
            throw new UnauthorizedAccessException("Solo profesionales médicos pueden emitir órdenes de egreso y alta hospitalaria.");
        }

        CareEpisode episodio = episodeRepository.findByPublicId(episodePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Episodio no encontrado: " + episodePublicId));

        if ("EGRESADO".equals(episodio.status())) {
            throw new OperationConflictException("El episodio ya fue egresado previamente.");
        }

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        Professional medico = professionalRepository.findByUserId(usuario.id())
                .orElseThrow(() -> new UnauthorizedAccessException("Usuario no registrado como profesional médico."));

        Instant ahora = Instant.now(clock);

        // Si el paciente tiene cama activa asignada, se finaliza ocupación y se pasa a LIMPIEZA
        Optional<BedOccupancy> ocupacionActiva = occupancyRepository.findActiveByEpisodeId(episodio.id());
        if (ocupacionActiva.isPresent()) {
            BedOccupancy oc = ocupacionActiva.get();
            occupancyRepository.finishOccupancy(oc.id(), ahora);
            bedRepository.updateStatus(oc.bedId(), "LIMPIEZA");
        }

        // Guardar Egreso Hospitalario
        String egresoPubId = UUID.randomUUID().toString();
        HospitalDischarge egreso = new HospitalDischarge(
                null,
                egresoPubId,
                episodio.id(),
                medico.id(),
                ahora,
                request.destinationType(),
                request.dischargeDiagnosis(),
                request.epicrisisSummary(),
                request.managementPlan(),
                ahora
        );
        dischargeRepository.save(egreso);

        // Actualizar estado del episodio a EGRESADO
        episodeRepository.closeEpisode(episodio.id(), ahora);

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.EGRESO_HOSPITALARIO_REGISTRADO,
                "EGRESO_HOSPITALARIO",
                egresoPubId,
                AuditResult.EXITO,
                ipOrigen
        ));

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.EPISODIO_CERRADO,
                "EPISODIO_ATENCION",
                episodio.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));
    }

    /**
     * Centro de Control Hospitalario: censo de camas e indicadores de ocupación en tiempo real (H06).
     * No expone contenido clínico privado a perfiles administrativos.
     */
    public BedCensusResponse getBedCensus() {
        // Hospital único: el censo siempre corresponde a la única sede.
        Site sede = siteRepository.getSingleSite();

        List<BedDetailResponse> camas = bedRepository.listDetailsBySiteId(sede.id());
        int total = camas.size();
        int ocupadas = (int) camas.stream().filter(c -> "OCUPADA".equals(c.status())).count();
        int disponibles = (int) camas.stream().filter(c -> "DISPONIBLE".equals(c.status())).count();
        int limpieza = (int) camas.stream().filter(c -> "LIMPIEZA".equals(c.status())).count();
        int mantenimiento = (int) camas.stream().filter(c -> "MANTENIMIENTO".equals(c.status()) || "INACTIVA".equals(c.status())).count();

        double tasaOcupacion = total > 0 ? (double) ocupadas / total * 100.0 : 0.0;

        List<HospitalArea> areas = areaRepository.listBySiteId(sede.id());
        List<BedCensusResponse.AreaCensusItem> areasCenso = areas.stream().map(a -> {
            List<BedDetailResponse> camasArea = camas.stream().filter(c -> a.name().equals(c.areaName())).toList();
            int totArea = camasArea.size();
            int ocuArea = (int) camasArea.stream().filter(c -> "OCUPADA".equals(c.status())).count();
            int dispArea = (int) camasArea.stream().filter(c -> "DISPONIBLE".equals(c.status())).count();
            return new BedCensusResponse.AreaCensusItem(
                    a.publicId(),
                    a.code(),
                    a.name(),
                    a.type(),
                    totArea,
                    ocuArea,
                    dispArea
            );
        }).toList();

        return new BedCensusResponse(
                sede.publicId(),
                sede.name(),
                total,
                ocupadas,
                disponibles,
                limpieza,
                mantenimiento,
                Math.round(tasaOcupacion * 10.0) / 10.0,
                areasCenso
        );
    }

    public List<BedDetailResponse> listBedDetails() {
        return listBedDetails(false);
    }

    public List<BedDetailResponse> listBedDetails(boolean anonimizarPaciente) {
        List<BedDetailResponse> camas = bedRepository.listDetailsBySiteId(siteRepository.getSingleSiteId());
        if (anonimizarPaciente) {
            return camas.stream().map(c -> new BedDetailResponse(
                    c.publicId(),
                    c.roomPublicId(),
                    c.roomCode(),
                    c.areaPublicId(),
                    c.areaName(),
                    c.sitePublicId(),
                    c.siteName(),
                    c.code(),
                    c.status(),
                    null, // episodioPublicId anonimizado para admin (S-02)
                    null, // pacienteNombre anonimizado para admin (S-02)
                    c.occupancyPublicId()
            )).toList();
        }
        return camas;
    }

    public List<MovementResponse> listEpisodeMovements(String episodePublicId) {
        CareEpisode episodio = episodeRepository.findByPublicId(episodePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Episodio no encontrado: " + episodePublicId));
        return movementRepository.listByEpisodeId(episodio.id());
    }

    public List<ProcedureResponse> listEpisodeProcedures(String episodePublicId) {
        CareEpisode episodio = episodeRepository.findByPublicId(episodePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Episodio no encontrado: " + episodePublicId));
        return procedureRepository.listByEpisodeId(episodio.id());
    }

    /**
     * Lista las áreas hospitalarias activas junto con sus habitaciones o salas configuradas.
     */
    public List<HospitalAreaResponse> listAreasWithRooms() {
        Long siteId = siteRepository.getSingleSiteId();
        List<HospitalArea> areas = areaRepository.listBySiteId(siteId);
        return areas.stream()
                .map(area -> {
                    List<HospitalRoomResponse> rooms = roomRepository.listByAreaId(area.id()).stream()
                            .map(r -> new HospitalRoomResponse(
                                    r.publicId(), area.publicId(), r.code(), r.type(), r.status()))
                            .toList();
                    return new HospitalAreaResponse(
                            area.publicId(), area.code(), area.name(), area.type(),
                            area.floor(), area.status(), rooms);
                })
                .toList();
    }

    /**
     * Crea una nueva área o pabellón hospitalario (rol administrativo).
     */
    @Transactional
    public HospitalAreaResponse createArea(
            CreateHospitalAreaRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        String tipo = request.type().trim().toUpperCase(Locale.ROOT);
        if (!VALID_AREA_TYPES.contains(tipo)) {
            throw new InvalidDataException("Tipo de área hospitalaria inválido: " + request.type());
        }

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
        Long siteId = siteRepository.getSingleSiteId();

        String codigo = request.code().trim().toUpperCase(Locale.ROOT);
        String piso = request.floor() != null && !request.floor().isBlank() ? request.floor().trim() : null;

        HospitalArea creada = areaRepository.save(new HospitalArea(
                null,
                UUID.randomUUID().toString(),
                siteId,
                codigo,
                request.name().trim(),
                tipo,
                piso,
                "ACTIVA",
                Instant.now(clock)
        ));

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.CAMBIO_ADMINISTRATIVO,
                "AREA_HOSPITALARIA",
                creada.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));

        return new HospitalAreaResponse(
                creada.publicId(), creada.code(), creada.name(), creada.type(),
                creada.floor(), creada.status(), List.of());
    }

    /**
     * Crea una habitación o sala dentro de un área hospitalaria existente (rol administrativo).
     */
    @Transactional
    public HospitalRoomResponse createRoom(
            String areaPublicId,
            CreateHospitalRoomRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        String tipo = request.type().trim().toUpperCase(Locale.ROOT);
        if (!VALID_ROOM_TYPES.contains(tipo)) {
            throw new InvalidDataException("Tipo de habitación o sala inválido: " + request.type());
        }

        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
        HospitalArea area = areaRepository.findByPublicId(areaPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Área hospitalaria no encontrada: " + areaPublicId));

        String codigo = request.code().trim().toUpperCase(Locale.ROOT);
        HospitalRoom creada = roomRepository.save(new HospitalRoom(
                null,
                UUID.randomUUID().toString(),
                area.id(),
                codigo,
                tipo,
                "ACTIVA",
                Instant.now(clock)
        ));

        auditService.audit(new AuditEvent(
                usuario.id(),
                AuditableAction.CAMBIO_ADMINISTRATIVO,
                "HABITACION_SALA",
                creada.publicId(),
                AuditResult.EXITO,
                ipOrigen
        ));

        return new HospitalRoomResponse(
                creada.publicId(), area.publicId(), creada.code(), creada.type(), creada.status());
    }

    /**
     * Registra una o varias camas disponibles dentro de una habitación o sala (rol administrativo).
     */
    @Transactional
    public List<BedDetailResponse> createBeds(
            String roomPublicId,
            CreateHospitalBedRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        User usuario = userRepository.findByPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
        HospitalRoom room = roomRepository.findByPublicId(roomPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Habitación o sala no encontrada: " + roomPublicId));
        HospitalArea area = areaRepository.findById(room.areaId()).orElse(null);

        int count = request.count() != null ? request.count() : 1;
        if (count < 1 || count > 20) {
            throw new InvalidDataException("La cantidad de camas debe estar entre 1 y 20.");
        }

        String prefix = request.code().trim().toUpperCase(Locale.ROOT);
        Instant ahora = Instant.now(clock);
        List<BedDetailResponse> result = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            String bedCode = count == 1 ? prefix : prefix + "-" + String.format("%02d", i);
            if (bedCode.length() > 30) {
                throw new InvalidDataException("El código generado para la cama excede 30 caracteres: " + bedCode);
            }

            HospitalBed guardada = bedRepository.save(new HospitalBed(
                    null,
                    UUID.randomUUID().toString(),
                    room.id(),
                    bedCode,
                    "DISPONIBLE",
                    ahora,
                    ahora
            ));

            auditService.audit(new AuditEvent(
                    usuario.id(),
                    AuditableAction.CAMBIO_ADMINISTRATIVO,
                    "CAMA_HOSPITALARIA",
                    guardada.publicId(),
                    AuditResult.EXITO,
                    ipOrigen
            ));

            BedDetailResponse detail = bedRepository.findDetailByPublicId(guardada.publicId())
                    .orElseGet(() -> new BedDetailResponse(
                            guardada.publicId(),
                            room.publicId(),
                            room.code(),
                            area != null ? area.publicId() : null,
                            area != null ? area.name() : null,
                            null,
                            null,
                            guardada.code(),
                            guardada.status(),
                            null,
                            null,
                            null
                    ));
            result.add(detail);
        }

        return result;
    }
}
