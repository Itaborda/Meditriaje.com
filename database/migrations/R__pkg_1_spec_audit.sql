-- =============================================================================
-- MediTriaje - R__pkg_1_spec_audit: especificación de PKG_AUDIT
-- Repetible. Bitácora de auditoría (solo inserción y consulta, ADR-011/ADR-012/ADR-019) y accesos de
-- emergencia Break-Glass (ADR-017). Derechos del definidor; sin COMMIT ni ROLLBACK.
-- =============================================================================
CREATE OR REPLACE PACKAGE PKG_AUDIT AUTHID DEFINER AS

    -- -------------------------------------------------------------- AUDITORIA
    -- Inserta un evento de auditoría (la tabla es inmutable: no hay rutinas de actualización ni borrado).
    PROCEDURE insert_audit_event(p_user_id IN NUMBER, p_action IN VARCHAR2, p_resource_type IN VARCHAR2,
                                 p_resource_public_id IN VARCHAR2, p_result IN VARCHAR2, p_source_ip IN VARCHAR2);

    -- Página de eventos más recientes primero. Los filtros nulos no se aplican. Columnas: ID, USUARIO_EMAIL,
    -- ACCION, TIPO_RECURSO, RECURSO_PUBLIC_ID, RESULTADO, IP_ORIGEN, FECHA_HORA.
    PROCEDURE list_audit_events(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                p_action IN VARCHAR2, p_result IN VARCHAR2, p_offset IN NUMBER, p_size IN NUMBER,
                                o_rows OUT SYS_REFCURSOR);

    -- Total de eventos con los mismos filtros de list_audit_events.
    FUNCTION count_audit_events(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                p_action IN VARCHAR2, p_result IN VARCHAR2) RETURN NUMBER;

    -- ----------------------------------------------------- ACCESO_BREAK_GLASS
    -- Registra un acceso Break-Glass y devuelve su ID interno.
    PROCEDURE create_break_glass_access(p_public_id IN VARCHAR2, p_professional_id IN NUMBER,
                                        p_patient_id IN NUMBER, p_reason IN VARCHAR2,
                                        p_expires_at IN TIMESTAMP WITH TIME ZONE,
                                        p_created_at IN TIMESTAMP WITH TIME ZONE, o_access_id OUT NUMBER);

    -- Número de accesos no expirados (FECHA_EXPIRACION > p_now) del profesional sobre el paciente.
    FUNCTION count_active_break_glass(p_professional_id IN NUMBER, p_patient_id IN NUMBER,
                                      p_now IN TIMESTAMP WITH TIME ZONE) RETURN NUMBER;

    -- Acceso por identificador público (0 o 1 fila): ID, PUBLIC_ID, PROFESIONAL_ID, PACIENTE_ID, MOTIVO,
    -- FECHA_EXPIRACION, CREATED_AT.
    PROCEDURE get_break_glass_by_public_id(p_public_id IN VARCHAR2, o_access OUT SYS_REFCURSOR);

    -- Detalle del acceso con nombres (0 o 1 fila): PUBLIC_ID, PROF_PUB_ID, PROF_NOMBRE, PAC_PUB_ID, PAC_NOMBRE,
    -- MOTIVO, FECHA_EXPIRACION, CREATED_AT y ACTIVO (1 si FECHA_EXPIRACION > p_now).
    PROCEDURE get_break_glass_detail(p_public_id IN VARCHAR2, p_now IN TIMESTAMP WITH TIME ZONE,
                                     o_access OUT SYS_REFCURSOR);

    -- Accesos no expirados del profesional, del que vence más tarde al que vence antes (mismas columnas que
    -- get_break_glass_detail).
    PROCEDURE list_active_break_glass(p_professional_id IN NUMBER, p_now IN TIMESTAMP WITH TIME ZONE,
                                      o_rows OUT SYS_REFCURSOR);
END PKG_AUDIT;
/
