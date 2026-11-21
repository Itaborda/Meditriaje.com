-- =============================================================================
-- MediTriaje - R__pkg_2_body_staff: cuerpo de PKG_STAFF
-- Repetible. Sin COMMIT ni ROLLBACK (la transacción pertenece a la capa de aplicación).
-- =============================================================================
CREATE OR REPLACE PACKAGE BODY PKG_STAFF AS

    -- ------------------------------------------------------------ PROFESIONAL

    PROCEDURE create_professional(p_user_id IN NUMBER, p_public_id IN VARCHAR2, p_specialty_id IN NUMBER,
                                  p_document_type IN VARCHAR2, p_document_number IN VARCHAR2,
                                  p_medical_registry IN VARCHAR2, p_first_names IN VARCHAR2,
                                  p_last_names IN VARCHAR2, p_phone IN VARCHAR2, o_professional_id OUT NUMBER) IS
    BEGIN
        INSERT INTO PROFESIONAL (USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                                 REGISTRO_MEDICO, NOMBRES, APELLIDOS, TELEFONO)
        VALUES (p_user_id, p_public_id, p_specialty_id, p_document_type, p_document_number,
                p_medical_registry, p_first_names, p_last_names, p_phone)
        RETURNING ID INTO o_professional_id;
    END create_professional;

    PROCEDURE update_professional(p_public_id IN VARCHAR2, p_specialty_id IN NUMBER, p_first_names IN VARCHAR2,
                                  p_last_names IN VARCHAR2, p_phone IN VARCHAR2) IS
    BEGIN
        UPDATE PROFESIONAL
           SET ESPECIALIDAD_ID = p_specialty_id, NOMBRES = p_first_names, APELLIDOS = p_last_names,
               TELEFONO = p_phone, UPDATED_AT = CURRENT_TIMESTAMP
         WHERE PUBLIC_ID = p_public_id;
    END update_professional;

    PROCEDURE get_professional_by_public_id(p_public_id IN VARCHAR2, o_professional OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_professional FOR
            SELECT ID, USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, REGISTRO_MEDICO,
                   NOMBRES, APELLIDOS, TELEFONO, CREATED_AT, UPDATED_AT
              FROM PROFESIONAL
             WHERE PUBLIC_ID = p_public_id;
    END get_professional_by_public_id;

    PROCEDURE get_professional_by_id(p_professional_id IN NUMBER, o_professional OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_professional FOR
            SELECT ID, USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, REGISTRO_MEDICO,
                   NOMBRES, APELLIDOS, TELEFONO, CREATED_AT, UPDATED_AT
              FROM PROFESIONAL
             WHERE ID = p_professional_id;
    END get_professional_by_id;

    PROCEDURE get_professional_by_user_id(p_user_id IN NUMBER, o_professional OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_professional FOR
            SELECT ID, USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, REGISTRO_MEDICO,
                   NOMBRES, APELLIDOS, TELEFONO, CREATED_AT, UPDATED_AT
              FROM PROFESIONAL
             WHERE USUARIO_ID = p_user_id;
    END get_professional_by_user_id;

    FUNCTION count_by_medical_registry(p_medical_registry IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM PROFESIONAL
         WHERE UPPER(TRIM(REGISTRO_MEDICO)) = UPPER(TRIM(p_medical_registry));
        RETURN v_count;
    END count_by_medical_registry;

    FUNCTION count_other_by_medical_registry(p_medical_registry IN VARCHAR2, p_public_id IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM PROFESIONAL
         WHERE UPPER(TRIM(REGISTRO_MEDICO)) = UPPER(TRIM(p_medical_registry))
           AND PUBLIC_ID <> p_public_id;
        RETURN v_count;
    END count_other_by_medical_registry;

    FUNCTION count_by_document(p_document_type IN VARCHAR2, p_document_number IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM PROFESIONAL
         WHERE UPPER(TRIM(TIPO_DOCUMENTO)) = UPPER(TRIM(p_document_type))
           AND UPPER(TRIM(NUMERO_DOCUMENTO)) = UPPER(TRIM(p_document_number));
        RETURN v_count;
    END count_by_document;

    FUNCTION count_other_by_document(p_document_type IN VARCHAR2, p_document_number IN VARCHAR2,
                                     p_public_id IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM PROFESIONAL
         WHERE UPPER(TRIM(TIPO_DOCUMENTO)) = UPPER(TRIM(p_document_type))
           AND UPPER(TRIM(NUMERO_DOCUMENTO)) = UPPER(TRIM(p_document_number))
           AND PUBLIC_ID <> p_public_id;
        RETURN v_count;
    END count_other_by_document;

    PROCEDURE list_professionals(p_specialty_public_id IN VARCHAR2, p_status IN VARCHAR2, p_offset IN NUMBER,
                                 p_size IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT p.PUBLIC_ID, u.PUBLIC_ID AS USUARIO_PUBLIC_ID, p.TIPO_DOCUMENTO, p.NUMERO_DOCUMENTO,
                   p.REGISTRO_MEDICO, p.NOMBRES, p.APELLIDOS, u.EMAIL, p.TELEFONO,
                   e.PUBLIC_ID AS ESPECIALIDAD_PUBLIC_ID, e.NOMBRE AS ESPECIALIDAD_NOMBRE, u.ESTADO,
                   u.DEBE_CAMBIAR_PASSWORD, p.CREATED_AT
              FROM PROFESIONAL p
              JOIN USUARIO u ON p.USUARIO_ID = u.ID
              JOIN ESPECIALIDAD e ON p.ESPECIALIDAD_ID = e.ID
             WHERE (p_specialty_public_id IS NULL OR e.PUBLIC_ID = p_specialty_public_id)
               AND (p_status IS NULL OR u.ESTADO = p_status)
             ORDER BY p.APELLIDOS ASC, p.NOMBRES ASC, p.ID ASC
            OFFSET p_offset ROWS FETCH NEXT p_size ROWS ONLY;
    END list_professionals;

    FUNCTION count_professionals(p_specialty_public_id IN VARCHAR2, p_status IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM PROFESIONAL p
          JOIN USUARIO u ON p.USUARIO_ID = u.ID
          JOIN ESPECIALIDAD e ON p.ESPECIALIDAD_ID = e.ID
         WHERE (p_specialty_public_id IS NULL OR e.PUBLIC_ID = p_specialty_public_id)
           AND (p_status IS NULL OR u.ESTADO = p_status);
        RETURN v_count;
    END count_professionals;

    -- ----------------------------------------------------------- ESPECIALIDAD

    PROCEDURE create_specialty(p_public_id IN VARCHAR2, p_name IN VARCHAR2, p_slot_minutes IN NUMBER,
                               o_specialty_id OUT NUMBER) IS
    BEGIN
        INSERT INTO ESPECIALIDAD (PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN, ESTADO)
        VALUES (p_public_id, p_name, p_slot_minutes, 'ACTIVO')
        RETURNING ID INTO o_specialty_id;
    END create_specialty;

    PROCEDURE update_specialty(p_public_id IN VARCHAR2, p_name IN VARCHAR2, p_slot_minutes IN NUMBER) IS
    BEGIN
        UPDATE ESPECIALIDAD SET NOMBRE = p_name, DURACION_SLOT_MIN = p_slot_minutes WHERE PUBLIC_ID = p_public_id;
    END update_specialty;

    PROCEDURE get_specialty_by_public_id(p_public_id IN VARCHAR2, o_specialty OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_specialty FOR
            SELECT ID, PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN, ESTADO FROM ESPECIALIDAD WHERE PUBLIC_ID = p_public_id;
    END get_specialty_by_public_id;

    PROCEDURE get_specialty_by_id(p_specialty_id IN NUMBER, o_specialty OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_specialty FOR
            SELECT ID, PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN, ESTADO FROM ESPECIALIDAD WHERE ID = p_specialty_id;
    END get_specialty_by_id;

    FUNCTION count_specialties_by_name(p_name IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM ESPECIALIDAD WHERE UPPER(TRIM(NOMBRE)) = UPPER(TRIM(p_name));
        RETURN v_count;
    END count_specialties_by_name;

    FUNCTION count_other_specialties_by_name(p_name IN VARCHAR2, p_public_id IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM ESPECIALIDAD
         WHERE UPPER(TRIM(NOMBRE)) = UPPER(TRIM(p_name)) AND PUBLIC_ID <> p_public_id;
        RETURN v_count;
    END count_other_specialties_by_name;

    PROCEDURE change_specialty_status(p_public_id IN VARCHAR2, p_status IN VARCHAR2) IS
    BEGIN
        UPDATE ESPECIALIDAD SET ESTADO = p_status WHERE PUBLIC_ID = p_public_id;
    END change_specialty_status;

    PROCEDURE list_specialties(p_status IN VARCHAR2, p_offset IN NUMBER, p_size IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ID, PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN, ESTADO
              FROM ESPECIALIDAD
             WHERE (p_status IS NULL OR ESTADO = p_status)
             ORDER BY NOMBRE ASC, ID ASC
            OFFSET p_offset ROWS FETCH NEXT p_size ROWS ONLY;
    END list_specialties;

    FUNCTION count_specialties(p_status IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM ESPECIALIDAD WHERE (p_status IS NULL OR ESTADO = p_status);
        RETURN v_count;
    END count_specialties;

    -- ------------------------------------------------- ASIGNACION_ASISTENCIAL

    PROCEDURE create_assignment(p_public_id IN VARCHAR2, p_episode_id IN NUMBER, p_professional_id IN NUMBER,
                                p_role IN VARCHAR2, p_assigned_by_user_id IN NUMBER,
                                p_start_at IN TIMESTAMP WITH TIME ZONE, p_active IN NUMBER,
                                o_assignment_id OUT NUMBER) IS
    BEGIN
        INSERT INTO ASIGNACION_ASISTENCIAL (
            PUBLIC_ID, EPISODIO_ID, PROFESIONAL_ID, FUNCION, ASIGNADO_POR_USUARIO_ID, FECHA_INICIO, ACTIVO
        ) VALUES (
            p_public_id, p_episode_id, p_professional_id, p_role, p_assigned_by_user_id, p_start_at, p_active
        )
        RETURNING ID INTO o_assignment_id;
    END create_assignment;

    PROCEDURE deactivate_previous_assignment(p_episode_id IN NUMBER, p_role IN VARCHAR2, o_updated OUT NUMBER) IS
    BEGIN
        UPDATE ASIGNACION_ASISTENCIAL
           SET ACTIVO = 0, FECHA_FIN = CURRENT_TIMESTAMP
         WHERE EPISODIO_ID = p_episode_id AND FUNCION = p_role AND ACTIVO = 1;
        o_updated := SQL%ROWCOUNT;
    END deactivate_previous_assignment;

    FUNCTION count_active_assignments(p_professional_id IN NUMBER, p_episode_id IN NUMBER) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM ASIGNACION_ASISTENCIAL
         WHERE PROFESIONAL_ID = p_professional_id AND EPISODIO_ID = p_episode_id AND ACTIVO = 1;
        RETURN v_count;
    END count_active_assignments;

    PROCEDURE list_assignments_by_episode(p_episode_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ID, PUBLIC_ID, EPISODIO_ID, PROFESIONAL_ID, FUNCION, ASIGNADO_POR_USUARIO_ID, FECHA_INICIO,
                   FECHA_FIN, ACTIVO, CREADO_AT
              FROM ASIGNACION_ASISTENCIAL
             WHERE EPISODIO_ID = p_episode_id
             ORDER BY FECHA_INICIO ASC;
    END list_assignments_by_episode;
END PKG_STAFF;
/
