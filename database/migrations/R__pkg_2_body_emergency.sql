-- =============================================================================
-- MediTriaje - R__pkg_2_body_emergency: cuerpo de PKG_EMERGENCY
-- Repetible. Sin COMMIT ni ROLLBACK (la transacción pertenece a la capa de aplicación).
-- =============================================================================
CREATE OR REPLACE PACKAGE BODY PKG_EMERGENCY AS

    -- ---------------------------------------------------------------- Episodios
    PROCEDURE create_episode(p_public_id IN VARCHAR2, p_patient_id IN NUMBER, p_type IN VARCHAR2,
                             p_status IN VARCHAR2, p_admitted_at IN TIMESTAMP WITH TIME ZONE,
                             p_reason IN VARCHAR2, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO EPISODIO_ATENCION (PUBLIC_ID, PACIENTE_ID, TIPO, ESTADO, INGRESO_AT, MOTIVO_INGRESO)
        VALUES (p_public_id, p_patient_id, p_type, p_status, p_admitted_at, p_reason)
        RETURNING ID INTO o_id;
    END create_episode;

    PROCEDURE get_episode_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM EPISODIO_ATENCION WHERE ID = p_id;
    END get_episode_by_id;

    PROCEDURE get_episode_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM EPISODIO_ATENCION WHERE PUBLIC_ID = p_public_id;
    END get_episode_by_public_id;

    PROCEDURE update_episode_status(p_id IN NUMBER, p_status IN VARCHAR2, o_updated OUT NUMBER) IS
    BEGIN
        UPDATE EPISODIO_ATENCION SET ESTADO = p_status, UPDATED_AT = CURRENT_TIMESTAMP WHERE ID = p_id;
        o_updated := SQL%ROWCOUNT;
    END update_episode_status;

    PROCEDURE link_patient(p_id IN NUMBER, p_patient_id IN NUMBER, o_updated OUT NUMBER) IS
    BEGIN
        UPDATE EPISODIO_ATENCION SET PACIENTE_ID = p_patient_id, UPDATED_AT = CURRENT_TIMESTAMP WHERE ID = p_id;
        o_updated := SQL%ROWCOUNT;
    END link_patient;

    PROCEDURE close_episode(p_id IN NUMBER, p_discharged_at IN TIMESTAMP WITH TIME ZONE, o_updated OUT NUMBER) IS
    BEGIN
        UPDATE EPISODIO_ATENCION
           SET ESTADO = 'EGRESADO', EGRESO_AT = p_discharged_at, UPDATED_AT = CURRENT_TIMESTAMP
         WHERE ID = p_id;
        o_updated := SQL%ROWCOUNT;
    END close_episode;

    PROCEDURE get_episode_detail(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT e.PUBLIC_ID AS EPISODIO_PUB_ID,
                   p.PUBLIC_ID AS PACIENTE_PUB_ID,
                   CASE WHEN p.ID IS NOT NULL THEN (p.NOMBRES || ' ' || p.APELLIDOS) ELSE NULL END AS PACIENTE_NOMBRE,
                   cfg.PUBLIC_ID AS SEDE_PUB_ID,
                   cfg.RAZON_SOCIAL AS SEDE_NOMBRE,
                   e.TIPO,
                   e.ESTADO,
                   ip.CODIGO_PROVISIONAL,
                   ip.ESTADO AS IDENTIDAD_ESTADO,
                   iu.MEDIO_LLEGADA,
                   iu.MOTIVO_RESUMIDO,
                   (SELECT vt.NIVEL FROM VALORACION_TRIAJE vt WHERE vt.EPISODIO_ID = e.ID
                     ORDER BY vt.VERSION DESC FETCH FIRST 1 ROWS ONLY) AS NIVEL_TRIAJE,
                   (SELECT prof.NOMBRES || ' ' || prof.APELLIDOS FROM ASIGNACION_ASISTENCIAL aa
                      JOIN PROFESIONAL prof ON aa.PROFESIONAL_ID = prof.ID
                     WHERE aa.EPISODIO_ID = e.ID AND aa.ACTIVO = 1 AND aa.FUNCION = 'MEDICO_TRATANTE'
                     FETCH FIRST 1 ROWS ONLY) AS MEDICO_TRATANTE_NOMBRE,
                   e.INGRESO_AT,
                   e.EGRESO_AT,
                   e.CREADO_AT
              FROM EPISODIO_ATENCION e
              CROSS JOIN CONFIGURACION_HOSPITAL cfg
              LEFT JOIN PACIENTE p ON e.PACIENTE_ID = p.ID
              LEFT JOIN INGRESO_URGENCIA iu ON e.ID = iu.EPISODIO_ID
              LEFT JOIN IDENTIDAD_PROVISIONAL ip ON e.ID = ip.EPISODIO_ID
             WHERE e.PUBLIC_ID = p_public_id;
    END get_episode_detail;

    PROCEDURE list_emergency_queue(p_site_id IN NUMBER, p_status IN VARCHAR2, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        -- Orden de clasificación clínica según la Resolución 5596 de 2015.
        OPEN o_rows FOR
            SELECT e.PUBLIC_ID AS EPISODIO_PUB_ID,
                   p.PUBLIC_ID AS PACIENTE_PUB_ID,
                   CASE
                     WHEN p.ID IS NOT NULL THEN (p.NOMBRES || ' ' || p.APELLIDOS)
                     WHEN ip.CODIGO_PROVISIONAL IS NOT NULL THEN ('NN (' || ip.CODIGO_PROVISIONAL || ')')
                     ELSE 'Paciente no identificado'
                   END AS IDENTIFICADOR_VISIBLE,
                   ip.CODIGO_PROVISIONAL,
                   cfg.PUBLIC_ID AS SEDE_PUB_ID,
                   cfg.RAZON_SOCIAL AS SEDE_NOMBRE,
                   e.TIPO,
                   e.ESTADO,
                   COALESCE((SELECT vt.NIVEL FROM VALORACION_TRIAJE vt WHERE vt.EPISODIO_ID = e.ID
                              ORDER BY vt.VERSION DESC FETCH FIRST 1 ROWS ONLY), 'PENDIENTE_VALORACION') AS NIVEL_TRIAJE,
                   COALESCE(iu.MOTIVO_RESUMIDO, e.MOTIVO_INGRESO) AS MOTIVO_LLEGADA,
                   ROUND((CAST(CURRENT_TIMESTAMP AS DATE) - CAST(e.INGRESO_AT AS DATE)) * 1440) AS MINUTOS_ESPERA,
                   e.INGRESO_AT,
                   (SELECT prof.NOMBRES || ' ' || prof.APELLIDOS FROM ASIGNACION_ASISTENCIAL aa
                      JOIN PROFESIONAL prof ON aa.PROFESIONAL_ID = prof.ID
                     WHERE aa.EPISODIO_ID = e.ID AND aa.ACTIVO = 1 AND aa.FUNCION = 'MEDICO_TRATANTE'
                     FETCH FIRST 1 ROWS ONLY) AS MEDICO_TRATANTE_NOMBRE
              FROM EPISODIO_ATENCION e
              CROSS JOIN CONFIGURACION_HOSPITAL cfg
              LEFT JOIN PACIENTE p ON e.PACIENTE_ID = p.ID
              LEFT JOIN INGRESO_URGENCIA iu ON e.ID = iu.EPISODIO_ID
              LEFT JOIN IDENTIDAD_PROVISIONAL ip ON e.ID = ip.EPISODIO_ID
             WHERE e.TIPO = 'URGENCIA'
               AND e.ESTADO NOT IN ('EGRESADO', 'CANCELADO')
               AND (p_site_id IS NULL OR cfg.ID = p_site_id)
               AND (p_status IS NULL OR e.ESTADO = p_status)
             ORDER BY
               CASE
                 WHEN (SELECT vt.NIVEL FROM VALORACION_TRIAJE vt WHERE vt.EPISODIO_ID = e.ID ORDER BY vt.VERSION DESC FETCH FIRST 1 ROWS ONLY) = 'I' THEN 1
                 WHEN (SELECT vt.NIVEL FROM VALORACION_TRIAJE vt WHERE vt.EPISODIO_ID = e.ID ORDER BY vt.VERSION DESC FETCH FIRST 1 ROWS ONLY) = 'II' THEN 2
                 WHEN (SELECT vt.NIVEL FROM VALORACION_TRIAJE vt WHERE vt.EPISODIO_ID = e.ID ORDER BY vt.VERSION DESC FETCH FIRST 1 ROWS ONLY) = 'III' THEN 3
                 WHEN (SELECT vt.NIVEL FROM VALORACION_TRIAJE vt WHERE vt.EPISODIO_ID = e.ID ORDER BY vt.VERSION DESC FETCH FIRST 1 ROWS ONLY) = 'IV' THEN 4
                 WHEN (SELECT vt.NIVEL FROM VALORACION_TRIAJE vt WHERE vt.EPISODIO_ID = e.ID ORDER BY vt.VERSION DESC FETCH FIRST 1 ROWS ONLY) = 'V' THEN 5
                 ELSE 6
               END ASC,
               e.INGRESO_AT ASC;
    END list_emergency_queue;

    -- ---------------------------------------------------------------- Ingresos a urgencias
    PROCEDURE create_admission(p_public_id IN VARCHAR2, p_episode_id IN NUMBER, p_arrival_mode IN VARCHAR2,
                               p_companion_name IN VARCHAR2, p_companion_contact IN VARCHAR2,
                               p_reason IN VARCHAR2, p_observations IN VARCHAR2,
                               p_registered_by_user_id IN NUMBER, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO INGRESO_URGENCIA (PUBLIC_ID, EPISODIO_ID, MEDIO_LLEGADA, ACOMPANANTE_NOMBRE,
                                      ACOMPANANTE_CONTACTO, MOTIVO_RESUMIDO, OBSERVACIONES,
                                      REGISTRADO_POR_USUARIO_ID)
        VALUES (p_public_id, p_episode_id, p_arrival_mode, p_companion_name, p_companion_contact, p_reason,
                p_observations, p_registered_by_user_id)
        RETURNING ID INTO o_id;
    END create_admission;

    PROCEDURE get_admission_by_episode(p_episode_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM INGRESO_URGENCIA WHERE EPISODIO_ID = p_episode_id;
    END get_admission_by_episode;
END PKG_EMERGENCY;
/
