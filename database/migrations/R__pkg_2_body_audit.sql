-- =============================================================================
-- MediTriaje - R__pkg_2_body_audit: cuerpo de PKG_AUDIT
-- Repetible. Sin COMMIT ni ROLLBACK (la transacción pertenece a la capa de aplicación).
-- =============================================================================
CREATE OR REPLACE PACKAGE BODY PKG_AUDIT AS

    -- -------------------------------------------------------------- AUDITORIA

    PROCEDURE insert_audit_event(p_user_id IN NUMBER, p_action IN VARCHAR2, p_resource_type IN VARCHAR2,
                                 p_resource_public_id IN VARCHAR2, p_result IN VARCHAR2, p_source_ip IN VARCHAR2) IS
    BEGIN
        INSERT INTO AUDITORIA (USUARIO_ID, ACCION, TIPO_RECURSO, RECURSO_PUBLIC_ID, RESULTADO, IP_ORIGEN)
        VALUES (p_user_id, p_action, p_resource_type, p_resource_public_id, p_result, p_source_ip);
    END insert_audit_event;

    PROCEDURE list_audit_events(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                p_action IN VARCHAR2, p_result IN VARCHAR2, p_offset IN NUMBER, p_size IN NUMBER,
                                o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT a.ID,
                   u.EMAIL AS USUARIO_EMAIL,
                   a.ACCION,
                   a.TIPO_RECURSO,
                   a.RECURSO_PUBLIC_ID,
                   a.RESULTADO,
                   a.IP_ORIGEN,
                   a.FECHA_HORA
              FROM AUDITORIA a
              LEFT JOIN USUARIO u ON a.USUARIO_ID = u.ID
             WHERE (p_from IS NULL OR a.FECHA_HORA >= p_from)
               AND (p_to IS NULL OR a.FECHA_HORA <= p_to)
               AND (p_action IS NULL OR a.ACCION = p_action)
               AND (p_result IS NULL OR a.RESULTADO = p_result)
             ORDER BY a.FECHA_HORA DESC, a.ID DESC
            OFFSET p_offset ROWS FETCH NEXT p_size ROWS ONLY;
    END list_audit_events;

    FUNCTION count_audit_events(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                p_action IN VARCHAR2, p_result IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM AUDITORIA a
         WHERE (p_from IS NULL OR a.FECHA_HORA >= p_from)
           AND (p_to IS NULL OR a.FECHA_HORA <= p_to)
           AND (p_action IS NULL OR a.ACCION = p_action)
           AND (p_result IS NULL OR a.RESULTADO = p_result);
        RETURN v_count;
    END count_audit_events;

    -- ----------------------------------------------------- ACCESO_BREAK_GLASS

    PROCEDURE create_break_glass_access(p_public_id IN VARCHAR2, p_professional_id IN NUMBER,
                                        p_patient_id IN NUMBER, p_reason IN VARCHAR2,
                                        p_expires_at IN TIMESTAMP WITH TIME ZONE,
                                        p_created_at IN TIMESTAMP WITH TIME ZONE, o_access_id OUT NUMBER) IS
    BEGIN
        INSERT INTO ACCESO_BREAK_GLASS (PUBLIC_ID, PROFESIONAL_ID, PACIENTE_ID, MOTIVO, FECHA_EXPIRACION, CREATED_AT)
        VALUES (p_public_id, p_professional_id, p_patient_id, p_reason, p_expires_at, p_created_at)
        RETURNING ID INTO o_access_id;
    END create_break_glass_access;

    FUNCTION count_active_break_glass(p_professional_id IN NUMBER, p_patient_id IN NUMBER,
                                      p_now IN TIMESTAMP WITH TIME ZONE) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM ACCESO_BREAK_GLASS
         WHERE PROFESIONAL_ID = p_professional_id AND PACIENTE_ID = p_patient_id AND FECHA_EXPIRACION > p_now;
        RETURN v_count;
    END count_active_break_glass;

    PROCEDURE get_break_glass_by_public_id(p_public_id IN VARCHAR2, o_access OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_access FOR
            SELECT ID, PUBLIC_ID, PROFESIONAL_ID, PACIENTE_ID, MOTIVO, FECHA_EXPIRACION, CREATED_AT
              FROM ACCESO_BREAK_GLASS
             WHERE PUBLIC_ID = p_public_id;
    END get_break_glass_by_public_id;

    PROCEDURE get_break_glass_detail(p_public_id IN VARCHAR2, p_now IN TIMESTAMP WITH TIME ZONE,
                                     o_access OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_access FOR
            SELECT bg.PUBLIC_ID,
                   prof.PUBLIC_ID AS PROF_PUB_ID,
                   (prof.NOMBRES || ' ' || prof.APELLIDOS) AS PROF_NOMBRE,
                   pac.PUBLIC_ID AS PAC_PUB_ID,
                   (pac.NOMBRES || ' ' || pac.APELLIDOS) AS PAC_NOMBRE,
                   bg.MOTIVO,
                   bg.FECHA_EXPIRACION,
                   bg.CREATED_AT,
                   CASE WHEN bg.FECHA_EXPIRACION > p_now THEN 1 ELSE 0 END AS ACTIVO
              FROM ACCESO_BREAK_GLASS bg
              JOIN PROFESIONAL prof ON bg.PROFESIONAL_ID = prof.ID
              JOIN PACIENTE pac ON bg.PACIENTE_ID = pac.ID
             WHERE bg.PUBLIC_ID = p_public_id;
    END get_break_glass_detail;

    PROCEDURE list_active_break_glass(p_professional_id IN NUMBER, p_now IN TIMESTAMP WITH TIME ZONE,
                                      o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT bg.PUBLIC_ID,
                   prof.PUBLIC_ID AS PROF_PUB_ID,
                   (prof.NOMBRES || ' ' || prof.APELLIDOS) AS PROF_NOMBRE,
                   pac.PUBLIC_ID AS PAC_PUB_ID,
                   (pac.NOMBRES || ' ' || pac.APELLIDOS) AS PAC_NOMBRE,
                   bg.MOTIVO,
                   bg.FECHA_EXPIRACION,
                   bg.CREATED_AT,
                   1 AS ACTIVO
              FROM ACCESO_BREAK_GLASS bg
              JOIN PROFESIONAL prof ON bg.PROFESIONAL_ID = prof.ID
              JOIN PACIENTE pac ON bg.PACIENTE_ID = pac.ID
             WHERE bg.PROFESIONAL_ID = p_professional_id AND bg.FECHA_EXPIRACION > p_now
             ORDER BY bg.FECHA_EXPIRACION DESC;
    END list_active_break_glass;
END PKG_AUDIT;
/
