-- =============================================================================
-- MediTriaje - R__pkg_1_spec_patients: especificación de PKG_PATIENTS
-- Repetible. Pacientes, consentimientos, representación legal, identidades provisionales y alergias.
-- Derechos del definidor; nunca ejecuta COMMIT ni ROLLBACK (la transacción es de Java).
-- Los cursores de 0 o 1 fila los consume el repositorio con PackageGateway.single.
-- =============================================================================
CREATE OR REPLACE PACKAGE PKG_PATIENTS AUTHID DEFINER AS

    -- ---------------------------------------------------------------- PACIENTE
    -- Cantidad de pacientes con el tipo y número de documento indicados (0 o 1).
    FUNCTION count_patients_by_document(p_document_type IN VARCHAR2, p_document_number IN VARCHAR2) RETURN NUMBER;

    -- Registra un paciente y devuelve su ID interno.
    PROCEDURE create_patient(p_user_id IN NUMBER, p_public_id IN VARCHAR2, p_document_type IN VARCHAR2,
                             p_document_number IN VARCHAR2, p_first_names IN VARCHAR2, p_last_names IN VARCHAR2,
                             p_birth_date IN DATE, p_phone IN VARCHAR2, o_id OUT NUMBER);

    -- Perfil del paciente asociado al usuario (PUBLIC_ID de USUARIO). Columnas: PUBLIC_ID, TIPO_DOCUMENTO,
    -- NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, FECHA_NACIMIENTO, TELEFONO, EMAIL.
    PROCEDURE get_patient_profile_by_user_public_id(p_user_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);

    -- Paciente por ID de usuario, ID interno, PUBLIC_ID o documento. Columnas: ID, USUARIO_ID, PUBLIC_ID,
    -- TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, FECHA_NACIMIENTO, TELEFONO, CREATED_AT, UPDATED_AT.
    PROCEDURE get_patient_by_user_id(p_user_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    PROCEDURE get_patient_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    PROCEDURE get_patient_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);
    PROCEDURE get_patient_by_document(p_document_type IN VARCHAR2, p_document_number IN VARCHAR2,
                                      o_row OUT SYS_REFCURSOR);
    -- Busca pacientes por número de documento (y tipo opcional) para admisión y triaje presencial.
    PROCEDURE search_patients_by_document(p_document_number IN VARCHAR2, p_document_type IN VARCHAR2,
                                          o_rows OUT SYS_REFCURSOR);
    -- Vincula un usuario web recién registrado a un registro de paciente creado presencialmente sin cuenta.
    PROCEDURE link_user_to_patient(p_patient_id IN NUMBER, p_user_id IN NUMBER, o_updated OUT NUMBER);

    -- ---------------------------------------------------------- CONSENTIMIENTO
    -- Registra el consentimiento de tratamiento de datos (REVOCADO = 0) y devuelve su ID interno.
    PROCEDURE create_consent(p_user_id IN NUMBER, p_text_version IN VARCHAR2, p_accepted IN NUMBER,
                             p_source_ip IN VARCHAR2, o_id OUT NUMBER);

    -- ---------------------------------------------------- REPRESENTACION_LEGAL
    -- Registra una representación legal y devuelve su ID interno.
    PROCEDURE create_legal_representation(p_public_id IN VARCHAR2, p_minor_patient_id IN NUMBER,
                                          p_representative_patient_id IN NUMBER, p_relationship IN VARCHAR2,
                                          p_supporting_document IN VARCHAR2, p_verified IN NUMBER,
                                          o_id OUT NUMBER);

    -- Representación legal por ID interno (todas las columnas de la tabla).
    PROCEDURE get_legal_representation_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);

    -- Menores representados por un paciente, con nombres y documentos de ambos.
    PROCEDURE list_legal_representations_by_representative(p_representative_patient_id IN NUMBER,
                                                           o_rows OUT SYS_REFCURSOR);

    -- --------------------------------------------------- IDENTIDAD_PROVISIONAL
    -- Registra una identidad provisional de urgencias y devuelve su ID interno.
    PROCEDURE create_provisional_identity(p_public_id IN VARCHAR2, p_episode_id IN NUMBER,
                                          p_provisional_code IN VARCHAR2, p_physical_description IN VARCHAR2,
                                          p_apparent_age IN NUMBER, p_apparent_gender IN VARCHAR2,
                                          p_arrival_condition IN VARCHAR2, p_status IN VARCHAR2,
                                          o_id OUT NUMBER);

    -- Identidad provisional por episodio o por código (todas las columnas de la tabla).
    PROCEDURE get_provisional_identity_by_episode(p_episode_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    PROCEDURE get_provisional_identity_by_code(p_provisional_code IN VARCHAR2, o_row OUT SYS_REFCURSOR);

    -- Vincula una identidad PROVISIONAL con el paciente real; o_affected = filas actualizadas (0 o 1).
    PROCEDURE reconcile_provisional_identity(p_id IN NUMBER, p_patient_id IN NUMBER,
                                             p_confirmed_by_user_id IN NUMBER,
                                             p_confirmed_at IN TIMESTAMP WITH TIME ZONE, o_affected OUT NUMBER);

    -- ----------------------------------------------------------------- ALERGIA
    -- Alergias de un paciente por ID interno, más recientes primero; p_only_active = 1 solo devuelve ACTIVA.
    PROCEDURE list_allergies_by_patient_id(p_patient_id IN NUMBER, p_only_active IN NUMBER,
                                           o_rows OUT SYS_REFCURSOR);

    -- Alergias (vista de respuesta) de un paciente por PUBLIC_ID; p_include_inactive = 1 incluye INACTIVA.
    PROCEDURE list_allergies_by_patient_public_id(p_patient_public_id IN VARCHAR2, p_include_inactive IN NUMBER,
                                                  o_rows OUT SYS_REFCURSOR);

    -- Alergia (fila completa) por PUBLIC_ID.
    PROCEDURE get_allergy_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);

    -- Alergia (vista de respuesta) por PUBLIC_ID.
    PROCEDURE get_allergy_view_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);

    -- Cantidad de alergias ACTIVA del paciente para la sustancia (sin distinguir mayúsculas ni espacios).
    FUNCTION count_active_allergies_by_substance(p_patient_id IN NUMBER, p_substance IN VARCHAR2) RETURN NUMBER;

    -- Registra una alergia y devuelve su ID interno.
    PROCEDURE create_allergy(p_public_id IN VARCHAR2, p_patient_id IN NUMBER, p_substance IN VARCHAR2,
                             p_reaction IN VARCHAR2, p_severity IN VARCHAR2, p_status IN VARCHAR2,
                             p_origin IN VARCHAR2, p_registered_by_user_id IN NUMBER, p_encounter_id IN NUMBER,
                             p_created_at IN TIMESTAMP WITH TIME ZONE, p_updated_at IN TIMESTAMP WITH TIME ZONE,
                             o_id OUT NUMBER);

    -- Inactiva una alergia ACTIVA; o_affected = filas actualizadas (0 o 1).
    PROCEDURE inactivate_allergy(p_id IN NUMBER, p_inactivated_by_user_id IN NUMBER, p_reason IN VARCHAR2,
                                 p_inactivated_at IN TIMESTAMP WITH TIME ZONE, o_affected OUT NUMBER);
END PKG_PATIENTS;
/
