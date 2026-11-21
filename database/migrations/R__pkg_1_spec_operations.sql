-- =============================================================================
-- MediTriaje - R__pkg_1_spec_operations: especificación de PKG_OPERATIONS
-- Repetible. Alertas operativas (Fase O, ADR-027), accesos temporales QR de emergencia (ADR-010,
-- SEC-002), seguimiento intrahospitalario por QR (ADR-028) y utilidades del sembrador demo.
-- Derechos del definidor; sin COMMIT ni ROLLBACK (la transacción es de Java).
-- =============================================================================
CREATE OR REPLACE PACKAGE PKG_OPERATIONS AUTHID DEFINER AS

    -- ------------------------------------------------------- ALERTA_OPERATIVA
    -- Registra una alerta operativa en la sede única del hospital y devuelve su ID interno.
    PROCEDURE create_operational_alert(p_public_id IN VARCHAR2, p_alert_type IN VARCHAR2,
                                       p_severity IN VARCHAR2, p_message IN VARCHAR2,
                                       p_status IN VARCHAR2, o_id OUT NUMBER);

    -- Alerta operativa por ID interno (0 o 1 fila): ID, PUBLIC_ID, SEDE_ID, TIPO_ALERTA, NIVEL_SEVERIDAD,
    -- MENSAJE, ESTADO, RECONOCIDO_POR_USUARIO_ID, RECONOCIDO_AT, MOTIVO_RECONOCIMIENTO, CREADO_AT.
    PROCEDURE get_operational_alert_by_id(p_id IN NUMBER, o_alert OUT SYS_REFCURSOR);

    -- Alerta operativa por identificador público (mismas columnas que get_operational_alert_by_id).
    PROCEDURE get_operational_alert_by_public_id(p_public_id IN VARCHAR2, o_alert OUT SYS_REFCURSOR);

    -- Listado de alertas de la más reciente a la más antigua (filtros nulos no filtran). Columnas: PUBLIC_ID,
    -- SEDE_PUB_ID, SEDE_NOMBRE, TIPO_ALERTA, NIVEL_SEVERIDAD, MENSAJE, ESTADO, RECONOCIDO_POR_EMAIL,
    -- RECONOCIDO_AT, MOTIVO_RECONOCIMIENTO, CREADO_AT.
    PROCEDURE list_operational_alerts(p_site_id IN NUMBER, p_status IN VARCHAR2, o_rows OUT SYS_REFCURSOR);

    -- Marca una alerta operativa como RECONOCIDA registrando usuario, instante y motivo.
    PROCEDURE acknowledge_operational_alert(p_id IN NUMBER, p_user_id IN NUMBER,
                                            p_acknowledged_at IN TIMESTAMP WITH TIME ZONE, p_reason IN VARCHAR2);

    -- ----------------------------------------------------- ACCESO_TEMPORAL_QR
    -- Crea un acceso temporal QR y devuelve su ID interno.
    PROCEDURE create_qr_access(p_public_id IN VARCHAR2, p_patient_id IN NUMBER, p_token_hash IN VARCHAR2,
                               p_pin_hash IN VARCHAR2, p_include_allergies IN NUMBER,
                               p_include_medications IN NUMBER, p_include_attentions IN NUMBER,
                               p_include_contact IN NUMBER, p_max_accesses IN NUMBER,
                               p_accesses_made IN NUMBER, p_revoked IN NUMBER,
                               p_failed_pin_attempts IN NUMBER, p_expires_at IN TIMESTAMP WITH TIME ZONE,
                               o_id OUT NUMBER);

    -- Acceso QR por hash SHA-256 del token (0 o 1 fila): ID, PUBLIC_ID, PACIENTE_ID, TOKEN_HASH, PIN_HASH,
    -- INCLUIR_ALERGIAS, INCLUIR_MEDICAMENTOS, INCLUIR_ATENCIONES, INCLUIR_CONTACTO, MAX_ACCESOS,
    -- ACCESOS_REALIZADOS, REVOCADO, INTENTOS_PIN_FALLIDOS, EXPIRA_AT, CREATED_AT, UPDATED_AT.
    PROCEDURE get_qr_access_by_token_hash(p_token_hash IN VARCHAR2, o_access OUT SYS_REFCURSOR);

    -- Acceso QR por identificador público (mismas columnas que get_qr_access_by_token_hash).
    PROCEDURE get_qr_access_by_public_id(p_public_id IN VARCHAR2, o_access OUT SYS_REFCURSOR);

    -- Accesos QR del paciente, del más reciente al más antiguo (mismas columnas).
    PROCEDURE list_qr_accesses_by_patient(p_patient_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Incrementa atómicamente ACCESOS_REALIZADOS si está vigente y disponible; o_updated = filas actualizadas.
    PROCEDURE record_qr_access(p_id IN NUMBER, p_now IN TIMESTAMP WITH TIME ZONE, o_updated OUT NUMBER);

    -- Registra un intento fallido de PIN (consume lectura y revoca al llegar a 3); o_updated = filas actualizadas.
    PROCEDURE record_failed_qr_pin(p_id IN NUMBER, o_updated OUT NUMBER);

    -- Revoca un acceso QR vigente del paciente; o_updated = filas actualizadas.
    PROCEDURE revoke_qr_access(p_public_id IN VARCHAR2, p_patient_id IN NUMBER, o_updated OUT NUMBER);

    -- --------------------------------------- SEGUIMIENTO_INTRAHOSPITALARIO_QR
    -- Crea un token QR de seguimiento intrahospitalario y devuelve su ID interno.
    PROCEDURE create_inpatient_qr(p_public_id IN VARCHAR2, p_episode_id IN NUMBER, p_token IN VARCHAR2,
                                  p_location_text IN VARCHAR2, p_status IN VARCHAR2,
                                  p_expires_at IN TIMESTAMP WITH TIME ZONE, o_id OUT NUMBER);

    -- Seguimiento QR por ID interno (0 o 1 fila): ID, PUBLIC_ID, EPISODIO_ID, CODIGO_QR_TOKEN,
    -- UBICACION_ACTUAL_TEXTO, ESTADO, CREADO_AT, EXPIRA_AT.
    PROCEDURE get_inpatient_qr_by_id(p_id IN NUMBER, o_qr OUT SYS_REFCURSOR);

    -- Seguimiento QR por token opaco (mismas columnas que get_inpatient_qr_by_id).
    PROCEDURE get_inpatient_qr_by_token(p_token IN VARCHAR2, o_qr OUT SYS_REFCURSOR);

    -- Último seguimiento QR ACTIVO del episodio (0 o 1 fila, mismas columnas).
    PROCEDURE get_active_inpatient_qr_by_episode(p_episode_id IN NUMBER, o_qr OUT SYS_REFCURSOR);

    -- Actualiza la ubicación legible del seguimiento QR.
    PROCEDURE update_inpatient_qr_location(p_id IN NUMBER, p_location_text IN VARCHAR2);

    -- ---------------------------------------------------- DEMO SEEDER HELPERS
    -- Cuenta usuarios cuyo correo no pertenece al dominio reservado indicado.
    FUNCTION count_users_outside_domain(p_domain IN VARCHAR2) RETURN NUMBER;

    -- Cuenta usuarios cuyo correo pertenece al dominio reservado indicado.
    FUNCTION count_users_in_domain(p_domain IN VARCHAR2) RETURN NUMBER;

    -- Ajusta transitoriamente el horario de un slot al pasado para simular inasistencia en datos demo.
    PROCEDURE shift_slot_to_past_for_demo(p_slot_public_id IN VARCHAR2);
END PKG_OPERATIONS;
/
