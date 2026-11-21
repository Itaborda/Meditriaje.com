-- =============================================================================
-- MediTriaje - R__pkg_2_body_clinical: cuerpo de PKG_CLINICAL
-- Repetible. Sin COMMIT ni ROLLBACK (la transacción pertenece a la capa de aplicación).
-- =============================================================================
CREATE OR REPLACE PACKAGE BODY PKG_CLINICAL AS

    -- ---------------------------------------------------------------- ATENCION
    PROCEDURE create_encounter(p_public_id IN VARCHAR2, p_appointment_id IN NUMBER, p_patient_id IN NUMBER,
                               p_professional_id IN NUMBER, p_status IN VARCHAR2, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO ATENCION (PUBLIC_ID, CITA_ID, PACIENTE_ID, PROFESIONAL_ID, ESTADO)
        VALUES (p_public_id, p_appointment_id, p_patient_id, p_professional_id, p_status)
        RETURNING ID INTO o_id;
    END create_encounter;

    PROCEDURE get_encounter_by_public_id(p_public_id IN VARCHAR2, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ID, PUBLIC_ID, CITA_ID, PACIENTE_ID, PROFESIONAL_ID, DIAGNOSTICO_PRINCIPAL_ID,
                   MOTIVO_CONSULTA, EVOLUCION, INDICACIONES, ESTADO, FECHA_CIERRE, CREATED_AT
              FROM ATENCION
             WHERE PUBLIC_ID = p_public_id;
    END get_encounter_by_public_id;

    PROCEDURE get_encounter_by_id(p_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ID, PUBLIC_ID, CITA_ID, PACIENTE_ID, PROFESIONAL_ID, DIAGNOSTICO_PRINCIPAL_ID,
                   MOTIVO_CONSULTA, EVOLUCION, INDICACIONES, ESTADO, FECHA_CIERRE, CREATED_AT
              FROM ATENCION
             WHERE ID = p_id;
    END get_encounter_by_id;

    PROCEDURE get_encounter_by_appointment_id(p_appointment_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ID, PUBLIC_ID, CITA_ID, PACIENTE_ID, PROFESIONAL_ID, DIAGNOSTICO_PRINCIPAL_ID,
                   MOTIVO_CONSULTA, EVOLUCION, INDICACIONES, ESTADO, FECHA_CIERRE, CREATED_AT
              FROM ATENCION
             WHERE CITA_ID = p_appointment_id;
    END get_encounter_by_appointment_id;

    FUNCTION count_encounters_by_appointment(p_appointment_id IN NUMBER) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM ATENCION WHERE CITA_ID = p_appointment_id;
        RETURN v_count;
    END count_encounters_by_appointment;

    PROCEDURE close_encounter(p_id IN NUMBER, p_diagnosis_id IN NUMBER, p_reason IN VARCHAR2,
                              p_evolution IN VARCHAR2, p_instructions IN VARCHAR2,
                              p_closed_at IN TIMESTAMP WITH TIME ZONE, o_affected OUT NUMBER) IS
    BEGIN
        UPDATE ATENCION
           SET ESTADO = 'CERRADA',
               FECHA_CIERRE = p_closed_at,
               DIAGNOSTICO_PRINCIPAL_ID = p_diagnosis_id,
               MOTIVO_CONSULTA = p_reason,
               EVOLUCION = p_evolution,
               INDICACIONES = p_instructions
         WHERE ID = p_id AND ESTADO = 'ABIERTA';
        o_affected := SQL%ROWCOUNT;
    END close_encounter;

    PROCEDURE create_vital_signs(p_encounter_id IN NUMBER, p_systolic IN NUMBER, p_diastolic IN NUMBER,
                                 p_heart_rate IN NUMBER, p_respiratory_rate IN NUMBER, p_temperature IN NUMBER,
                                 p_oxygen_saturation IN NUMBER, p_weight_kg IN NUMBER, p_height_cm IN NUMBER) IS
    BEGIN
        INSERT INTO SIGNO_VITAL (ATENCION_ID, PRESION_SISTOLICA, PRESION_DIASTOLICA, FRECUENCIA_CARDIACA,
                                 FRECUENCIA_RESPIRATORIA, TEMPERATURA, SATURACION_OXIGENO, PESO_KG, TALLA_CM)
        VALUES (p_encounter_id, p_systolic, p_diastolic, p_heart_rate,
                p_respiratory_rate, p_temperature, p_oxygen_saturation, p_weight_kg, p_height_cm);
    END create_vital_signs;

    PROCEDURE list_vital_signs_by_encounter(p_encounter_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT PRESION_SISTOLICA, PRESION_DIASTOLICA, FRECUENCIA_CARDIACA,
                   FRECUENCIA_RESPIRATORIA, TEMPERATURA, SATURACION_OXIGENO, PESO_KG, TALLA_CM
              FROM SIGNO_VITAL
             WHERE ATENCION_ID = p_encounter_id;
    END list_vital_signs_by_encounter;

    PROCEDURE get_encounter_detail_by_public_id(p_public_id IN VARCHAR2, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT a.ID AS ATENCION_ID,
                   a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   c.PUBLIC_ID AS CITA_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   TRIM(p.NOMBRES || ' ' || p.APELLIDOS) AS PACIENTE_NOMBRE,
                   pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   TRIM(pr.NOMBRES || ' ' || pr.APELLIDOS) AS PROFESIONAL_NOMBRE,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   a.ESTADO,
                   a.CREATED_AT,
                   a.FECHA_CIERRE,
                   d.CODIGO AS DIAGNOSTICO_CODIGO,
                   d.DESCRIPCION AS DIAGNOSTICO_DESCRIPCION,
                   a.MOTIVO_CONSULTA,
                   a.EVOLUCION,
                   a.INDICACIONES
              FROM ATENCION a
              JOIN CITA c ON a.CITA_ID = c.ID
              JOIN PACIENTE p ON a.PACIENTE_ID = p.ID
              JOIN PROFESIONAL pr ON a.PROFESIONAL_ID = pr.ID
              JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
              JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
              LEFT JOIN DIAGNOSTICO_CIE10 d ON a.DIAGNOSTICO_PRINCIPAL_ID = d.ID
             WHERE a.PUBLIC_ID = p_public_id;
    END get_encounter_detail_by_public_id;

    PROCEDURE create_amendment(p_encounter_id IN NUMBER, p_professional_id IN NUMBER, p_reason IN VARCHAR2,
                               p_content IN VARCHAR2, p_amended_at IN TIMESTAMP WITH TIME ZONE, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO ATENCION_ENMIENDA (ATENCION_ID, PROFESIONAL_ID, MOTIVO, CONTENIDO, FECHA_ENMIENDA)
        VALUES (p_encounter_id, p_professional_id, p_reason, p_content, p_amended_at)
        RETURNING ID INTO o_id;
    END create_amendment;

    PROCEDURE list_amendments_by_encounter(p_encounter_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   TRIM(pr.NOMBRES || ' ' || pr.APELLIDOS) AS PROFESIONAL_NOMBRE,
                   ae.MOTIVO,
                   ae.CONTENIDO,
                   ae.FECHA_ENMIENDA
              FROM ATENCION_ENMIENDA ae
              JOIN PROFESIONAL pr ON ae.PROFESIONAL_ID = pr.ID
             WHERE ae.ATENCION_ID = p_encounter_id
             ORDER BY ae.FECHA_ENMIENDA ASC, ae.ID ASC;
    END list_amendments_by_encounter;

    FUNCTION count_encounters_since(p_patient_id IN NUMBER, p_professional_id IN NUMBER,
                                    p_since IN TIMESTAMP WITH TIME ZONE) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM ATENCION a
         WHERE a.PACIENTE_ID = p_patient_id
           AND a.PROFESIONAL_ID = p_professional_id
           AND a.CREATED_AT >= p_since;
        RETURN v_count;
    END count_encounters_since;

    PROCEDURE list_patient_history(p_patient_id IN NUMBER, p_offset IN NUMBER, p_size IN NUMBER,
                                   o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT a.ID AS ATENCION_ID,
                   a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   c.PUBLIC_ID AS CITA_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   TRIM(p.NOMBRES || ' ' || p.APELLIDOS) AS PACIENTE_NOMBRE,
                   pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   TRIM(pr.NOMBRES || ' ' || pr.APELLIDOS) AS PROFESIONAL_NOMBRE,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   a.ESTADO,
                   a.CREATED_AT,
                   a.FECHA_CIERRE,
                   d.CODIGO AS DIAGNOSTICO_CODIGO,
                   d.DESCRIPCION AS DIAGNOSTICO_DESCRIPCION,
                   a.MOTIVO_CONSULTA,
                   a.EVOLUCION,
                   a.INDICACIONES
              FROM ATENCION a
              JOIN CITA c ON a.CITA_ID = c.ID
              JOIN PACIENTE p ON a.PACIENTE_ID = p.ID
              JOIN PROFESIONAL pr ON a.PROFESIONAL_ID = pr.ID
              JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
              JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
              LEFT JOIN DIAGNOSTICO_CIE10 d ON a.DIAGNOSTICO_PRINCIPAL_ID = d.ID
             WHERE a.PACIENTE_ID = p_patient_id
             ORDER BY a.CREATED_AT DESC, a.ID DESC
            OFFSET p_offset ROWS FETCH NEXT p_size ROWS ONLY;
    END list_patient_history;

    FUNCTION count_patient_history(p_patient_id IN NUMBER) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM ATENCION WHERE PACIENTE_ID = p_patient_id;
        RETURN v_count;
    END count_patient_history;

    -- ------------------------------------------------ SEGUIMIENTO_POST_ATENCION
    PROCEDURE create_follow_up(p_public_id IN VARCHAR2, p_encounter_id IN NUMBER, p_patient_id IN NUMBER,
                               p_professional_id IN NUMBER, p_type IN VARCHAR2, p_instructions IN VARCHAR2,
                               p_suggested_control_date IN DATE, p_status IN VARCHAR2,
                               p_patient_answered_at IN TIMESTAMP WITH TIME ZONE, p_patient_report IN VARCHAR2,
                               p_created_at IN TIMESTAMP WITH TIME ZONE, p_updated_at IN TIMESTAMP WITH TIME ZONE,
                               o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO SEGUIMIENTO_POST_ATENCION (PUBLIC_ID, ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID,
                                               TIPO, INDICACIONES, FECHA_SUGERIDA_CONTROL, ESTADO,
                                               FECHA_RESPUESTA_PACIENTE, REPORTE_PACIENTE, CREATED_AT, UPDATED_AT)
        VALUES (p_public_id, p_encounter_id, p_patient_id, p_professional_id,
                p_type, p_instructions, p_suggested_control_date, p_status,
                p_patient_answered_at, p_patient_report, p_created_at, p_updated_at)
        RETURNING ID INTO o_id;
    END create_follow_up;

    PROCEDURE get_follow_up_by_public_id(p_public_id IN VARCHAR2, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ID, PUBLIC_ID, ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID,
                   TIPO, INDICACIONES, FECHA_SUGERIDA_CONTROL, ESTADO,
                   FECHA_RESPUESTA_PACIENTE, REPORTE_PACIENTE, CREATED_AT, UPDATED_AT
              FROM SEGUIMIENTO_POST_ATENCION
             WHERE PUBLIC_ID = p_public_id;
    END get_follow_up_by_public_id;

    PROCEDURE get_follow_up_by_id(p_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ID, PUBLIC_ID, ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID,
                   TIPO, INDICACIONES, FECHA_SUGERIDA_CONTROL, ESTADO,
                   FECHA_RESPUESTA_PACIENTE, REPORTE_PACIENTE, CREATED_AT, UPDATED_AT
              FROM SEGUIMIENTO_POST_ATENCION
             WHERE ID = p_id;
    END get_follow_up_by_id;

    PROCEDURE get_follow_up_view_by_public_id(p_public_id IN VARCHAR2, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT s.ID, s.PUBLIC_ID, s.ATENCION_ID, s.PACIENTE_ID, s.PROFESIONAL_ID,
                   s.TIPO, s.INDICACIONES, s.FECHA_SUGERIDA_CONTROL, s.ESTADO,
                   s.FECHA_RESPUESTA_PACIENTE, s.REPORTE_PACIENTE, s.CREATED_AT, s.UPDATED_AT,
                   a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   TRIM(p.NOMBRES || ' ' || p.APELLIDOS) AS PACIENTE_NOMBRE,
                   prof.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   TRIM(prof.NOMBRES || ' ' || prof.APELLIDOS) AS PROFESIONAL_NOMBRE,
                   esp.NOMBRE AS ESPECIALIDAD_NOMBRE
              FROM SEGUIMIENTO_POST_ATENCION s
              JOIN ATENCION a ON s.ATENCION_ID = a.ID
              JOIN PACIENTE p ON s.PACIENTE_ID = p.ID
              JOIN PROFESIONAL prof ON s.PROFESIONAL_ID = prof.ID
              JOIN ESPECIALIDAD esp ON prof.ESPECIALIDAD_ID = esp.ID
             WHERE s.PUBLIC_ID = p_public_id;
    END get_follow_up_view_by_public_id;

    PROCEDURE register_follow_up_report(p_id IN NUMBER, p_report IN VARCHAR2,
                                        p_answered_at IN TIMESTAMP WITH TIME ZONE) IS
    BEGIN
        UPDATE SEGUIMIENTO_POST_ATENCION
           SET REPORTE_PACIENTE = p_report,
               FECHA_RESPUESTA_PACIENTE = p_answered_at,
               ESTADO = 'COMPLETADO',
               UPDATED_AT = CURRENT_TIMESTAMP
         WHERE ID = p_id;
    END register_follow_up_report;

    PROCEDURE update_follow_up_status(p_id IN NUMBER, p_status IN VARCHAR2) IS
    BEGIN
        UPDATE SEGUIMIENTO_POST_ATENCION
           SET ESTADO = p_status,
               UPDATED_AT = CURRENT_TIMESTAMP
         WHERE ID = p_id;
    END update_follow_up_status;

    PROCEDURE list_follow_ups_by_encounter(p_encounter_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT s.ID, s.PUBLIC_ID, s.ATENCION_ID, s.PACIENTE_ID, s.PROFESIONAL_ID,
                   s.TIPO, s.INDICACIONES, s.FECHA_SUGERIDA_CONTROL, s.ESTADO,
                   s.FECHA_RESPUESTA_PACIENTE, s.REPORTE_PACIENTE, s.CREATED_AT, s.UPDATED_AT,
                   a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   TRIM(p.NOMBRES || ' ' || p.APELLIDOS) AS PACIENTE_NOMBRE,
                   prof.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   TRIM(prof.NOMBRES || ' ' || prof.APELLIDOS) AS PROFESIONAL_NOMBRE,
                   esp.NOMBRE AS ESPECIALIDAD_NOMBRE
              FROM SEGUIMIENTO_POST_ATENCION s
              JOIN ATENCION a ON s.ATENCION_ID = a.ID
              JOIN PACIENTE p ON s.PACIENTE_ID = p.ID
              JOIN PROFESIONAL prof ON s.PROFESIONAL_ID = prof.ID
              JOIN ESPECIALIDAD esp ON prof.ESPECIALIDAD_ID = esp.ID
             WHERE s.ATENCION_ID = p_encounter_id
             ORDER BY s.CREATED_AT ASC, s.ID ASC;
    END list_follow_ups_by_encounter;

    PROCEDURE list_follow_ups_by_patient(p_patient_id IN NUMBER, p_status IN VARCHAR2, p_offset IN NUMBER,
                                         p_size IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT s.ID, s.PUBLIC_ID, s.ATENCION_ID, s.PACIENTE_ID, s.PROFESIONAL_ID,
                   s.TIPO, s.INDICACIONES, s.FECHA_SUGERIDA_CONTROL, s.ESTADO,
                   s.FECHA_RESPUESTA_PACIENTE, s.REPORTE_PACIENTE, s.CREATED_AT, s.UPDATED_AT,
                   a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   TRIM(p.NOMBRES || ' ' || p.APELLIDOS) AS PACIENTE_NOMBRE,
                   prof.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   TRIM(prof.NOMBRES || ' ' || prof.APELLIDOS) AS PROFESIONAL_NOMBRE,
                   esp.NOMBRE AS ESPECIALIDAD_NOMBRE
              FROM SEGUIMIENTO_POST_ATENCION s
              JOIN ATENCION a ON s.ATENCION_ID = a.ID
              JOIN PACIENTE p ON s.PACIENTE_ID = p.ID
              JOIN PROFESIONAL prof ON s.PROFESIONAL_ID = prof.ID
              JOIN ESPECIALIDAD esp ON prof.ESPECIALIDAD_ID = esp.ID
             WHERE s.PACIENTE_ID = p_patient_id
               AND (p_status IS NULL OR s.ESTADO = p_status)
             ORDER BY s.CREATED_AT DESC, s.ID DESC
            OFFSET p_offset ROWS FETCH NEXT p_size ROWS ONLY;
    END list_follow_ups_by_patient;

    FUNCTION count_follow_ups_by_patient(p_patient_id IN NUMBER, p_status IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM SEGUIMIENTO_POST_ATENCION
         WHERE PACIENTE_ID = p_patient_id
           AND (p_status IS NULL OR ESTADO = p_status);
        RETURN v_count;
    END count_follow_ups_by_patient;
END PKG_CLINICAL;
/
