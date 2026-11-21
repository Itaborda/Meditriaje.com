-- =============================================================================
-- MediTriaje - R__pkg_2_body_hospital: cuerpo de PKG_HOSPITAL
-- Repetible. Sin COMMIT ni ROLLBACK (la transacción pertenece a la capa de aplicación).
-- Respeta el trigger append-only de MOVIMIENTO_PACIENTE y los índices únicos de ocupación activa (V022).
-- =============================================================================
CREATE OR REPLACE PACKAGE BODY PKG_HOSPITAL AS

    -- ---------------------------------------------------------------- Áreas
    PROCEDURE create_area(p_public_id IN VARCHAR2, p_code IN VARCHAR2, p_name IN VARCHAR2,
                          p_type IN VARCHAR2, p_floor IN VARCHAR2, p_status IN VARCHAR2,
                          o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO AREA_HOSPITALARIA (PUBLIC_ID, CODIGO, NOMBRE, TIPO, PISO, ESTADO)
        VALUES (p_public_id, p_code, p_name, p_type, p_floor, p_status)
        RETURNING ID INTO o_id;
    END create_area;

    PROCEDURE get_area_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM AREA_HOSPITALARIA WHERE ID = p_id;
    END get_area_by_id;

    PROCEDURE get_area_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM AREA_HOSPITALARIA WHERE PUBLIC_ID = p_public_id;
    END get_area_by_public_id;

    PROCEDURE list_areas_by_site(p_site_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT a.ID, a.PUBLIC_ID, a.CODIGO, a.NOMBRE, a.TIPO, a.PISO, a.ESTADO, a.CREADO_AT
              FROM AREA_HOSPITALARIA a
              CROSS JOIN CONFIGURACION_HOSPITAL cfg
             WHERE (p_site_id IS NULL OR cfg.ID = p_site_id)
             ORDER BY a.CODIGO ASC;
    END list_areas_by_site;

    -- ---------------------------------------------------------------- Habitaciones y salas
    PROCEDURE create_room(p_public_id IN VARCHAR2, p_area_id IN NUMBER, p_code IN VARCHAR2,
                          p_type IN VARCHAR2, p_status IN VARCHAR2, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO HABITACION_SALA (PUBLIC_ID, AREA_ID, CODIGO, TIPO, ESTADO)
        VALUES (p_public_id, p_area_id, p_code, p_type, p_status)
        RETURNING ID INTO o_id;
    END create_room;

    PROCEDURE get_room_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM HABITACION_SALA WHERE ID = p_id;
    END get_room_by_id;

    PROCEDURE get_room_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM HABITACION_SALA WHERE PUBLIC_ID = p_public_id;
    END get_room_by_public_id;

    PROCEDURE list_rooms_by_area(p_area_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR SELECT * FROM HABITACION_SALA WHERE AREA_ID = p_area_id ORDER BY CODIGO ASC;
    END list_rooms_by_area;

    -- ---------------------------------------------------------------- Camas
    PROCEDURE create_bed(p_public_id IN VARCHAR2, p_room_id IN NUMBER, p_code IN VARCHAR2,
                         p_status IN VARCHAR2, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO CAMA_HOSPITALARIA (PUBLIC_ID, HABITACION_ID, CODIGO, ESTADO)
        VALUES (p_public_id, p_room_id, p_code, p_status)
        RETURNING ID INTO o_id;
    END create_bed;

    PROCEDURE get_bed_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM CAMA_HOSPITALARIA WHERE ID = p_id;
    END get_bed_by_id;

    PROCEDURE get_bed_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM CAMA_HOSPITALARIA WHERE PUBLIC_ID = p_public_id;
    END get_bed_by_public_id;

    PROCEDURE update_bed_status(p_bed_id IN NUMBER, p_status IN VARCHAR2) IS
    BEGIN
        UPDATE CAMA_HOSPITALARIA SET ESTADO = p_status, UPDATED_AT = CURRENT_TIMESTAMP WHERE ID = p_bed_id;
    END update_bed_status;

    PROCEDURE reserve_bed_if_available(p_bed_id IN NUMBER, o_reserved OUT NUMBER) IS
    BEGIN
        UPDATE CAMA_HOSPITALARIA
           SET ESTADO = 'OCUPADA', UPDATED_AT = CURRENT_TIMESTAMP
         WHERE ID = p_bed_id AND ESTADO = 'DISPONIBLE';
        o_reserved := CASE WHEN SQL%ROWCOUNT = 1 THEN 1 ELSE 0 END;
    END reserve_bed_if_available;

    PROCEDURE list_bed_details_by_site(p_site_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT c.PUBLIC_ID AS CAMA_PUB_ID,
                   c.CODIGO AS CAMA_CODIGO,
                   c.ESTADO,
                   h.PUBLIC_ID AS HAB_PUB_ID,
                   h.CODIGO AS HAB_CODIGO,
                   a.PUBLIC_ID AS AREA_PUB_ID,
                   a.NOMBRE AS AREA_NOMBRE,
                   cfg.PUBLIC_ID AS SEDE_PUB_ID,
                   cfg.RAZON_SOCIAL AS SEDE_NOMBRE,
                   ep.PUBLIC_ID AS EPISODIO_PUB_ID,
                   CASE
                       WHEN p.ID IS NOT NULL THEN (p.NOMBRES || ' ' || p.APELLIDOS)
                       WHEN ip.CODIGO_PROVISIONAL IS NOT NULL THEN ('NN (' || ip.CODIGO_PROVISIONAL || ')')
                       ELSE NULL
                   END AS PACIENTE_NOMBRE,
                   oc.PUBLIC_ID AS OCUPACION_PUB_ID
              FROM CAMA_HOSPITALARIA c
              JOIN HABITACION_SALA h ON c.HABITACION_ID = h.ID
              JOIN AREA_HOSPITALARIA a ON h.AREA_ID = a.ID
              CROSS JOIN CONFIGURACION_HOSPITAL cfg
              LEFT JOIN OCUPACION_CAMA oc ON oc.CAMA_ID = c.ID AND oc.ESTADO = 'ACTIVA'
              LEFT JOIN EPISODIO_ATENCION ep ON oc.EPISODIO_ID = ep.ID
              LEFT JOIN PACIENTE p ON ep.PACIENTE_ID = p.ID
              LEFT JOIN IDENTIDAD_PROVISIONAL ip ON ip.EPISODIO_ID = ep.ID AND ip.ESTADO = 'PROVISIONAL'
             WHERE (p_site_id IS NULL OR cfg.ID = p_site_id)
             ORDER BY a.CODIGO, h.CODIGO, c.CODIGO;
    END list_bed_details_by_site;

    PROCEDURE get_bed_detail_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT c.PUBLIC_ID AS CAMA_PUB_ID,
                   c.CODIGO AS CAMA_CODIGO,
                   c.ESTADO,
                   h.PUBLIC_ID AS HAB_PUB_ID,
                   h.CODIGO AS HAB_CODIGO,
                   a.PUBLIC_ID AS AREA_PUB_ID,
                   a.NOMBRE AS AREA_NOMBRE,
                   cfg.PUBLIC_ID AS SEDE_PUB_ID,
                   cfg.RAZON_SOCIAL AS SEDE_NOMBRE,
                   ep.PUBLIC_ID AS EPISODIO_PUB_ID,
                   CASE
                       WHEN p.ID IS NOT NULL THEN (p.NOMBRES || ' ' || p.APELLIDOS)
                       WHEN ip.CODIGO_PROVISIONAL IS NOT NULL THEN ('NN (' || ip.CODIGO_PROVISIONAL || ')')
                       ELSE NULL
                   END AS PACIENTE_NOMBRE,
                   oc.PUBLIC_ID AS OCUPACION_PUB_ID
              FROM CAMA_HOSPITALARIA c
              JOIN HABITACION_SALA h ON c.HABITACION_ID = h.ID
              JOIN AREA_HOSPITALARIA a ON h.AREA_ID = a.ID
              CROSS JOIN CONFIGURACION_HOSPITAL cfg
              LEFT JOIN OCUPACION_CAMA oc ON oc.CAMA_ID = c.ID AND oc.ESTADO = 'ACTIVA'
              LEFT JOIN EPISODIO_ATENCION ep ON oc.EPISODIO_ID = ep.ID
              LEFT JOIN PACIENTE p ON ep.PACIENTE_ID = p.ID
              LEFT JOIN IDENTIDAD_PROVISIONAL ip ON ip.EPISODIO_ID = ep.ID AND ip.ESTADO = 'PROVISIONAL'
             WHERE c.PUBLIC_ID = p_public_id;
    END get_bed_detail_by_public_id;

    -- ---------------------------------------------------------------- Ocupación de camas
    PROCEDURE create_occupancy(p_public_id IN VARCHAR2, p_episode_id IN NUMBER, p_bed_id IN NUMBER,
                               p_start_at IN TIMESTAMP WITH TIME ZONE, p_status IN VARCHAR2,
                               p_assigned_by_user_id IN NUMBER, p_reason IN VARCHAR2, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO OCUPACION_CAMA (PUBLIC_ID, EPISODIO_ID, CAMA_ID, INICIO_AT, ESTADO,
                                    ASIGNADO_POR_USUARIO_ID, MOTIVO_ASIGNACION)
        VALUES (p_public_id, p_episode_id, p_bed_id, p_start_at, p_status, p_assigned_by_user_id, p_reason)
        RETURNING ID INTO o_id;
    EXCEPTION
        WHEN DUP_VAL_ON_INDEX THEN
            -- Ocupación concurrente detectada por UQ_OCUP_CAMA_ACTIVA / UQ_OCUP_EPISODIO_ACTIVA.
            o_id := NULL;
    END create_occupancy;

    PROCEDURE get_occupancy_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM OCUPACION_CAMA WHERE ID = p_id;
    END get_occupancy_by_id;

    PROCEDURE get_active_occupancy_by_bed(p_bed_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM OCUPACION_CAMA WHERE CAMA_ID = p_bed_id AND ESTADO = 'ACTIVA';
    END get_active_occupancy_by_bed;

    PROCEDURE get_active_occupancy_by_episode(p_episode_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM OCUPACION_CAMA WHERE EPISODIO_ID = p_episode_id AND ESTADO = 'ACTIVA';
    END get_active_occupancy_by_episode;

    PROCEDURE finish_occupancy(p_occupancy_id IN NUMBER, p_end_at IN TIMESTAMP WITH TIME ZONE) IS
    BEGIN
        UPDATE OCUPACION_CAMA SET ESTADO = 'FINALIZADA', FIN_AT = p_end_at WHERE ID = p_occupancy_id;
    END finish_occupancy;

    -- ---------------------------------------------------------------- Movimientos
    PROCEDURE create_movement(p_public_id IN VARCHAR2, p_episode_id IN NUMBER, p_origin_area_id IN NUMBER,
                              p_target_area_id IN NUMBER, p_origin_bed_id IN NUMBER,
                              p_target_bed_id IN NUMBER, p_moved_at IN TIMESTAMP WITH TIME ZONE,
                              p_reason IN VARCHAR2, p_registered_by_user_id IN NUMBER, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO MOVIMIENTO_PACIENTE (PUBLIC_ID, EPISODIO_ID, AREA_ORIGEN_ID, AREA_DESTINO_ID,
                                         CAMA_ORIGEN_ID, CAMA_DESTINO_ID, FECHA_MOVIMIENTO,
                                         MOTIVO_TRASLADO, REGISTRADO_POR_USUARIO_ID)
        VALUES (p_public_id, p_episode_id, p_origin_area_id, p_target_area_id, p_origin_bed_id,
                p_target_bed_id, p_moved_at, p_reason, p_registered_by_user_id)
        RETURNING ID INTO o_id;
    END create_movement;

    PROCEDURE get_movement_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM MOVIMIENTO_PACIENTE WHERE ID = p_id;
    END get_movement_by_id;

    PROCEDURE list_movements_by_episode(p_episode_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT m.PUBLIC_ID,
                   e.PUBLIC_ID AS EPISODIO_PUB_ID,
                   ao.NOMBRE AS AREA_ORIGEN_NOMBRE,
                   ad.NOMBRE AS AREA_DESTINO_NOMBRE,
                   co.CODIGO AS CAMA_ORIGEN_CODIGO,
                   cd.CODIGO AS CAMA_DESTINO_CODIGO,
                   m.FECHA_MOVIMIENTO,
                   m.MOTIVO_TRASLADO,
                   u.EMAIL AS REGISTRADO_POR_NOMBRE
              FROM MOVIMIENTO_PACIENTE m
              JOIN EPISODIO_ATENCION e ON m.EPISODIO_ID = e.ID
              LEFT JOIN AREA_HOSPITALARIA ao ON m.AREA_ORIGEN_ID = ao.ID
              JOIN AREA_HOSPITALARIA ad ON m.AREA_DESTINO_ID = ad.ID
              LEFT JOIN CAMA_HOSPITALARIA co ON m.CAMA_ORIGEN_ID = co.ID
              LEFT JOIN CAMA_HOSPITALARIA cd ON m.CAMA_DESTINO_ID = cd.ID
              JOIN USUARIO u ON m.REGISTRADO_POR_USUARIO_ID = u.ID
             WHERE m.EPISODIO_ID = p_episode_id
             ORDER BY m.FECHA_MOVIMIENTO ASC;
    END list_movements_by_episode;

    -- ---------------------------------------------------------------- Egresos
    PROCEDURE create_discharge(p_public_id IN VARCHAR2, p_episode_id IN NUMBER, p_doctor_id IN NUMBER,
                               p_discharged_at IN TIMESTAMP WITH TIME ZONE, p_destination_type IN VARCHAR2,
                               p_diagnosis IN VARCHAR2, p_epicrisis IN VARCHAR2, p_care_plan IN VARCHAR2,
                               o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO EGRESO_HOSPITALARIO (PUBLIC_ID, EPISODIO_ID, MEDICO_EGRESO_ID, FECHA_EGRESO,
                                         TIPO_DESTINO, DIAGNOSTICO_EGRESO, EPICRISIS_RESUMEN, PLAN_MANEJO)
        VALUES (p_public_id, p_episode_id, p_doctor_id, p_discharged_at, p_destination_type, p_diagnosis,
                p_epicrisis, p_care_plan)
        RETURNING ID INTO o_id;
    END create_discharge;

    PROCEDURE get_discharge_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM EGRESO_HOSPITALARIO WHERE ID = p_id;
    END get_discharge_by_id;

    PROCEDURE get_discharge_by_episode(p_episode_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM EGRESO_HOSPITALARIO WHERE EPISODIO_ID = p_episode_id;
    END get_discharge_by_episode;

    -- ---------------------------------------------------------------- Procedimientos
    PROCEDURE create_procedure(p_public_id IN VARCHAR2, p_episode_id IN NUMBER, p_procedure_type IN VARCHAR2,
                               p_description IN VARCHAR2, p_professional_id IN NUMBER, p_room_id IN NUMBER,
                               p_status IN VARCHAR2, p_start_at IN TIMESTAMP WITH TIME ZONE,
                               p_observations IN VARCHAR2, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO PROCEDIMIENTO_HOSPITALARIO (PUBLIC_ID, EPISODIO_ID, TIPO_PROCEDIMIENTO, DESCRIPCION,
                                                PROFESIONAL_ID, SALA_ID, ESTADO, INICIO_AT, OBSERVACIONES)
        VALUES (p_public_id, p_episode_id, p_procedure_type, p_description, p_professional_id, p_room_id,
                p_status, p_start_at, p_observations)
        RETURNING ID INTO o_id;
    END create_procedure;

    PROCEDURE get_procedure_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM PROCEDIMIENTO_HOSPITALARIO WHERE ID = p_id;
    END get_procedure_by_id;

    PROCEDURE get_procedure_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR SELECT * FROM PROCEDIMIENTO_HOSPITALARIO WHERE PUBLIC_ID = p_public_id;
    END get_procedure_by_public_id;

    PROCEDURE update_procedure_status(p_id IN NUMBER, p_status IN VARCHAR2,
                                      p_end_at IN TIMESTAMP WITH TIME ZONE, p_observations IN VARCHAR2) IS
    BEGIN
        UPDATE PROCEDIMIENTO_HOSPITALARIO
           SET ESTADO = p_status, FIN_AT = p_end_at, OBSERVACIONES = COALESCE(p_observations, OBSERVACIONES)
         WHERE ID = p_id;
    END update_procedure_status;

    PROCEDURE list_procedures_by_episode(p_episode_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ph.PUBLIC_ID,
                   e.PUBLIC_ID AS EPISODIO_PUB_ID,
                   ph.TIPO_PROCEDIMIENTO,
                   ph.DESCRIPCION,
                   (prof.NOMBRES || ' ' || prof.APELLIDOS) AS PROFESIONAL_NOMBRE,
                   hs.CODIGO AS SALA_CODIGO,
                   ph.ESTADO,
                   ph.INICIO_AT,
                   ph.FIN_AT,
                   ph.OBSERVACIONES,
                   ph.CREADO_AT
              FROM PROCEDIMIENTO_HOSPITALARIO ph
              JOIN EPISODIO_ATENCION e ON ph.EPISODIO_ID = e.ID
              JOIN PROFESIONAL prof ON ph.PROFESIONAL_ID = prof.ID
              LEFT JOIN HABITACION_SALA hs ON ph.SALA_ID = hs.ID
             WHERE ph.EPISODIO_ID = p_episode_id
             ORDER BY ph.CREADO_AT DESC;
    END list_procedures_by_episode;
END PKG_HOSPITAL;
/
