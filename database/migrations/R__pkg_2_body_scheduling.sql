-- =============================================================================
-- MediTriaje - R__pkg_2_body_scheduling: cuerpo de PKG_SCHEDULING
-- Repetible. Sin COMMIT ni ROLLBACK (la transacción pertenece a la capa de aplicación).
-- =============================================================================
CREATE OR REPLACE PACKAGE BODY PKG_SCHEDULING AS

    -- ---------------------------------------------------------------- Citas
    PROCEDURE create_appointment(p_public_id IN VARCHAR2, p_slot_id IN NUMBER, p_patient_id IN NUMBER,
                                 p_triage_id IN NUMBER, p_origin_appointment_id IN NUMBER,
                                 p_status IN VARCHAR2, p_cancel_reason IN VARCHAR2, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO CITA (PUBLIC_ID, SLOT_ID, PACIENTE_ID, TRIAJE_ID, CITA_ORIGEN_ID, ESTADO, MOTIVO_CANCELACION)
        VALUES (p_public_id, p_slot_id, p_patient_id, p_triage_id, p_origin_appointment_id, p_status, p_cancel_reason)
        RETURNING ID INTO o_id;
    END create_appointment;

    PROCEDURE get_appointment_detail(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT c.PUBLIC_ID AS CITA_PUBLIC_ID,
                   s.PUBLIC_ID AS SLOT_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   p.NOMBRES || ' ' || p.APELLIDOS AS PACIENTE_NOMBRE,
                   pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   pr.NOMBRES || ' ' || pr.APELLIDOS AS PROFESIONAL_NOMBRE,
                   e.PUBLIC_ID AS ESPECIALIDAD_PUBLIC_ID,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   cfg.PUBLIC_ID AS SEDE_PUBLIC_ID,
                   cfg.RAZON_SOCIAL AS SEDE_NOMBRE,
                   cfg.DIRECCION AS SEDE_DIRECCION,
                   s.FECHA_HORA_INICIO,
                   s.FECHA_HORA_FIN,
                   s.MODALIDAD,
                   c.ESTADO,
                   t.PUBLIC_ID AS TRIAJE_PUBLIC_ID,
                   co.PUBLIC_ID AS CITA_ORIGEN_PUBLIC_ID,
                   t.NIVEL_PRIORIDAD AS TRIAJE_NIVEL,
                   COALESCE(t.OBSERVACIONES, 'Consulta médica general') AS MOTIVO_CONSULTA,
                   c.CREATED_AT,
                   (CASE WHEN atn.ID IS NOT NULL THEN 1 ELSE 0 END) AS TIENE_ATENCION,
                   atn.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   atn.ESTADO AS ATENCION_ESTADO
              FROM CITA c
              JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
              JOIN PACIENTE p ON c.PACIENTE_ID = p.ID
              JOIN PROFESIONAL pr ON s.PROFESIONAL_ID = pr.ID
              JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
              CROSS JOIN CONFIGURACION_HOSPITAL cfg
              LEFT JOIN TRIAJE t ON c.TRIAJE_ID = t.ID
              LEFT JOIN CITA co ON c.CITA_ORIGEN_ID = co.ID
              LEFT JOIN ATENCION atn ON atn.CITA_ID = c.ID
             WHERE c.PUBLIC_ID = p_public_id;
    END get_appointment_detail;

    PROCEDURE get_appointment_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, SLOT_ID, PACIENTE_ID, TRIAJE_ID, CITA_ORIGEN_ID,
                   ESTADO, MOTIVO_CANCELACION, CREATED_AT, UPDATED_AT
              FROM CITA
             WHERE PUBLIC_ID = p_public_id;
    END get_appointment_by_public_id;

    PROCEDURE get_appointment_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, SLOT_ID, PACIENTE_ID, TRIAJE_ID, CITA_ORIGEN_ID,
                   ESTADO, MOTIVO_CANCELACION, CREATED_AT, UPDATED_AT
              FROM CITA
             WHERE ID = p_id;
    END get_appointment_by_id;

    PROCEDURE update_appointment_status(p_id IN NUMBER, p_status IN VARCHAR2, p_cancel_reason IN VARCHAR2) IS
    BEGIN
        UPDATE CITA
           SET ESTADO = p_status,
               MOTIVO_CANCELACION = p_cancel_reason,
               UPDATED_AT = CURRENT_TIMESTAMP
         WHERE ID = p_id;
    END update_appointment_status;

    FUNCTION count_active_appointments_in_slot(p_slot_id IN NUMBER) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM CITA
         WHERE SLOT_ID = p_slot_id
           AND ESTADO IN ('PROGRAMADA', 'CONFIRMADA');
        RETURN v_count;
    END count_active_appointments_in_slot;

    PROCEDURE list_professional_agenda(p_professional_id IN NUMBER, p_from IN TIMESTAMP WITH TIME ZONE,
                                       p_to IN TIMESTAMP WITH TIME ZONE, p_status IN VARCHAR2,
                                       p_offset IN NUMBER, p_limit IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT c.PUBLIC_ID AS CITA_PUBLIC_ID,
                   s.PUBLIC_ID AS SLOT_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   p.NOMBRES || ' ' || p.APELLIDOS AS PACIENTE_NOMBRE,
                   pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   pr.NOMBRES || ' ' || pr.APELLIDOS AS PROFESIONAL_NOMBRE,
                   e.PUBLIC_ID AS ESPECIALIDAD_PUBLIC_ID,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   cfg.PUBLIC_ID AS SEDE_PUBLIC_ID,
                   cfg.RAZON_SOCIAL AS SEDE_NOMBRE,
                   cfg.DIRECCION AS SEDE_DIRECCION,
                   s.FECHA_HORA_INICIO,
                   s.FECHA_HORA_FIN,
                   s.MODALIDAD,
                   c.ESTADO,
                   t.PUBLIC_ID AS TRIAJE_PUBLIC_ID,
                   co.PUBLIC_ID AS CITA_ORIGEN_PUBLIC_ID,
                   t.NIVEL_PRIORIDAD AS TRIAJE_NIVEL,
                   COALESCE(t.OBSERVACIONES, 'Consulta médica general') AS MOTIVO_CONSULTA,
                   c.CREATED_AT,
                   (CASE WHEN atn.ID IS NOT NULL THEN 1 ELSE 0 END) AS TIENE_ATENCION,
                   atn.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   atn.ESTADO AS ATENCION_ESTADO
              FROM CITA c
              JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
              JOIN PACIENTE p ON c.PACIENTE_ID = p.ID
              JOIN PROFESIONAL pr ON s.PROFESIONAL_ID = pr.ID
              JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
              CROSS JOIN CONFIGURACION_HOSPITAL cfg
              LEFT JOIN TRIAJE t ON c.TRIAJE_ID = t.ID
              LEFT JOIN CITA co ON c.CITA_ORIGEN_ID = co.ID
              LEFT JOIN ATENCION atn ON atn.CITA_ID = c.ID
             WHERE s.PROFESIONAL_ID = p_professional_id
               AND (p_from IS NULL OR s.FECHA_HORA_INICIO >= p_from)
               AND (p_to IS NULL OR s.FECHA_HORA_INICIO <= p_to)
               AND (p_status IS NULL OR c.ESTADO = p_status)
             ORDER BY s.FECHA_HORA_INICIO ASC, c.ID ASC
            OFFSET p_offset ROWS FETCH NEXT p_limit ROWS ONLY;
    END list_professional_agenda;

    FUNCTION count_professional_agenda(p_professional_id IN NUMBER, p_from IN TIMESTAMP WITH TIME ZONE,
                                       p_to IN TIMESTAMP WITH TIME ZONE, p_status IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM CITA c
          JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
         WHERE s.PROFESIONAL_ID = p_professional_id
           AND (p_from IS NULL OR s.FECHA_HORA_INICIO >= p_from)
           AND (p_to IS NULL OR s.FECHA_HORA_INICIO <= p_to)
           AND (p_status IS NULL OR c.ESTADO = p_status);
        RETURN v_count;
    END count_professional_agenda;

    FUNCTION count_future_active_appointments(p_professional_id IN NUMBER, p_patient_id IN NUMBER,
                                              p_now IN TIMESTAMP WITH TIME ZONE) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM CITA c
          JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
         WHERE c.PACIENTE_ID = p_patient_id
           AND s.PROFESIONAL_ID = p_professional_id
           AND c.ESTADO IN ('PROGRAMADA', 'CONFIRMADA')
           AND s.FECHA_HORA_INICIO > p_now;
        RETURN v_count;
    END count_future_active_appointments;

    PROCEDURE list_patient_appointments(p_patient_id IN NUMBER, p_offset IN NUMBER, p_limit IN NUMBER,
                                        o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT c.PUBLIC_ID AS CITA_PUBLIC_ID,
                   s.PUBLIC_ID AS SLOT_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   p.NOMBRES || ' ' || p.APELLIDOS AS PACIENTE_NOMBRE,
                   pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   pr.NOMBRES || ' ' || pr.APELLIDOS AS PROFESIONAL_NOMBRE,
                   e.PUBLIC_ID AS ESPECIALIDAD_PUBLIC_ID,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   cfg.PUBLIC_ID AS SEDE_PUBLIC_ID,
                   cfg.RAZON_SOCIAL AS SEDE_NOMBRE,
                   cfg.DIRECCION AS SEDE_DIRECCION,
                   s.FECHA_HORA_INICIO,
                   s.FECHA_HORA_FIN,
                   s.MODALIDAD,
                   c.ESTADO,
                   t.PUBLIC_ID AS TRIAJE_PUBLIC_ID,
                   co.PUBLIC_ID AS CITA_ORIGEN_PUBLIC_ID,
                   t.NIVEL_PRIORIDAD AS TRIAJE_NIVEL,
                   COALESCE(t.OBSERVACIONES, 'Consulta médica general') AS MOTIVO_CONSULTA,
                   c.CREATED_AT,
                   (CASE WHEN atn.ID IS NOT NULL THEN 1 ELSE 0 END) AS TIENE_ATENCION,
                   atn.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   atn.ESTADO AS ATENCION_ESTADO
              FROM CITA c
              JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
              JOIN PACIENTE p ON c.PACIENTE_ID = p.ID
              JOIN PROFESIONAL pr ON s.PROFESIONAL_ID = pr.ID
              JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
              CROSS JOIN CONFIGURACION_HOSPITAL cfg
              LEFT JOIN TRIAJE t ON c.TRIAJE_ID = t.ID
              LEFT JOIN CITA co ON c.CITA_ORIGEN_ID = co.ID
              LEFT JOIN ATENCION atn ON atn.CITA_ID = c.ID
             WHERE c.PACIENTE_ID = p_patient_id
             ORDER BY s.FECHA_HORA_INICIO DESC, c.ID DESC
            OFFSET p_offset ROWS FETCH NEXT p_limit ROWS ONLY;
    END list_patient_appointments;

    FUNCTION count_patient_appointments(p_patient_id IN NUMBER) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM CITA WHERE PACIENTE_ID = p_patient_id;
        RETURN v_count;
    END count_patient_appointments;

    PROCEDURE list_admin_appointments(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                      p_professional_public_id IN VARCHAR2, p_specialty_public_id IN VARCHAR2,
                                      p_status IN VARCHAR2, p_offset IN NUMBER, p_limit IN NUMBER,
                                      o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        IF p_limit IS NULL THEN
            OPEN o_rows FOR
                SELECT c.PUBLIC_ID AS CITA_PUBLIC_ID,
                       p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                       p.NOMBRES || ' ' || p.APELLIDOS AS PACIENTE_NOMBRE,
                       p.TIPO_DOCUMENTO AS PACIENTE_DOC_TIPO,
                       p.NUMERO_DOCUMENTO AS PACIENTE_DOC_NUM,
                       p.TELEFONO AS PACIENTE_TELEFONO,
                       up.EMAIL AS PACIENTE_EMAIL,
                       pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                       pr.NOMBRES || ' ' || pr.APELLIDOS AS PROFESIONAL_NOMBRE,
                       pr.REGISTRO_MEDICO AS REGISTRO_MEDICO,
                       e.PUBLIC_ID AS ESPECIALIDAD_PUBLIC_ID,
                       e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                       cfg.PUBLIC_ID AS SEDE_PUBLIC_ID,
                       cfg.RAZON_SOCIAL AS SEDE_NOMBRE,
                       cfg.CIUDAD AS SEDE_CIUDAD,
                       s.FECHA_HORA_INICIO,
                       s.FECHA_HORA_FIN,
                       s.MODALIDAD,
                       c.ESTADO,
                       c.MOTIVO_CANCELACION,
                       t.PUBLIC_ID AS TRIAJE_PUBLIC_ID,
                       t.NIVEL_PRIORIDAD AS TRIAJE_NIVEL,
                       COALESCE(t.OBSERVACIONES, 'Consulta médica general') AS MOTIVO_CONSULTA,
                       c.CREATED_AT
                  FROM CITA c
                  JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
                  JOIN PACIENTE p ON c.PACIENTE_ID = p.ID
                  JOIN USUARIO up ON p.USUARIO_ID = up.ID
                  JOIN PROFESIONAL pr ON s.PROFESIONAL_ID = pr.ID
                  JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
                  CROSS JOIN CONFIGURACION_HOSPITAL cfg
                  LEFT JOIN TRIAJE t ON c.TRIAJE_ID = t.ID
                 WHERE (p_from IS NULL OR s.FECHA_HORA_INICIO >= p_from)
                   AND (p_to IS NULL OR s.FECHA_HORA_INICIO <= p_to)
                   AND (p_professional_public_id IS NULL OR pr.PUBLIC_ID = p_professional_public_id)
                   AND (p_specialty_public_id IS NULL OR e.PUBLIC_ID = p_specialty_public_id)
                   AND (p_status IS NULL OR c.ESTADO = p_status)
                 ORDER BY s.FECHA_HORA_INICIO ASC, c.ID ASC;
        ELSE
            OPEN o_rows FOR
                SELECT c.PUBLIC_ID AS CITA_PUBLIC_ID,
                       p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                       p.NOMBRES || ' ' || p.APELLIDOS AS PACIENTE_NOMBRE,
                       p.TIPO_DOCUMENTO AS PACIENTE_DOC_TIPO,
                       p.NUMERO_DOCUMENTO AS PACIENTE_DOC_NUM,
                       p.TELEFONO AS PACIENTE_TELEFONO,
                       up.EMAIL AS PACIENTE_EMAIL,
                       pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                       pr.NOMBRES || ' ' || pr.APELLIDOS AS PROFESIONAL_NOMBRE,
                       pr.REGISTRO_MEDICO AS REGISTRO_MEDICO,
                       e.PUBLIC_ID AS ESPECIALIDAD_PUBLIC_ID,
                       e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                       cfg.PUBLIC_ID AS SEDE_PUBLIC_ID,
                       cfg.RAZON_SOCIAL AS SEDE_NOMBRE,
                       cfg.CIUDAD AS SEDE_CIUDAD,
                       s.FECHA_HORA_INICIO,
                       s.FECHA_HORA_FIN,
                       s.MODALIDAD,
                       c.ESTADO,
                       c.MOTIVO_CANCELACION,
                       t.PUBLIC_ID AS TRIAJE_PUBLIC_ID,
                       t.NIVEL_PRIORIDAD AS TRIAJE_NIVEL,
                       COALESCE(t.OBSERVACIONES, 'Consulta médica general') AS MOTIVO_CONSULTA,
                       c.CREATED_AT
                  FROM CITA c
                  JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
                  JOIN PACIENTE p ON c.PACIENTE_ID = p.ID
                  JOIN USUARIO up ON p.USUARIO_ID = up.ID
                  JOIN PROFESIONAL pr ON s.PROFESIONAL_ID = pr.ID
                  JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
                  CROSS JOIN CONFIGURACION_HOSPITAL cfg
                  LEFT JOIN TRIAJE t ON c.TRIAJE_ID = t.ID
                 WHERE (p_from IS NULL OR s.FECHA_HORA_INICIO >= p_from)
                   AND (p_to IS NULL OR s.FECHA_HORA_INICIO <= p_to)
                   AND (p_professional_public_id IS NULL OR pr.PUBLIC_ID = p_professional_public_id)
                   AND (p_specialty_public_id IS NULL OR e.PUBLIC_ID = p_specialty_public_id)
                   AND (p_status IS NULL OR c.ESTADO = p_status)
                 ORDER BY s.FECHA_HORA_INICIO ASC, c.ID ASC
                OFFSET COALESCE(p_offset, 0) ROWS FETCH NEXT p_limit ROWS ONLY;
        END IF;
    END list_admin_appointments;

    FUNCTION count_admin_appointments(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                      p_professional_public_id IN VARCHAR2, p_specialty_public_id IN VARCHAR2,
                                      p_status IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM CITA c
          JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
          JOIN PACIENTE p ON c.PACIENTE_ID = p.ID
          JOIN PROFESIONAL pr ON s.PROFESIONAL_ID = pr.ID
          JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
         WHERE (p_from IS NULL OR s.FECHA_HORA_INICIO >= p_from)
           AND (p_to IS NULL OR s.FECHA_HORA_INICIO <= p_to)
           AND (p_professional_public_id IS NULL OR pr.PUBLIC_ID = p_professional_public_id)
           AND (p_specialty_public_id IS NULL OR e.PUBLIC_ID = p_specialty_public_id)
           AND (p_status IS NULL OR c.ESTADO = p_status);
        RETURN v_count;
    END count_admin_appointments;

    -- ---------------------------------------------------------------- Disponibilidad
    PROCEDURE create_slot(p_public_id IN VARCHAR2, p_professional_id IN NUMBER, p_specialty_id IN NUMBER,
                          p_start_at IN TIMESTAMP WITH TIME ZONE, p_end_at IN TIMESTAMP WITH TIME ZONE,
                          p_modality IN VARCHAR2, p_status IN VARCHAR2, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO DISPONIBILIDAD_SLOT (PUBLIC_ID, PROFESIONAL_ID, ESPECIALIDAD_ID,
                                         FECHA_HORA_INICIO, FECHA_HORA_FIN, MODALIDAD, ESTADO)
        VALUES (p_public_id, p_professional_id, p_specialty_id,
                p_start_at, p_end_at, p_modality, p_status)
        RETURNING ID INTO o_id;
    END create_slot;

    PROCEDURE get_slot_detail(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT s.PUBLIC_ID,
                   p.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   p.NOMBRES || ' ' || p.APELLIDOS AS PROFESIONAL_NOMBRE,
                   cfg.PUBLIC_ID AS SEDE_PUBLIC_ID,
                   cfg.RAZON_SOCIAL AS SEDE_NOMBRE,
                   e.PUBLIC_ID AS ESPECIALIDAD_PUBLIC_ID,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   s.FECHA_HORA_INICIO,
                   s.FECHA_HORA_FIN,
                   s.MODALIDAD,
                   s.ESTADO
              FROM DISPONIBILIDAD_SLOT s
              JOIN PROFESIONAL p ON s.PROFESIONAL_ID = p.ID
              CROSS JOIN CONFIGURACION_HOSPITAL cfg
              JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
             WHERE s.PUBLIC_ID = p_public_id;
    END get_slot_detail;

    PROCEDURE get_slot_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, PROFESIONAL_ID, ESPECIALIDAD_ID,
                   FECHA_HORA_INICIO, FECHA_HORA_FIN, MODALIDAD, ESTADO
              FROM DISPONIBILIDAD_SLOT
             WHERE ID = p_id;
    END get_slot_by_id;

    PROCEDURE get_slot_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, PROFESIONAL_ID, ESPECIALIDAD_ID,
                   FECHA_HORA_INICIO, FECHA_HORA_FIN, MODALIDAD, ESTADO
              FROM DISPONIBILIDAD_SLOT
             WHERE PUBLIC_ID = p_public_id;
    END get_slot_by_public_id;

    FUNCTION count_overlapping_slots(p_professional_id IN NUMBER, p_start_at IN TIMESTAMP WITH TIME ZONE,
                                     p_end_at IN TIMESTAMP WITH TIME ZONE) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM DISPONIBILIDAD_SLOT
         WHERE PROFESIONAL_ID = p_professional_id
           AND FECHA_HORA_INICIO < p_end_at
           AND FECHA_HORA_FIN > p_start_at;
        RETURN v_count;
    END count_overlapping_slots;

    PROCEDURE update_slot_status(p_public_id IN VARCHAR2, p_status IN VARCHAR2) IS
    BEGIN
        UPDATE DISPONIBILIDAD_SLOT SET ESTADO = p_status WHERE PUBLIC_ID = p_public_id;
    END update_slot_status;

    PROCEDURE delete_free_slot(p_public_id IN VARCHAR2, o_deleted OUT NUMBER) IS
    BEGIN
        DELETE FROM DISPONIBILIDAD_SLOT WHERE PUBLIC_ID = p_public_id AND ESTADO = 'LIBRE';
        o_deleted := SQL%ROWCOUNT;
    END delete_free_slot;

    PROCEDURE list_slots(p_professional_public_id IN VARCHAR2, p_site_public_id IN VARCHAR2,
                         p_specialty_public_id IN VARCHAR2, p_from IN TIMESTAMP WITH TIME ZONE,
                         p_to IN TIMESTAMP WITH TIME ZONE, p_status IN VARCHAR2, p_offset IN NUMBER,
                         p_limit IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT s.PUBLIC_ID,
                   p.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   p.NOMBRES || ' ' || p.APELLIDOS AS PROFESIONAL_NOMBRE,
                   cfg.PUBLIC_ID AS SEDE_PUBLIC_ID,
                   cfg.RAZON_SOCIAL AS SEDE_NOMBRE,
                   e.PUBLIC_ID AS ESPECIALIDAD_PUBLIC_ID,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   s.FECHA_HORA_INICIO,
                   s.FECHA_HORA_FIN,
                   s.MODALIDAD,
                   s.ESTADO
              FROM DISPONIBILIDAD_SLOT s
              JOIN PROFESIONAL p ON s.PROFESIONAL_ID = p.ID
              CROSS JOIN CONFIGURACION_HOSPITAL cfg
              JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
             WHERE (p_professional_public_id IS NULL OR p.PUBLIC_ID = p_professional_public_id)
               AND (p_site_public_id IS NULL OR cfg.PUBLIC_ID = p_site_public_id)
               AND (p_specialty_public_id IS NULL OR e.PUBLIC_ID = p_specialty_public_id)
               AND (p_from IS NULL OR s.FECHA_HORA_INICIO >= p_from)
               AND (p_to IS NULL OR s.FECHA_HORA_INICIO <= p_to)
               AND (p_status IS NULL OR s.ESTADO = p_status)
             ORDER BY s.FECHA_HORA_INICIO ASC, s.ID ASC
            OFFSET p_offset ROWS FETCH NEXT p_limit ROWS ONLY;
    END list_slots;

    FUNCTION count_slots(p_professional_public_id IN VARCHAR2, p_site_public_id IN VARCHAR2,
                         p_specialty_public_id IN VARCHAR2, p_from IN TIMESTAMP WITH TIME ZONE,
                         p_to IN TIMESTAMP WITH TIME ZONE, p_status IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM DISPONIBILIDAD_SLOT s
          JOIN PROFESIONAL p ON s.PROFESIONAL_ID = p.ID
          CROSS JOIN CONFIGURACION_HOSPITAL cfg
          JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
         WHERE (p_professional_public_id IS NULL OR p.PUBLIC_ID = p_professional_public_id)
           AND (p_site_public_id IS NULL OR cfg.PUBLIC_ID = p_site_public_id)
           AND (p_specialty_public_id IS NULL OR e.PUBLIC_ID = p_specialty_public_id)
           AND (p_from IS NULL OR s.FECHA_HORA_INICIO >= p_from)
           AND (p_to IS NULL OR s.FECHA_HORA_INICIO <= p_to)
           AND (p_status IS NULL OR s.ESTADO = p_status);
        RETURN v_count;
    END count_slots;

    PROCEDURE list_available_slots(p_specialty_public_id IN VARCHAR2, p_professional_public_id IN VARCHAR2,
                                   p_site_public_id IN VARCHAR2, p_modality IN VARCHAR2,
                                   p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                   p_offset IN NUMBER, p_limit IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT s.PUBLIC_ID AS SLOT_PUBLIC_ID,
                   p.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   p.NOMBRES || ' ' || p.APELLIDOS AS PROFESIONAL_NOMBRE,
                   e.PUBLIC_ID AS ESPECIALIDAD_PUBLIC_ID,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   cfg.PUBLIC_ID AS SEDE_PUBLIC_ID,
                   cfg.RAZON_SOCIAL AS SEDE_NOMBRE,
                   cfg.DIRECCION AS SEDE_DIRECCION,
                   cfg.CIUDAD AS SEDE_CIUDAD,
                   s.FECHA_HORA_INICIO,
                   s.FECHA_HORA_FIN,
                   s.MODALIDAD
              FROM DISPONIBILIDAD_SLOT s
              JOIN PROFESIONAL p ON s.PROFESIONAL_ID = p.ID
              JOIN USUARIO u ON p.USUARIO_ID = u.ID
              CROSS JOIN CONFIGURACION_HOSPITAL cfg
              JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
             WHERE s.ESTADO = 'LIBRE'
               AND u.ESTADO = 'ACTIVO'
               AND e.ESTADO = 'ACTIVO'
               AND (p_specialty_public_id IS NULL OR e.PUBLIC_ID = p_specialty_public_id)
               AND (p_professional_public_id IS NULL OR p.PUBLIC_ID = p_professional_public_id)
               AND (p_site_public_id IS NULL OR cfg.PUBLIC_ID = p_site_public_id)
               AND (p_modality IS NULL OR s.MODALIDAD = p_modality)
               AND (p_from IS NULL OR s.FECHA_HORA_INICIO >= p_from)
               AND (p_to IS NULL OR s.FECHA_HORA_INICIO <= p_to)
             ORDER BY s.FECHA_HORA_INICIO ASC, s.ID ASC
            OFFSET p_offset ROWS FETCH NEXT p_limit ROWS ONLY;
    END list_available_slots;

    FUNCTION count_available_slots(p_specialty_public_id IN VARCHAR2, p_professional_public_id IN VARCHAR2,
                                   p_site_public_id IN VARCHAR2, p_modality IN VARCHAR2,
                                   p_from IN TIMESTAMP WITH TIME ZONE,
                                   p_to IN TIMESTAMP WITH TIME ZONE) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM DISPONIBILIDAD_SLOT s
          JOIN PROFESIONAL p ON s.PROFESIONAL_ID = p.ID
          JOIN USUARIO u ON p.USUARIO_ID = u.ID
          CROSS JOIN CONFIGURACION_HOSPITAL cfg
          JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
         WHERE s.ESTADO = 'LIBRE'
           AND u.ESTADO = 'ACTIVO'
           AND e.ESTADO = 'ACTIVO'
           AND (p_specialty_public_id IS NULL OR e.PUBLIC_ID = p_specialty_public_id)
           AND (p_professional_public_id IS NULL OR p.PUBLIC_ID = p_professional_public_id)
           AND (p_site_public_id IS NULL OR cfg.PUBLIC_ID = p_site_public_id)
           AND (p_modality IS NULL OR s.MODALIDAD = p_modality)
           AND (p_from IS NULL OR s.FECHA_HORA_INICIO >= p_from)
           AND (p_to IS NULL OR s.FECHA_HORA_INICIO <= p_to);
        RETURN v_count;
    END count_available_slots;

    PROCEDURE reserve_slot(p_slot_id IN NUMBER, o_updated OUT NUMBER) IS
    BEGIN
        UPDATE DISPONIBILIDAD_SLOT
           SET ESTADO = 'OCUPADO'
         WHERE ID = p_slot_id
           AND ESTADO = 'LIBRE';
        o_updated := SQL%ROWCOUNT;
    END reserve_slot;

    PROCEDURE release_slot(p_slot_id IN NUMBER, o_updated OUT NUMBER) IS
    BEGIN
        UPDATE DISPONIBILIDAD_SLOT
           SET ESTADO = 'LIBRE'
         WHERE ID = p_slot_id
           AND ESTADO = 'OCUPADO';
        o_updated := SQL%ROWCOUNT;
    END release_slot;

    -- ---------------------------------------------------------------- Ausencias médicas
    PROCEDURE create_absence(p_public_id IN VARCHAR2, p_professional_id IN NUMBER,
                             p_start_at IN TIMESTAMP WITH TIME ZONE, p_end_at IN TIMESTAMP WITH TIME ZONE,
                             p_reason IN VARCHAR2, p_status IN VARCHAR2, p_registered_by_user_id IN NUMBER,
                             o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO AUSENCIA_MEDICA (PUBLIC_ID, PROFESIONAL_ID, FECHA_INICIO, FECHA_FIN,
                                     MOTIVO, ESTADO, REGISTRADO_POR_USUARIO_ID)
        VALUES (p_public_id, p_professional_id, p_start_at, p_end_at, p_reason, p_status,
                p_registered_by_user_id)
        RETURNING ID INTO o_id;
    END create_absence;

    PROCEDURE get_absence_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM AUSENCIA_MEDICA WHERE ID = p_id;
    END get_absence_by_id;

    FUNCTION count_overlapping_absences(p_professional_id IN NUMBER, p_start_at IN TIMESTAMP WITH TIME ZONE,
                                        p_end_at IN TIMESTAMP WITH TIME ZONE) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM AUSENCIA_MEDICA
         WHERE PROFESIONAL_ID = p_professional_id
           AND ESTADO = 'ACTIVA'
           AND NOT (FECHA_FIN <= p_start_at OR FECHA_INICIO >= p_end_at);
        RETURN v_count;
    END count_overlapping_absences;

    PROCEDURE list_absences_by_professional(p_professional_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT a.PUBLIC_ID,
                   p.PUBLIC_ID AS PROF_PUB_ID,
                   p.NOMBRES AS PROF_NOMBRE,
                   p.APELLIDOS AS PROF_APELLIDO,
                   a.FECHA_INICIO,
                   a.FECHA_FIN,
                   a.MOTIVO,
                   a.ESTADO
              FROM AUSENCIA_MEDICA a
              JOIN PROFESIONAL p ON a.PROFESIONAL_ID = p.ID
             WHERE a.PROFESIONAL_ID = p_professional_id
             ORDER BY a.FECHA_INICIO DESC;
    END list_absences_by_professional;

    -- ---------------------------------------------------------------- Recordatorios
    PROCEDURE create_reminder(p_public_id IN VARCHAR2, p_appointment_id IN NUMBER, p_patient_id IN NUMBER,
                              p_type IN VARCHAR2, p_channel IN VARCHAR2, p_recipient IN VARCHAR2,
                              p_delivery_status IN VARCHAR2, p_error_message IN VARCHAR2,
                              p_sent_at IN TIMESTAMP WITH TIME ZONE, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO RECORDATORIO_CITA (PUBLIC_ID, CITA_ID, PACIENTE_ID, TIPO, CANAL,
                                       DESTINATARIO, ESTADO_ENVIO, ERROR_MENSAJE, ENVIADO_AT)
        VALUES (p_public_id, p_appointment_id, p_patient_id, p_type, p_channel, p_recipient,
                p_delivery_status, p_error_message, p_sent_at)
        RETURNING ID INTO o_id;
    END create_reminder;

    PROCEDURE list_reminders_by_appointment(p_appointment_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ID, PUBLIC_ID, CITA_ID, PACIENTE_ID, TIPO, CANAL,
                   DESTINATARIO, ESTADO_ENVIO, ERROR_MENSAJE, ENVIADO_AT
              FROM RECORDATORIO_CITA
             WHERE CITA_ID = p_appointment_id
             ORDER BY ID ASC;
    END list_reminders_by_appointment;

    FUNCTION count_reminders_by_type(p_appointment_id IN NUMBER, p_type IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM RECORDATORIO_CITA
         WHERE CITA_ID = p_appointment_id
           AND TIPO = p_type;
        RETURN v_count;
    END count_reminders_by_type;
END PKG_SCHEDULING;
/
