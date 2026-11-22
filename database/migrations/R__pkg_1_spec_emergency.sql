-- =============================================================================
-- MediTriaje - R__pkg_1_spec_emergency: especificación de PKG_EMERGENCY
-- Repetible. Urgencias: episodios de atención (EPISODIO_ATENCION), ingresos a urgencias (INGRESO_URGENCIA),
-- detalle del episodio y cola priorizada de urgencias (V018).
-- Derechos del definidor; nunca ejecuta COMMIT ni ROLLBACK (la transacción es de Java).
-- Las consultas de un solo registro devuelven un cursor con 0 o 1 fila.
-- =============================================================================
CREATE OR REPLACE PACKAGE PKG_EMERGENCY AUTHID DEFINER AS

    -- ---------------------------------------------------------------- Episodios
    -- Registra un episodio de atención en la única sede del hospital y devuelve su ID interno.
    PROCEDURE create_episode(p_public_id IN VARCHAR2, p_patient_id IN NUMBER, p_type IN VARCHAR2,
                             p_status IN VARCHAR2, p_admitted_at IN TIMESTAMP WITH TIME ZONE,
                             p_reason IN VARCHAR2, o_id OUT NUMBER);
    -- Episodio por ID interno (todas las columnas de EPISODIO_ATENCION).
    PROCEDURE get_episode_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    -- Episodio por identificador público.
    PROCEDURE get_episode_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);
    -- Cambia el estado del episodio; o_updated = filas afectadas.
    PROCEDURE update_episode_status(p_id IN NUMBER, p_status IN VARCHAR2, o_updated OUT NUMBER);
    -- Vincula el paciente identificado al episodio; o_updated = filas afectadas.
    PROCEDURE link_patient(p_id IN NUMBER, p_patient_id IN NUMBER, o_updated OUT NUMBER);
    -- Cierra el episodio (EGRESADO) con la fecha de egreso indicada; o_updated = filas afectadas.
    PROCEDURE close_episode(p_id IN NUMBER, p_discharged_at IN TIMESTAMP WITH TIME ZONE, o_updated OUT NUMBER);
    -- Detalle del episodio: paciente, sede, identidad provisional, ingreso, triaje vigente y médico tratante.
    PROCEDURE get_episode_detail(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);
    -- Cola priorizada de urgencias abiertas (nivel I..V, luego pendientes; a igual nivel, por llegada).
    -- p_site_id y p_status son filtros opcionales (NULL = sin filtro).
    PROCEDURE list_emergency_queue(p_site_id IN NUMBER, p_status IN VARCHAR2, o_rows OUT SYS_REFCURSOR);

    -- ---------------------------------------------------------------- Ingresos a urgencias
    -- Registra el ingreso presencial a urgencias de un episodio y devuelve su ID interno.
    PROCEDURE create_admission(p_public_id IN VARCHAR2, p_episode_id IN NUMBER, p_arrival_mode IN VARCHAR2,
                               p_companion_name IN VARCHAR2, p_companion_contact IN VARCHAR2,
                               p_reason IN VARCHAR2, p_observations IN VARCHAR2,
                               p_registered_by_user_id IN NUMBER, o_id OUT NUMBER);
    -- Ingreso a urgencias de un episodio.
    PROCEDURE get_admission_by_episode(p_episode_id IN NUMBER, o_row OUT SYS_REFCURSOR);
END PKG_EMERGENCY;
/
