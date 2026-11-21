-- =============================================================================
-- MediTriaje - R__pkg_2_body_operations: cuerpo de PKG_OPERATIONS
-- Repetible. Sin COMMIT ni ROLLBACK (la transacción pertenece a la capa de aplicación).
-- =============================================================================
CREATE OR REPLACE PACKAGE BODY PKG_OPERATIONS AS

    -- ------------------------------------------------------- ALERTA_OPERATIVA

    PROCEDURE create_operational_alert(p_public_id IN VARCHAR2, p_alert_type IN VARCHAR2,
                                       p_severity IN VARCHAR2, p_message IN VARCHAR2,
                                       p_status IN VARCHAR2, o_id OUT NUMBER) IS
        v_site_id SEDE.ID%TYPE := PKG_COMMON.get_single_site_id;
    BEGIN
        INSERT INTO ALERTA_OPERATIVA (
            PUBLIC_ID, SEDE_ID, TIPO_ALERTA, NIVEL_SEVERIDAD, MENSAJE, ESTADO
        ) VALUES (
            p_public_id, v_site_id, p_alert_type, p_severity, p_message, NVL(p_status, 'ACTIVA')
        )
        RETURNING ID INTO o_id;
    END create_operational_alert;

    PROCEDURE get_operational_alert_by_id(p_id IN NUMBER, o_alert OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_alert FOR
            SELECT ID, PUBLIC_ID, SEDE_ID, TIPO_ALERTA, NIVEL_SEVERIDAD, MENSAJE, ESTADO,
                   RECONOCIDO_POR_USUARIO_ID, RECONOCIDO_AT, MOTIVO_RECONOCIMIENTO, CREADO_AT
              FROM ALERTA_OPERATIVA
             WHERE ID = p_id;
    END get_operational_alert_by_id;

    PROCEDURE get_operational_alert_by_public_id(p_public_id IN VARCHAR2, o_alert OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_alert FOR
            SELECT ID, PUBLIC_ID, SEDE_ID, TIPO_ALERTA, NIVEL_SEVERIDAD, MENSAJE, ESTADO,
                   RECONOCIDO_POR_USUARIO_ID, RECONOCIDO_AT, MOTIVO_RECONOCIMIENTO, CREADO_AT
              FROM ALERTA_OPERATIVA
             WHERE PUBLIC_ID = p_public_id;
    END get_operational_alert_by_public_id;

    PROCEDURE list_operational_alerts(p_site_id IN NUMBER, p_status IN VARCHAR2, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT a.PUBLIC_ID,
                   s.PUBLIC_ID AS SEDE_PUB_ID,
                   s.NOMBRE AS SEDE_NOMBRE,
                   a.TIPO_ALERTA,
                   a.NIVEL_SEVERIDAD,
                   a.MENSAJE,
                   a.ESTADO,
                   u.EMAIL AS RECONOCIDO_POR_EMAIL,
                   a.RECONOCIDO_AT,
                   a.MOTIVO_RECONOCIMIENTO,
                   a.CREADO_AT
              FROM ALERTA_OPERATIVA a
              JOIN SEDE s ON a.SEDE_ID = s.ID
              LEFT JOIN USUARIO u ON a.RECONOCIDO_POR_USUARIO_ID = u.ID
             WHERE (p_site_id IS NULL OR a.SEDE_ID = p_site_id)
               AND (p_status IS NULL OR a.ESTADO = p_status)
             ORDER BY a.CREADO_AT DESC, a.ID DESC;
    END list_operational_alerts;

    PROCEDURE acknowledge_operational_alert(p_id IN NUMBER, p_user_id IN NUMBER,
                                            p_acknowledged_at IN TIMESTAMP WITH TIME ZONE, p_reason IN VARCHAR2) IS
    BEGIN
        UPDATE ALERTA_OPERATIVA
           SET ESTADO = 'RECONOCIDA',
               RECONOCIDO_POR_USUARIO_ID = p_user_id,
               RECONOCIDO_AT = p_acknowledged_at,
               MOTIVO_RECONOCIMIENTO = p_reason
         WHERE ID = p_id;
    END acknowledge_operational_alert;

    -- ----------------------------------------------------- ACCESO_TEMPORAL_QR

    PROCEDURE create_qr_access(p_public_id IN VARCHAR2, p_patient_id IN NUMBER, p_token_hash IN VARCHAR2,
                               p_pin_hash IN VARCHAR2, p_include_allergies IN NUMBER,
                               p_include_medications IN NUMBER, p_include_attentions IN NUMBER,
                               p_include_contact IN NUMBER, p_max_accesses IN NUMBER,
                               p_accesses_made IN NUMBER, p_revoked IN NUMBER,
                               p_failed_pin_attempts IN NUMBER, p_expires_at IN TIMESTAMP WITH TIME ZONE,
                               o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO ACCESO_TEMPORAL_QR (
            PUBLIC_ID, PACIENTE_ID, TOKEN_HASH, PIN_HASH,
            INCLUIR_ALERGIAS, INCLUIR_MEDICAMENTOS, INCLUIR_ATENCIONES, INCLUIR_CONTACTO,
            MAX_ACCESOS, ACCESOS_REALIZADOS, REVOCADO, INTENTOS_PIN_FALLIDOS, EXPIRA_AT
        ) VALUES (
            p_public_id, p_patient_id, p_token_hash, p_pin_hash,
            p_include_allergies, p_include_medications, p_include_attentions, p_include_contact,
            p_max_accesses, p_accesses_made, p_revoked, p_failed_pin_attempts, p_expires_at
        )
        RETURNING ID INTO o_id;
    END create_qr_access;

    PROCEDURE get_qr_access_by_token_hash(p_token_hash IN VARCHAR2, o_access OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_access FOR
            SELECT ID, PUBLIC_ID, PACIENTE_ID, TOKEN_HASH, PIN_HASH,
                   INCLUIR_ALERGIAS, INCLUIR_MEDICAMENTOS, INCLUIR_ATENCIONES, INCLUIR_CONTACTO,
                   MAX_ACCESOS, ACCESOS_REALIZADOS, REVOCADO, INTENTOS_PIN_FALLIDOS, EXPIRA_AT,
                   CREATED_AT, UPDATED_AT
              FROM ACCESO_TEMPORAL_QR
             WHERE TOKEN_HASH = p_token_hash;
    END get_qr_access_by_token_hash;

    PROCEDURE get_qr_access_by_public_id(p_public_id IN VARCHAR2, o_access OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_access FOR
            SELECT ID, PUBLIC_ID, PACIENTE_ID, TOKEN_HASH, PIN_HASH,
                   INCLUIR_ALERGIAS, INCLUIR_MEDICAMENTOS, INCLUIR_ATENCIONES, INCLUIR_CONTACTO,
                   MAX_ACCESOS, ACCESOS_REALIZADOS, REVOCADO, INTENTOS_PIN_FALLIDOS, EXPIRA_AT,
                   CREATED_AT, UPDATED_AT
              FROM ACCESO_TEMPORAL_QR
             WHERE PUBLIC_ID = p_public_id;
    END get_qr_access_by_public_id;

    PROCEDURE list_qr_accesses_by_patient(p_patient_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ID, PUBLIC_ID, PACIENTE_ID, TOKEN_HASH, PIN_HASH,
                   INCLUIR_ALERGIAS, INCLUIR_MEDICAMENTOS, INCLUIR_ATENCIONES, INCLUIR_CONTACTO,
                   MAX_ACCESOS, ACCESOS_REALIZADOS, REVOCADO, INTENTOS_PIN_FALLIDOS, EXPIRA_AT,
                   CREATED_AT, UPDATED_AT
              FROM ACCESO_TEMPORAL_QR
             WHERE PACIENTE_ID = p_patient_id
             ORDER BY CREATED_AT DESC, ID DESC;
    END list_qr_accesses_by_patient;

    PROCEDURE record_qr_access(p_id IN NUMBER, p_now IN TIMESTAMP WITH TIME ZONE, o_updated OUT NUMBER) IS
    BEGIN
        UPDATE ACCESO_TEMPORAL_QR
           SET ACCESOS_REALIZADOS = ACCESOS_REALIZADOS + 1,
               UPDATED_AT = CURRENT_TIMESTAMP
         WHERE ID = p_id
           AND REVOCADO = 0
           AND ACCESOS_REALIZADOS < MAX_ACCESOS
           AND EXPIRA_AT > p_now;
        o_updated := SQL%ROWCOUNT;
    END record_qr_access;

    PROCEDURE record_failed_qr_pin(p_id IN NUMBER, o_updated OUT NUMBER) IS
    BEGIN
        UPDATE ACCESO_TEMPORAL_QR
           SET INTENTOS_PIN_FALLIDOS = INTENTOS_PIN_FALLIDOS + 1,
               ACCESOS_REALIZADOS = CASE
                   WHEN ACCESOS_REALIZADOS < MAX_ACCESOS THEN ACCESOS_REALIZADOS + 1
                   ELSE ACCESOS_REALIZADOS
               END,
               REVOCADO = CASE
                   WHEN INTENTOS_PIN_FALLIDOS + 1 >= 3 THEN 1
                   ELSE REVOCADO
               END,
               UPDATED_AT = CURRENT_TIMESTAMP
         WHERE ID = p_id;
        o_updated := SQL%ROWCOUNT;
    END record_failed_qr_pin;

    PROCEDURE revoke_qr_access(p_public_id IN VARCHAR2, p_patient_id IN NUMBER, o_updated OUT NUMBER) IS
    BEGIN
        UPDATE ACCESO_TEMPORAL_QR
           SET REVOCADO = 1,
               UPDATED_AT = CURRENT_TIMESTAMP
         WHERE PUBLIC_ID = p_public_id
           AND PACIENTE_ID = p_patient_id
           AND REVOCADO = 0;
        o_updated := SQL%ROWCOUNT;
    END revoke_qr_access;

    -- --------------------------------------- SEGUIMIENTO_INTRAHOSPITALARIO_QR

    PROCEDURE create_inpatient_qr(p_public_id IN VARCHAR2, p_episode_id IN NUMBER, p_token IN VARCHAR2,
                                  p_location_text IN VARCHAR2, p_status IN VARCHAR2,
                                  p_expires_at IN TIMESTAMP WITH TIME ZONE, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO SEGUIMIENTO_INTRAHOSPITALARIO_QR (
            PUBLIC_ID, EPISODIO_ID, CODIGO_QR_TOKEN, UBICACION_ACTUAL_TEXTO, ESTADO, EXPIRA_AT
        ) VALUES (
            p_public_id, p_episode_id, p_token, p_location_text, NVL(p_status, 'ACTIVO'), p_expires_at
        )
        RETURNING ID INTO o_id;
    END create_inpatient_qr;

    PROCEDURE get_inpatient_qr_by_id(p_id IN NUMBER, o_qr OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_qr FOR
            SELECT ID, PUBLIC_ID, EPISODIO_ID, CODIGO_QR_TOKEN, UBICACION_ACTUAL_TEXTO,
                   ESTADO, CREADO_AT, EXPIRA_AT
              FROM SEGUIMIENTO_INTRAHOSPITALARIO_QR
             WHERE ID = p_id;
    END get_inpatient_qr_by_id;

    PROCEDURE get_inpatient_qr_by_token(p_token IN VARCHAR2, o_qr OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_qr FOR
            SELECT ID, PUBLIC_ID, EPISODIO_ID, CODIGO_QR_TOKEN, UBICACION_ACTUAL_TEXTO,
                   ESTADO, CREADO_AT, EXPIRA_AT
              FROM SEGUIMIENTO_INTRAHOSPITALARIO_QR
             WHERE CODIGO_QR_TOKEN = p_token;
    END get_inpatient_qr_by_token;

    PROCEDURE get_active_inpatient_qr_by_episode(p_episode_id IN NUMBER, o_qr OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_qr FOR
            SELECT ID, PUBLIC_ID, EPISODIO_ID, CODIGO_QR_TOKEN, UBICACION_ACTUAL_TEXTO,
                   ESTADO, CREADO_AT, EXPIRA_AT
              FROM SEGUIMIENTO_INTRAHOSPITALARIO_QR
             WHERE EPISODIO_ID = p_episode_id
               AND ESTADO = 'ACTIVO'
             ORDER BY CREADO_AT DESC, ID DESC
             FETCH FIRST 1 ROWS ONLY;
    END get_active_inpatient_qr_by_episode;

    PROCEDURE update_inpatient_qr_location(p_id IN NUMBER, p_location_text IN VARCHAR2) IS
    BEGIN
        UPDATE SEGUIMIENTO_INTRAHOSPITALARIO_QR
           SET UBICACION_ACTUAL_TEXTO = p_location_text
         WHERE ID = p_id;
    END update_inpatient_qr_location;

    -- ---------------------------------------------------- DEMO SEEDER HELPERS

    FUNCTION count_users_outside_domain(p_domain IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM USUARIO
         WHERE LOWER(EMAIL) NOT LIKE '%' || LOWER(p_domain);
        RETURN v_count;
    END count_users_outside_domain;

    FUNCTION count_users_in_domain(p_domain IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM USUARIO
         WHERE LOWER(EMAIL) LIKE '%' || LOWER(p_domain);
        RETURN v_count;
    END count_users_in_domain;

    PROCEDURE shift_slot_to_past_for_demo(p_slot_public_id IN VARCHAR2) IS
    BEGIN
        UPDATE DISPONIBILIDAD_SLOT
           SET FECHA_HORA_INICIO = CURRENT_TIMESTAMP - INTERVAL '1' HOUR,
               FECHA_HORA_FIN = CURRENT_TIMESTAMP - INTERVAL '40' MINUTE
         WHERE PUBLIC_ID = p_slot_public_id;
    END shift_slot_to_past_for_demo;
END PKG_OPERATIONS;
/
