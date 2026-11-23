-- =============================================================================
-- MediTriaje - R__pkg_1_spec_triage: especificación de PKG_TRIAGE
-- Repetible. Triaje: autotriaje del paciente (TRIAJE, TRIAJE_SINTOMA), catálogo de síntomas y reglas
-- activas (SINTOMA, REGLA_TRIAJE) y valoraciones clínicas presenciales de urgencias (VALORACION_TRIAJE,
-- inmutable y append-only por el trigger TR_VAL_TRIAJE_INMUTABLE).
-- Derechos del definidor; nunca ejecuta COMMIT ni ROLLBACK (la transacción es de Java).
-- Las consultas de un solo registro devuelven un cursor con 0 o 1 fila.
-- =============================================================================
CREATE OR REPLACE PACKAGE PKG_TRIAGE AUTHID DEFINER AS

    -- ---------------------------------------------------------------- Autotriaje
    -- Registra un triaje evaluado y devuelve su ID interno. p_is_emergency: 1 = emergencia, 0 = no.
    PROCEDURE create_triage(p_public_id IN VARCHAR2, p_patient_id IN NUMBER, p_rules_version IN VARCHAR2,
                            p_priority_level IN VARCHAR2, p_suggested_route IN VARCHAR2,
                            p_is_emergency IN NUMBER, p_observations IN VARCHAR2, o_id OUT NUMBER);
    -- Registra un síntoma reportado en un triaje.
    PROCEDURE add_triage_symptom(p_triage_id IN NUMBER, p_symptom_id IN NUMBER, p_duration_hours IN NUMBER,
                                 p_intensity IN NUMBER);
    -- Triaje consolidado por identificador público: una fila por síntoma (o una sola fila sin síntoma),
    -- ordenadas por nombre del síntoma.
    PROCEDURE get_triage_detail(p_public_id IN VARCHAR2, o_rows OUT SYS_REFCURSOR);
    -- Triaje por identificador público.
    PROCEDURE get_triage_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);
    -- Triaje por ID interno.
    PROCEDURE get_triage_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);

    -- ---------------------------------------------------------------- Catálogo y reglas
    -- Catálogo de síntomas activos ordenado por categoría y nombre.
    PROCEDURE list_active_symptom_catalog(o_rows OUT SYS_REFCURSOR);
    -- Código e ID interno de los síntomas activos.
    PROCEDURE list_active_symptom_ids(o_rows OUT SYS_REFCURSOR);
    -- Código y marca de alarma de los síntomas activos, ordenados por código (motor de reglas).
    PROCEDURE list_active_symptoms(o_rows OUT SYS_REFCURSOR);
    -- Reglas activas de una versión, con el código del síntoma, ordenadas por código e intensidad mínima.
    PROCEDURE list_active_rules(p_rules_version IN VARCHAR2, o_rows OUT SYS_REFCURSOR);

    -- ---------------------------------------------------------------- Valoraciones de urgencias
    -- Siguiente número de versión de la valoración de un episodio (1 si no hay ninguna).
    FUNCTION get_next_assessment_version(p_episode_id IN NUMBER) RETURN NUMBER;
    -- Registra una valoración clínica (append-only) y devuelve su ID interno. p_is_reassessment: 1/0.
    PROCEDURE create_assessment(p_public_id IN VARCHAR2, p_episode_id IN NUMBER, p_version IN NUMBER,
                                p_level IN VARCHAR2, p_chief_complaint IN VARCHAR2, p_findings IN VARCHAR2,
                                p_blood_pressure IN VARCHAR2, p_heart_rate IN NUMBER,
                                p_respiratory_rate IN NUMBER, p_oxygen_saturation IN NUMBER,
                                p_temperature IN NUMBER, p_glasgow IN NUMBER, p_evaluator_user_id IN NUMBER,
                                p_is_reassessment IN NUMBER, p_reassessment_reason IN VARCHAR2,
                                o_id OUT NUMBER);
    -- Valoraciones de un episodio por versión ascendente, con el nombre del evaluador.
    PROCEDURE list_assessments_by_episode(p_episode_id IN NUMBER, o_rows OUT SYS_REFCURSOR);
    -- Última valoración (versión más alta) de un episodio.
    PROCEDURE get_latest_assessment(p_episode_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    -- Registra un síntoma reportado en una valoración de triaje presencial.
    PROCEDURE add_assessment_symptom(p_assessment_id IN NUMBER, p_symptom_id IN NUMBER,
                                     p_duration_hours IN NUMBER, p_intensity IN NUMBER);
    -- Síntomas asociados a todas las valoraciones de un episodio, ordenados por versión y nombre.
    PROCEDURE list_assessment_symptoms(p_episode_id IN NUMBER, o_rows OUT SYS_REFCURSOR);
END PKG_TRIAGE;
/
