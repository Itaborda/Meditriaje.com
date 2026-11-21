-- =============================================================================
-- MediTriaje - R__pkg_1_spec_clinical: especificación de PKG_CLINICAL
-- Repetible. Atención médica (ATENCION, SIGNO_VITAL, ATENCION_ENMIENDA) y seguimiento post-atención
-- (SEGUIMIENTO_POST_ATENCION). Los triggers de inmutabilidad de las tablas clínicas siguen activos.
-- Derechos del definidor; nunca ejecuta COMMIT ni ROLLBACK (la transacción es de Java).
-- =============================================================================
CREATE OR REPLACE PACKAGE PKG_CLINICAL AUTHID DEFINER AS

    -- ---------------------------------------------------------------- ATENCION
    -- Registra una atención y devuelve su ID interno.
    PROCEDURE create_encounter(p_public_id IN VARCHAR2, p_appointment_id IN NUMBER, p_patient_id IN NUMBER,
                               p_professional_id IN NUMBER, p_status IN VARCHAR2, o_id OUT NUMBER);

    -- Atención por PUBLIC_ID, ID interno o ID de cita. Columnas: ID, PUBLIC_ID, CITA_ID, PACIENTE_ID,
    -- PROFESIONAL_ID, DIAGNOSTICO_PRINCIPAL_ID, MOTIVO_CONSULTA, EVOLUCION, INDICACIONES, ESTADO,
    -- FECHA_CIERRE, CREATED_AT.
    PROCEDURE get_encounter_by_public_id(p_public_id IN VARCHAR2, o_rows OUT SYS_REFCURSOR);
    PROCEDURE get_encounter_by_id(p_id IN NUMBER, o_rows OUT SYS_REFCURSOR);
    PROCEDURE get_encounter_by_appointment_id(p_appointment_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Cantidad de atenciones registradas para la cita.
    FUNCTION count_encounters_by_appointment(p_appointment_id IN NUMBER) RETURN NUMBER;

    -- Cierra una atención ABIERTA; o_affected = filas actualizadas (0 si no existe o ya está cerrada).
    PROCEDURE close_encounter(p_id IN NUMBER, p_diagnosis_id IN NUMBER, p_reason IN VARCHAR2,
                              p_evolution IN VARCHAR2, p_instructions IN VARCHAR2,
                              p_closed_at IN TIMESTAMP WITH TIME ZONE, o_affected OUT NUMBER);

    -- Registra los signos vitales de una atención.
    PROCEDURE create_vital_signs(p_encounter_id IN NUMBER, p_systolic IN NUMBER, p_diastolic IN NUMBER,
                                 p_heart_rate IN NUMBER, p_respiratory_rate IN NUMBER, p_temperature IN NUMBER,
                                 p_oxygen_saturation IN NUMBER, p_weight_kg IN NUMBER, p_height_cm IN NUMBER);

    -- Signos vitales registrados para la atención.
    PROCEDURE list_vital_signs_by_encounter(p_encounter_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Detalle de la atención (cita, paciente, profesional, especialidad y diagnóstico) por PUBLIC_ID.
    PROCEDURE get_encounter_detail_by_public_id(p_public_id IN VARCHAR2, o_rows OUT SYS_REFCURSOR);

    -- Registra una enmienda (append-only) y devuelve su ID interno.
    PROCEDURE create_amendment(p_encounter_id IN NUMBER, p_professional_id IN NUMBER, p_reason IN VARCHAR2,
                               p_content IN VARCHAR2, p_amended_at IN TIMESTAMP WITH TIME ZONE, o_id OUT NUMBER);

    -- Enmiendas de la atención en orden cronológico ascendente.
    PROCEDURE list_amendments_by_encounter(p_encounter_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Cantidad de atenciones del profesional al paciente creadas desde p_since.
    FUNCTION count_encounters_since(p_patient_id IN NUMBER, p_professional_id IN NUMBER,
                                    p_since IN TIMESTAMP WITH TIME ZONE) RETURN NUMBER;

    -- Historia clínica paginada del paciente (más recientes primero).
    PROCEDURE list_patient_history(p_patient_id IN NUMBER, p_offset IN NUMBER, p_size IN NUMBER,
                                   o_rows OUT SYS_REFCURSOR);

    -- Total de atenciones del paciente.
    FUNCTION count_patient_history(p_patient_id IN NUMBER) RETURN NUMBER;

    -- ------------------------------------------------ SEGUIMIENTO_POST_ATENCION
    -- Registra un seguimiento y devuelve su ID interno.
    PROCEDURE create_follow_up(p_public_id IN VARCHAR2, p_encounter_id IN NUMBER, p_patient_id IN NUMBER,
                               p_professional_id IN NUMBER, p_type IN VARCHAR2, p_instructions IN VARCHAR2,
                               p_suggested_control_date IN DATE, p_status IN VARCHAR2,
                               p_patient_answered_at IN TIMESTAMP WITH TIME ZONE, p_patient_report IN VARCHAR2,
                               p_created_at IN TIMESTAMP WITH TIME ZONE, p_updated_at IN TIMESTAMP WITH TIME ZONE,
                               o_id OUT NUMBER);

    -- Seguimiento (fila completa) por PUBLIC_ID o por ID interno.
    PROCEDURE get_follow_up_by_public_id(p_public_id IN VARCHAR2, o_rows OUT SYS_REFCURSOR);
    PROCEDURE get_follow_up_by_id(p_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Seguimiento con datos de atención, paciente, profesional y especialidad por PUBLIC_ID.
    PROCEDURE get_follow_up_view_by_public_id(p_public_id IN VARCHAR2, o_rows OUT SYS_REFCURSOR);

    -- Registra el reporte del paciente y marca el seguimiento como COMPLETADO.
    PROCEDURE register_follow_up_report(p_id IN NUMBER, p_report IN VARCHAR2,
                                        p_answered_at IN TIMESTAMP WITH TIME ZONE);

    -- Cambia el estado del seguimiento.
    PROCEDURE update_follow_up_status(p_id IN NUMBER, p_status IN VARCHAR2);

    -- Seguimientos de una atención en orden de creación.
    PROCEDURE list_follow_ups_by_encounter(p_encounter_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Seguimientos paginados de un paciente; p_status nulo no filtra por estado.
    PROCEDURE list_follow_ups_by_patient(p_patient_id IN NUMBER, p_status IN VARCHAR2, p_offset IN NUMBER,
                                         p_size IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Total de seguimientos de un paciente; p_status nulo no filtra por estado.
    FUNCTION count_follow_ups_by_patient(p_patient_id IN NUMBER, p_status IN VARCHAR2) RETURN NUMBER;
END PKG_CLINICAL;
/
