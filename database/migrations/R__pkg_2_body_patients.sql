-- =============================================================================
-- MediTriaje - R__pkg_2_body_patients: cuerpo de PKG_PATIENTS
-- Repetible. Sin COMMIT ni ROLLBACK (la transacción pertenece a la capa de aplicación).
-- =============================================================================
CREATE OR REPLACE PACKAGE BODY PKG_PATIENTS AS

    -- ---------------------------------------------------------------- PACIENTE
    FUNCTION count_patients_by_document(p_document_type IN VARCHAR2, p_document_number IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM PACIENTE
         WHERE TIPO_DOCUMENTO = p_document_type AND NUMERO_DOCUMENTO = p_document_number;
        RETURN v_count;
    END count_patients_by_document;

    PROCEDURE create_patient(p_user_id IN NUMBER, p_public_id IN VARCHAR2, p_document_type IN VARCHAR2,
                             p_document_number IN VARCHAR2, p_first_names IN VARCHAR2, p_last_names IN VARCHAR2,
                             p_birth_date IN DATE, p_phone IN VARCHAR2, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO PACIENTE (USUARIO_ID, PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS,
                              FECHA_NACIMIENTO, TELEFONO)
        VALUES (p_user_id, p_public_id, p_document_type, p_document_number, p_first_names, p_last_names,
                p_birth_date, p_phone)
        RETURNING ID INTO o_id;
    END create_patient;

    PROCEDURE get_patient_profile_by_user_public_id(p_user_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT p.PUBLIC_ID, p.TIPO_DOCUMENTO, p.NUMERO_DOCUMENTO, p.NOMBRES, p.APELLIDOS,
                   p.FECHA_NACIMIENTO, p.TELEFONO, u.EMAIL
              FROM PACIENTE p
              JOIN USUARIO u ON u.ID = p.USUARIO_ID
             WHERE u.PUBLIC_ID = p_user_public_id;
    END get_patient_profile_by_user_public_id;

    PROCEDURE get_patient_by_user_id(p_user_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, USUARIO_ID, PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                   NOMBRES, APELLIDOS, FECHA_NACIMIENTO, TELEFONO, CREATED_AT, UPDATED_AT
              FROM PACIENTE
             WHERE USUARIO_ID = p_user_id;
    END get_patient_by_user_id;

    PROCEDURE get_patient_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, USUARIO_ID, PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                   NOMBRES, APELLIDOS, FECHA_NACIMIENTO, TELEFONO, CREATED_AT, UPDATED_AT
              FROM PACIENTE
             WHERE ID = p_id;
    END get_patient_by_id;

    PROCEDURE get_patient_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, USUARIO_ID, PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                   NOMBRES, APELLIDOS, FECHA_NACIMIENTO, TELEFONO, CREATED_AT, UPDATED_AT
              FROM PACIENTE
             WHERE PUBLIC_ID = p_public_id;
    END get_patient_by_public_id;

    PROCEDURE get_patient_by_document(p_document_type IN VARCHAR2, p_document_number IN VARCHAR2,
                                      o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, USUARIO_ID, PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                   NOMBRES, APELLIDOS, FECHA_NACIMIENTO, TELEFONO, CREATED_AT, UPDATED_AT
              FROM PACIENTE
             WHERE TIPO_DOCUMENTO = p_document_type AND NUMERO_DOCUMENTO = p_document_number;
    END get_patient_by_document;

    PROCEDURE search_patients_by_document(p_document_number IN VARCHAR2, p_document_type IN VARCHAR2,
                                          o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ID, USUARIO_ID, PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                   NOMBRES, APELLIDOS, FECHA_NACIMIENTO, TELEFONO, CREATED_AT, UPDATED_AT
              FROM PACIENTE
             WHERE TRIM(NUMERO_DOCUMENTO) = TRIM(p_document_number)
               AND (p_document_type IS NULL OR TIPO_DOCUMENTO = p_document_type)
             ORDER BY CREATED_AT DESC;
    END search_patients_by_document;

    PROCEDURE link_user_to_patient(p_patient_id IN NUMBER, p_user_id IN NUMBER, o_updated OUT NUMBER) IS
    BEGIN
        UPDATE PACIENTE
           SET USUARIO_ID = p_user_id,
               UPDATED_AT = CURRENT_TIMESTAMP
         WHERE ID = p_patient_id
           AND USUARIO_ID IS NULL;
        o_updated := SQL%ROWCOUNT;
    END link_user_to_patient;

    -- ---------------------------------------------------------- CONSENTIMIENTO
    PROCEDURE create_consent(p_user_id IN NUMBER, p_text_version IN VARCHAR2, p_accepted IN NUMBER,
                             p_source_ip IN VARCHAR2, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO CONSENTIMIENTO (USUARIO_ID, VERSION_TEXTO, ACEPTADO, IP_ORIGEN, REVOCADO)
        VALUES (p_user_id, p_text_version, p_accepted, p_source_ip, 0)
        RETURNING ID INTO o_id;
    END create_consent;

    -- ---------------------------------------------------- REPRESENTACION_LEGAL
    PROCEDURE create_legal_representation(p_public_id IN VARCHAR2, p_minor_patient_id IN NUMBER,
                                          p_representative_patient_id IN NUMBER, p_relationship IN VARCHAR2,
                                          p_supporting_document IN VARCHAR2, p_verified IN NUMBER,
                                          o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO REPRESENTACION_LEGAL (PUBLIC_ID, PACIENTE_MENOR_ID, REPRESENTANTE_PACIENTE_ID,
                                          PARENTESCO, DOCUMENTO_SOPORTE, VERIFICADO)
        VALUES (p_public_id, p_minor_patient_id, p_representative_patient_id,
                p_relationship, p_supporting_document, p_verified)
        RETURNING ID INTO o_id;
    END create_legal_representation;

    PROCEDURE get_legal_representation_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT * FROM REPRESENTACION_LEGAL WHERE ID = p_id;
    END get_legal_representation_by_id;

    PROCEDURE list_legal_representations_by_representative(p_representative_patient_id IN NUMBER,
                                                           o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT rl.PUBLIC_ID,
                   pm.PUBLIC_ID AS MENOR_PUB_ID,
                   pm.NOMBRES AS MENOR_NOMBRE,
                   pm.APELLIDOS AS MENOR_APELLIDO,
                   pm.NUMERO_DOCUMENTO AS MENOR_DOC,
                   pr.PUBLIC_ID AS REP_PUB_ID,
                   pr.NOMBRES AS REP_NOMBRE,
                   pr.APELLIDOS AS REP_APELLIDO,
                   pr.NUMERO_DOCUMENTO AS REP_DOC,
                   rl.PARENTESCO,
                   rl.VERIFICADO
              FROM REPRESENTACION_LEGAL rl
              JOIN PACIENTE pm ON rl.PACIENTE_MENOR_ID = pm.ID
              JOIN PACIENTE pr ON rl.REPRESENTANTE_PACIENTE_ID = pr.ID
             WHERE rl.REPRESENTANTE_PACIENTE_ID = p_representative_patient_id;
    END list_legal_representations_by_representative;

    -- --------------------------------------------------- IDENTIDAD_PROVISIONAL
    PROCEDURE create_provisional_identity(p_public_id IN VARCHAR2, p_episode_id IN NUMBER,
                                          p_provisional_code IN VARCHAR2, p_physical_description IN VARCHAR2,
                                          p_apparent_age IN NUMBER, p_apparent_gender IN VARCHAR2,
                                          p_arrival_condition IN VARCHAR2, p_status IN VARCHAR2,
                                          o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO IDENTIDAD_PROVISIONAL (PUBLIC_ID, EPISODIO_ID, CODIGO_PROVISIONAL, DESCRIPCION_FISICA,
                                           EDAD_APARENTE, GENERO_APARENTE, CONDICION_LLEGADA, ESTADO)
        VALUES (p_public_id, p_episode_id, p_provisional_code, p_physical_description,
                p_apparent_age, p_apparent_gender, p_arrival_condition, p_status)
        RETURNING ID INTO o_id;
    END create_provisional_identity;

    PROCEDURE get_provisional_identity_by_episode(p_episode_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT * FROM IDENTIDAD_PROVISIONAL WHERE EPISODIO_ID = p_episode_id;
    END get_provisional_identity_by_episode;

    PROCEDURE get_provisional_identity_by_code(p_provisional_code IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT * FROM IDENTIDAD_PROVISIONAL WHERE CODIGO_PROVISIONAL = p_provisional_code;
    END get_provisional_identity_by_code;

    PROCEDURE reconcile_provisional_identity(p_id IN NUMBER, p_patient_id IN NUMBER,
                                             p_confirmed_by_user_id IN NUMBER,
                                             p_confirmed_at IN TIMESTAMP WITH TIME ZONE, o_affected OUT NUMBER) IS
    BEGIN
        UPDATE IDENTIDAD_PROVISIONAL
           SET ESTADO = 'VINCULADA',
               VINCULADO_PACIENTE_ID = p_patient_id,
               CONFIRMADO_POR_USUARIO_ID = p_confirmed_by_user_id,
               FECHA_CONFIRMACION = p_confirmed_at
         WHERE ID = p_id AND ESTADO = 'PROVISIONAL';
        o_affected := SQL%ROWCOUNT;
    END reconcile_provisional_identity;

    -- ----------------------------------------------------------------- ALERGIA
    PROCEDURE list_allergies_by_patient_id(p_patient_id IN NUMBER, p_only_active IN NUMBER,
                                           o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ID, PUBLIC_ID, PACIENTE_ID, SUSTANCIA, REACCION, SEVERIDAD, ESTADO, ORIGEN,
                   REGISTRADA_POR_USUARIO_ID, ATENCION_ID, FECHA_INACTIVACION, INACTIVADA_POR_USUARIO_ID,
                   MOTIVO_INACTIVACION, CREATED_AT, UPDATED_AT
              FROM ALERGIA
             WHERE PACIENTE_ID = p_patient_id
               AND (p_only_active = 0 OR ESTADO = 'ACTIVA')
             ORDER BY CREATED_AT DESC;
    END list_allergies_by_patient_id;

    PROCEDURE list_allergies_by_patient_public_id(p_patient_public_id IN VARCHAR2, p_include_inactive IN NUMBER,
                                                  o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT a.PUBLIC_ID, p.PUBLIC_ID AS PACIENTE_PUBLIC_ID, a.SUSTANCIA, a.REACCION, a.SEVERIDAD,
                   a.ESTADO, a.ORIGEN, at.PUBLIC_ID AS ATENCION_PUBLIC_ID, a.CREATED_AT,
                   a.FECHA_INACTIVACION, a.MOTIVO_INACTIVACION
              FROM ALERGIA a
              JOIN PACIENTE p ON a.PACIENTE_ID = p.ID
              LEFT JOIN ATENCION at ON a.ATENCION_ID = at.ID
             WHERE p.PUBLIC_ID = p_patient_public_id
               AND (p_include_inactive = 1 OR a.ESTADO = 'ACTIVA')
             ORDER BY CASE WHEN a.ESTADO = 'ACTIVA' THEN 1 ELSE 2 END, a.CREATED_AT DESC;
    END list_allergies_by_patient_public_id;

    PROCEDURE get_allergy_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, PACIENTE_ID, SUSTANCIA, REACCION, SEVERIDAD, ESTADO, ORIGEN,
                   REGISTRADA_POR_USUARIO_ID, ATENCION_ID, FECHA_INACTIVACION, INACTIVADA_POR_USUARIO_ID,
                   MOTIVO_INACTIVACION, CREATED_AT, UPDATED_AT
              FROM ALERGIA
             WHERE PUBLIC_ID = p_public_id;
    END get_allergy_by_public_id;

    PROCEDURE get_allergy_view_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT a.PUBLIC_ID, p.PUBLIC_ID AS PACIENTE_PUBLIC_ID, a.SUSTANCIA, a.REACCION, a.SEVERIDAD,
                   a.ESTADO, a.ORIGEN, at.PUBLIC_ID AS ATENCION_PUBLIC_ID, a.CREATED_AT,
                   a.FECHA_INACTIVACION, a.MOTIVO_INACTIVACION
              FROM ALERGIA a
              JOIN PACIENTE p ON a.PACIENTE_ID = p.ID
              LEFT JOIN ATENCION at ON a.ATENCION_ID = at.ID
             WHERE a.PUBLIC_ID = p_public_id;
    END get_allergy_view_by_public_id;

    FUNCTION count_active_allergies_by_substance(p_patient_id IN NUMBER, p_substance IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM ALERGIA
         WHERE PACIENTE_ID = p_patient_id
           AND UPPER(TRIM(SUSTANCIA)) = UPPER(TRIM(p_substance))
           AND ESTADO = 'ACTIVA';
        RETURN v_count;
    END count_active_allergies_by_substance;

    PROCEDURE create_allergy(p_public_id IN VARCHAR2, p_patient_id IN NUMBER, p_substance IN VARCHAR2,
                             p_reaction IN VARCHAR2, p_severity IN VARCHAR2, p_status IN VARCHAR2,
                             p_origin IN VARCHAR2, p_registered_by_user_id IN NUMBER, p_encounter_id IN NUMBER,
                             p_created_at IN TIMESTAMP WITH TIME ZONE, p_updated_at IN TIMESTAMP WITH TIME ZONE,
                             o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO ALERGIA (PUBLIC_ID, PACIENTE_ID, SUSTANCIA, REACCION, SEVERIDAD, ESTADO, ORIGEN,
                             REGISTRADA_POR_USUARIO_ID, ATENCION_ID, CREATED_AT, UPDATED_AT)
        VALUES (p_public_id, p_patient_id, p_substance, p_reaction, p_severity, p_status, p_origin,
                p_registered_by_user_id, p_encounter_id, p_created_at, p_updated_at)
        RETURNING ID INTO o_id;
    END create_allergy;

    PROCEDURE inactivate_allergy(p_id IN NUMBER, p_inactivated_by_user_id IN NUMBER, p_reason IN VARCHAR2,
                                 p_inactivated_at IN TIMESTAMP WITH TIME ZONE, o_affected OUT NUMBER) IS
    BEGIN
        UPDATE ALERGIA
           SET ESTADO = 'INACTIVA',
               INACTIVADA_POR_USUARIO_ID = p_inactivated_by_user_id,
               MOTIVO_INACTIVACION = p_reason,
               FECHA_INACTIVACION = p_inactivated_at,
               UPDATED_AT = CURRENT_TIMESTAMP
         WHERE ID = p_id AND ESTADO = 'ACTIVA';
        o_affected := SQL%ROWCOUNT;
    END inactivate_allergy;
END PKG_PATIENTS;
/
