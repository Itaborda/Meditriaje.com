-- =============================================================================
-- MediTriaje - R__pkg_1_spec_scheduling: especificación de PKG_SCHEDULING
-- Repetible. Agenda: citas (CITA), disponibilidad de profesionales (DISPONIBILIDAD_SLOT), ausencias médicas
-- (AUSENCIA_MEDICA) y recordatorios de citas (RECORDATORIO_CITA).
-- Derechos del definidor; nunca ejecuta COMMIT ni ROLLBACK (la transacción es de Java).
-- Las consultas de un solo registro devuelven un cursor con 0 o 1 fila. Los filtros opcionales llegan en NULL
-- cuando no aplican; la normalización (recorte y mayúsculas) la hace el repositorio Java.
-- Los conteos se devuelven con funciones NUMBER.
-- =============================================================================
CREATE OR REPLACE PACKAGE PKG_SCHEDULING AUTHID DEFINER AS

    -- ---------------------------------------------------------------- Citas
    -- Registra una cita y devuelve su ID interno.
    PROCEDURE create_appointment(p_public_id IN VARCHAR2, p_slot_id IN NUMBER, p_patient_id IN NUMBER,
                                 p_triage_id IN NUMBER, p_origin_appointment_id IN NUMBER,
                                 p_status IN VARCHAR2, p_cancel_reason IN VARCHAR2, o_id OUT NUMBER);
    -- Detalle consolidado de una cita (slot, paciente, profesional, especialidad, sede, triaje y atención).
    PROCEDURE get_appointment_detail(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);
    -- Cita por identificador público.
    PROCEDURE get_appointment_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);
    -- Cita por ID interno.
    PROCEDURE get_appointment_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    -- Cambia el estado y el motivo de cancelación de una cita.
    PROCEDURE update_appointment_status(p_id IN NUMBER, p_status IN VARCHAR2, p_cancel_reason IN VARCHAR2);
    -- Número de citas activas (PROGRAMADA o CONFIRMADA) en un slot.
    FUNCTION count_active_appointments_in_slot(p_slot_id IN NUMBER) RETURN NUMBER;
    -- Agenda paginada de un profesional, por inicio del slot y luego por ID de cita.
    PROCEDURE list_professional_agenda(p_professional_id IN NUMBER, p_from IN TIMESTAMP WITH TIME ZONE,
                                       p_to IN TIMESTAMP WITH TIME ZONE, p_status IN VARCHAR2,
                                       p_offset IN NUMBER, p_limit IN NUMBER, o_rows OUT SYS_REFCURSOR);
    -- Total de citas de la agenda de un profesional para los mismos filtros.
    FUNCTION count_professional_agenda(p_professional_id IN NUMBER, p_from IN TIMESTAMP WITH TIME ZONE,
                                       p_to IN TIMESTAMP WITH TIME ZONE, p_status IN VARCHAR2) RETURN NUMBER;
    -- Número de citas activas futuras entre un profesional y un paciente (relación asistencial).
    FUNCTION count_future_active_appointments(p_professional_id IN NUMBER, p_patient_id IN NUMBER,
                                              p_now IN TIMESTAMP WITH TIME ZONE) RETURN NUMBER;
    -- Citas paginadas de un paciente, más recientes primero.
    PROCEDURE list_patient_appointments(p_patient_id IN NUMBER, p_offset IN NUMBER, p_limit IN NUMBER,
                                        o_rows OUT SYS_REFCURSOR);
    -- Total de citas de un paciente.
    FUNCTION count_patient_appointments(p_patient_id IN NUMBER) RETURN NUMBER;
    -- Citas para supervisión administrativa. p_limit NULL = sin paginación.
    PROCEDURE list_admin_appointments(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                      p_professional_public_id IN VARCHAR2, p_specialty_public_id IN VARCHAR2,
                                      p_status IN VARCHAR2, p_offset IN NUMBER, p_limit IN NUMBER,
                                      o_rows OUT SYS_REFCURSOR);
    -- Total de citas para los filtros administrativos.
    FUNCTION count_admin_appointments(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                      p_professional_public_id IN VARCHAR2, p_specialty_public_id IN VARCHAR2,
                                      p_status IN VARCHAR2) RETURN NUMBER;

    -- ---------------------------------------------------------------- Disponibilidad
    -- Registra un slot en la única sede del hospital y devuelve su ID interno.
    PROCEDURE create_slot(p_public_id IN VARCHAR2, p_professional_id IN NUMBER, p_specialty_id IN NUMBER,
                          p_start_at IN TIMESTAMP WITH TIME ZONE, p_end_at IN TIMESTAMP WITH TIME ZONE,
                          p_modality IN VARCHAR2, p_status IN VARCHAR2, o_id OUT NUMBER);
    -- Slot con profesional, sede y especialidad por identificador público.
    PROCEDURE get_slot_detail(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);
    -- Slot por ID interno.
    PROCEDURE get_slot_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    -- Slot por identificador público.
    PROCEDURE get_slot_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);
    -- Número de slots del profesional que se cruzan con el intervalo [p_start_at, p_end_at).
    FUNCTION count_overlapping_slots(p_professional_id IN NUMBER, p_start_at IN TIMESTAMP WITH TIME ZONE,
                                     p_end_at IN TIMESTAMP WITH TIME ZONE) RETURN NUMBER;
    -- Cambia el estado de un slot por identificador público.
    PROCEDURE update_slot_status(p_public_id IN VARCHAR2, p_status IN VARCHAR2);
    -- Elimina un slot solo si está LIBRE; o_deleted = filas eliminadas.
    PROCEDURE delete_free_slot(p_public_id IN VARCHAR2, o_deleted OUT NUMBER);
    -- Slots paginados para administración, por inicio y luego por ID.
    PROCEDURE list_slots(p_professional_public_id IN VARCHAR2, p_site_public_id IN VARCHAR2,
                         p_specialty_public_id IN VARCHAR2, p_from IN TIMESTAMP WITH TIME ZONE,
                         p_to IN TIMESTAMP WITH TIME ZONE, p_status IN VARCHAR2, p_offset IN NUMBER,
                         p_limit IN NUMBER, o_rows OUT SYS_REFCURSOR);
    -- Total de slots para los filtros administrativos.
    FUNCTION count_slots(p_professional_public_id IN VARCHAR2, p_site_public_id IN VARCHAR2,
                         p_specialty_public_id IN VARCHAR2, p_from IN TIMESTAMP WITH TIME ZONE,
                         p_to IN TIMESTAMP WITH TIME ZONE, p_status IN VARCHAR2) RETURN NUMBER;
    -- Slots LIBRE reservables (profesional, sede y especialidad activos), paginados.
    PROCEDURE list_available_slots(p_specialty_public_id IN VARCHAR2, p_professional_public_id IN VARCHAR2,
                                   p_site_public_id IN VARCHAR2, p_modality IN VARCHAR2,
                                   p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                   p_offset IN NUMBER, p_limit IN NUMBER, o_rows OUT SYS_REFCURSOR);
    -- Total de slots reservables para los mismos filtros.
    FUNCTION count_available_slots(p_specialty_public_id IN VARCHAR2, p_professional_public_id IN VARCHAR2,
                                   p_site_public_id IN VARCHAR2, p_modality IN VARCHAR2,
                                   p_from IN TIMESTAMP WITH TIME ZONE,
                                   p_to IN TIMESTAMP WITH TIME ZONE) RETURN NUMBER;
    -- Reserva atómica: LIBRE -> OCUPADO. o_updated = 1 si se reservó, 0 si ya no estaba LIBRE.
    PROCEDURE reserve_slot(p_slot_id IN NUMBER, o_updated OUT NUMBER);
    -- Liberación: OCUPADO -> LIBRE. o_updated = filas afectadas.
    PROCEDURE release_slot(p_slot_id IN NUMBER, o_updated OUT NUMBER);

    -- ---------------------------------------------------------------- Ausencias médicas
    -- Registra una ausencia de un profesional y devuelve su ID interno.
    PROCEDURE create_absence(p_public_id IN VARCHAR2, p_professional_id IN NUMBER,
                             p_start_at IN TIMESTAMP WITH TIME ZONE, p_end_at IN TIMESTAMP WITH TIME ZONE,
                             p_reason IN VARCHAR2, p_status IN VARCHAR2, p_registered_by_user_id IN NUMBER,
                             o_id OUT NUMBER);
    -- Ausencia por ID interno.
    PROCEDURE get_absence_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    -- Número de ausencias ACTIVAS del profesional que se cruzan con el intervalo dado.
    FUNCTION count_overlapping_absences(p_professional_id IN NUMBER, p_start_at IN TIMESTAMP WITH TIME ZONE,
                                        p_end_at IN TIMESTAMP WITH TIME ZONE) RETURN NUMBER;
    -- Ausencias de un profesional, más recientes primero.
    PROCEDURE list_absences_by_professional(p_professional_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- ---------------------------------------------------------------- Recordatorios
    -- Registra un recordatorio o notificación de cita y devuelve su ID interno.
    PROCEDURE create_reminder(p_public_id IN VARCHAR2, p_appointment_id IN NUMBER, p_patient_id IN NUMBER,
                              p_type IN VARCHAR2, p_channel IN VARCHAR2, p_recipient IN VARCHAR2,
                              p_delivery_status IN VARCHAR2, p_error_message IN VARCHAR2,
                              p_sent_at IN TIMESTAMP WITH TIME ZONE, o_id OUT NUMBER);
    -- Recordatorios de una cita en orden de registro.
    PROCEDURE list_reminders_by_appointment(p_appointment_id IN NUMBER, o_rows OUT SYS_REFCURSOR);
    -- Número de recordatorios de un tipo emitidos para una cita.
    FUNCTION count_reminders_by_type(p_appointment_id IN NUMBER, p_type IN VARCHAR2) RETURN NUMBER;
END PKG_SCHEDULING;
/
