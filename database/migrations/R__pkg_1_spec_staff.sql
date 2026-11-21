-- =============================================================================
-- MediTriaje - R__pkg_1_spec_staff: especificación de PKG_STAFF
-- Repetible. Talento humano asistencial: profesionales (Ley 1164/2007), especialidades y asignaciones
-- asistenciales a episodios (ADR-022). Derechos del definidor; sin COMMIT ni ROLLBACK.
-- Las comparaciones de registro médico, documento y nombre ignoran mayúsculas y espacios extremos.
-- =============================================================================
CREATE OR REPLACE PACKAGE PKG_STAFF AUTHID DEFINER AS

    -- ------------------------------------------------------------ PROFESIONAL
    -- Crea un profesional y devuelve su ID interno.
    PROCEDURE create_professional(p_user_id IN NUMBER, p_public_id IN VARCHAR2, p_specialty_id IN NUMBER,
                                  p_document_type IN VARCHAR2, p_document_number IN VARCHAR2,
                                  p_medical_registry IN VARCHAR2, p_first_names IN VARCHAR2,
                                  p_last_names IN VARCHAR2, p_phone IN VARCHAR2, o_professional_id OUT NUMBER);

    -- Actualiza especialidad, nombres, apellidos y teléfono del profesional.
    PROCEDURE update_professional(p_public_id IN VARCHAR2, p_specialty_id IN NUMBER, p_first_names IN VARCHAR2,
                                  p_last_names IN VARCHAR2, p_phone IN VARCHAR2);

    -- Profesional por identificador público (0 o 1 fila): ID, USUARIO_ID, PUBLIC_ID, ESPECIALIDAD_ID,
    -- TIPO_DOCUMENTO, NUMERO_DOCUMENTO, REGISTRO_MEDICO, NOMBRES, APELLIDOS, TELEFONO, CREATED_AT, UPDATED_AT.
    PROCEDURE get_professional_by_public_id(p_public_id IN VARCHAR2, o_professional OUT SYS_REFCURSOR);

    -- Profesional por ID interno (mismas columnas).
    PROCEDURE get_professional_by_id(p_professional_id IN NUMBER, o_professional OUT SYS_REFCURSOR);

    -- Profesional por ID de usuario (mismas columnas).
    PROCEDURE get_professional_by_user_id(p_user_id IN NUMBER, o_professional OUT SYS_REFCURSOR);

    -- Profesionales con el registro médico indicado.
    FUNCTION count_by_medical_registry(p_medical_registry IN VARCHAR2) RETURN NUMBER;

    -- Profesionales distintos de p_public_id con el registro médico indicado.
    FUNCTION count_other_by_medical_registry(p_medical_registry IN VARCHAR2, p_public_id IN VARCHAR2) RETURN NUMBER;

    -- Profesionales con el tipo y número de documento indicados.
    FUNCTION count_by_document(p_document_type IN VARCHAR2, p_document_number IN VARCHAR2) RETURN NUMBER;

    -- Profesionales distintos de p_public_id con el tipo y número de documento indicados.
    FUNCTION count_other_by_document(p_document_type IN VARCHAR2, p_document_number IN VARCHAR2,
                                     p_public_id IN VARCHAR2) RETURN NUMBER;

    -- Página de profesionales por apellidos, nombres e ID. Filtros opcionales (nulo = sin filtro). Columnas:
    -- PUBLIC_ID, USUARIO_PUBLIC_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, REGISTRO_MEDICO, NOMBRES, APELLIDOS, EMAIL,
    -- TELEFONO, ESPECIALIDAD_PUBLIC_ID, ESPECIALIDAD_NOMBRE, ESTADO, DEBE_CAMBIAR_PASSWORD, CREATED_AT.
    PROCEDURE list_professionals(p_specialty_public_id IN VARCHAR2, p_status IN VARCHAR2, p_offset IN NUMBER,
                                 p_size IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Total de profesionales con los mismos filtros de list_professionals.
    FUNCTION count_professionals(p_specialty_public_id IN VARCHAR2, p_status IN VARCHAR2) RETURN NUMBER;

    -- ----------------------------------------------------------- ESPECIALIDAD
    -- Crea una especialidad ACTIVO y devuelve su ID interno.
    PROCEDURE create_specialty(p_public_id IN VARCHAR2, p_name IN VARCHAR2, p_slot_minutes IN NUMBER,
                               o_specialty_id OUT NUMBER);

    -- Actualiza nombre y duración del cupo de la especialidad.
    PROCEDURE update_specialty(p_public_id IN VARCHAR2, p_name IN VARCHAR2, p_slot_minutes IN NUMBER);

    -- Especialidad por identificador público (0 o 1 fila): ID, PUBLIC_ID, NOMBRE, DURACION_SLOT_MIN, ESTADO.
    PROCEDURE get_specialty_by_public_id(p_public_id IN VARCHAR2, o_specialty OUT SYS_REFCURSOR);

    -- Especialidad por ID interno (mismas columnas).
    PROCEDURE get_specialty_by_id(p_specialty_id IN NUMBER, o_specialty OUT SYS_REFCURSOR);

    -- Especialidades con el nombre indicado.
    FUNCTION count_specialties_by_name(p_name IN VARCHAR2) RETURN NUMBER;

    -- Especialidades distintas de p_public_id con el nombre indicado.
    FUNCTION count_other_specialties_by_name(p_name IN VARCHAR2, p_public_id IN VARCHAR2) RETURN NUMBER;

    -- Cambia el estado de la especialidad.
    PROCEDURE change_specialty_status(p_public_id IN VARCHAR2, p_status IN VARCHAR2);

    -- Página de especialidades por nombre e ID; p_status nulo = todas (mismas columnas que get_specialty_by_id).
    PROCEDURE list_specialties(p_status IN VARCHAR2, p_offset IN NUMBER, p_size IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Total de especialidades; p_status nulo = todas.
    FUNCTION count_specialties(p_status IN VARCHAR2) RETURN NUMBER;

    -- ------------------------------------------------- ASIGNACION_ASISTENCIAL
    -- Crea una asignación asistencial y devuelve su ID interno.
    PROCEDURE create_assignment(p_public_id IN VARCHAR2, p_episode_id IN NUMBER, p_professional_id IN NUMBER,
                                p_role IN VARCHAR2, p_assigned_by_user_id IN NUMBER,
                                p_start_at IN TIMESTAMP WITH TIME ZONE, p_active IN NUMBER,
                                o_assignment_id OUT NUMBER);

    -- Inactiva la asignación activa del episodio para la función indicada; o_updated = filas inactivadas.
    PROCEDURE deactivate_previous_assignment(p_episode_id IN NUMBER, p_role IN VARCHAR2, o_updated OUT NUMBER);

    -- Asignaciones activas del profesional en el episodio.
    FUNCTION count_active_assignments(p_professional_id IN NUMBER, p_episode_id IN NUMBER) RETURN NUMBER;

    -- Asignaciones del episodio por fecha de inicio: ID, PUBLIC_ID, EPISODIO_ID, PROFESIONAL_ID, FUNCION,
    -- ASIGNADO_POR_USUARIO_ID, FECHA_INICIO, FECHA_FIN, ACTIVO, CREADO_AT.
    PROCEDURE list_assignments_by_episode(p_episode_id IN NUMBER, o_rows OUT SYS_REFCURSOR);
END PKG_STAFF;
/
