-- =============================================================================
-- MediTriaje - R__pkg_2_body_triage: cuerpo de PKG_TRIAGE
-- Repetible. Sin COMMIT ni ROLLBACK (la transacción pertenece a la capa de aplicación).
-- =============================================================================
CREATE OR REPLACE PACKAGE BODY PKG_TRIAGE AS

    -- ---------------------------------------------------------------- Autotriaje
    PROCEDURE create_triage(p_public_id IN VARCHAR2, p_patient_id IN NUMBER, p_rules_version IN VARCHAR2,
                            p_priority_level IN VARCHAR2, p_suggested_route IN VARCHAR2,
                            p_is_emergency IN NUMBER, p_observations IN VARCHAR2, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO TRIAJE (PUBLIC_ID, PACIENTE_ID, VERSION_REGLAS, NIVEL_PRIORIDAD, RUTA_SUGERIDA,
                            ES_EMERGENCIA, OBSERVACIONES)
        VALUES (p_public_id, p_patient_id, p_rules_version, p_priority_level, p_suggested_route,
                p_is_emergency, p_observations)
        RETURNING ID INTO o_id;
    END create_triage;

    PROCEDURE add_triage_symptom(p_triage_id IN NUMBER, p_symptom_id IN NUMBER, p_duration_hours IN NUMBER,
                                 p_intensity IN NUMBER) IS
    BEGIN
        INSERT INTO TRIAJE_SINTOMA (TRIAJE_ID, SINTOMA_ID, DURACION_HORAS, INTENSIDAD)
        VALUES (p_triage_id, p_symptom_id, p_duration_hours, p_intensity);
    END add_triage_symptom;

    PROCEDURE get_triage_detail(p_public_id IN VARCHAR2, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT t.ID AS TRIAJE_ID,
                   t.PUBLIC_ID AS TRIAJE_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   t.VERSION_REGLAS,
                   t.NIVEL_PRIORIDAD,
                   t.RUTA_SUGERIDA,
                   t.ES_EMERGENCIA,
                   t.OBSERVACIONES,
                   t.CREATED_AT,
                   s.CODIGO AS SINTOMA_CODIGO,
                   s.NOMBRE AS SINTOMA_NOMBRE,
                   ts.DURACION_HORAS,
                   ts.INTENSIDAD,
                   s.ES_ALARMA
              FROM TRIAJE t
              JOIN PACIENTE p ON t.PACIENTE_ID = p.ID
              LEFT JOIN TRIAJE_SINTOMA ts ON ts.TRIAJE_ID = t.ID
              LEFT JOIN SINTOMA s ON ts.SINTOMA_ID = s.ID
             WHERE t.PUBLIC_ID = p_public_id
             ORDER BY s.NOMBRE ASC;
    END get_triage_detail;

    PROCEDURE get_triage_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, PACIENTE_ID, VERSION_REGLAS, NIVEL_PRIORIDAD, RUTA_SUGERIDA,
                   ES_EMERGENCIA, OBSERVACIONES, CREATED_AT
              FROM TRIAJE
             WHERE PUBLIC_ID = p_public_id;
    END get_triage_by_public_id;

    PROCEDURE get_triage_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, PACIENTE_ID, VERSION_REGLAS, NIVEL_PRIORIDAD, RUTA_SUGERIDA,
                   ES_EMERGENCIA, OBSERVACIONES, CREATED_AT
              FROM TRIAJE
             WHERE ID = p_id;
    END get_triage_by_id;

    -- ---------------------------------------------------------------- Catálogo y reglas
    PROCEDURE list_active_symptom_catalog(o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT PUBLIC_ID, CODIGO, NOMBRE, CATEGORIA, ES_ALARMA
              FROM SINTOMA
             WHERE ESTADO = 'ACTIVO'
             ORDER BY CATEGORIA, NOMBRE;
    END list_active_symptom_catalog;

    PROCEDURE list_active_symptom_ids(o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR SELECT CODIGO, ID FROM SINTOMA WHERE ESTADO = 'ACTIVO';
    END list_active_symptom_ids;

    PROCEDURE list_active_symptoms(o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR SELECT CODIGO, ES_ALARMA FROM SINTOMA WHERE ESTADO = 'ACTIVO' ORDER BY CODIGO;
    END list_active_symptoms;

    PROCEDURE list_active_rules(p_rules_version IN VARCHAR2, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT S.CODIGO, R.DURACION_MIN_HORAS, R.DURACION_MAX_HORAS,
                   R.INTENSIDAD_MIN, R.INTENSIDAD_MAX, R.NIVEL_PRIORIDAD
              FROM REGLA_TRIAJE R
              JOIN SINTOMA S ON S.ID = R.SINTOMA_ID
             WHERE R.VERSION = p_rules_version AND R.ESTADO = 'ACTIVO' AND S.ESTADO = 'ACTIVO'
             ORDER BY S.CODIGO, R.INTENSIDAD_MIN;
    END list_active_rules;

    -- ---------------------------------------------------------------- Valoraciones de urgencias
    FUNCTION get_next_assessment_version(p_episode_id IN NUMBER) RETURN NUMBER IS
        v_version NUMBER;
    BEGIN
        SELECT COALESCE(MAX(VERSION), 0) + 1 INTO v_version FROM VALORACION_TRIAJE WHERE EPISODIO_ID = p_episode_id;
        RETURN v_version;
    END get_next_assessment_version;

    PROCEDURE create_assessment(p_public_id IN VARCHAR2, p_episode_id IN NUMBER, p_version IN NUMBER,
                                p_level IN VARCHAR2, p_chief_complaint IN VARCHAR2, p_findings IN VARCHAR2,
                                p_blood_pressure IN VARCHAR2, p_heart_rate IN NUMBER,
                                p_respiratory_rate IN NUMBER, p_oxygen_saturation IN NUMBER,
                                p_temperature IN NUMBER, p_glasgow IN NUMBER, p_evaluator_user_id IN NUMBER,
                                p_is_reassessment IN NUMBER, p_reassessment_reason IN VARCHAR2,
                                o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO VALORACION_TRIAJE (PUBLIC_ID, EPISODIO_ID, VERSION, NIVEL, MOTIVO_CONSULTA,
                                       HALLAZGOS_CLINICOS, PRESION_ARTERIAL, FRECUENCIA_CARDIACA,
                                       FRECUENCIA_RESPIRATORIA, SATURACION_OXIGENO, TEMPERATURA,
                                       ESCALA_GLASGOW, EVALUADOR_USUARIO_ID, ES_REEVALUACION,
                                       MOTIVO_REEVALUACION)
        VALUES (p_public_id, p_episode_id, p_version, p_level, p_chief_complaint, p_findings, p_blood_pressure,
                p_heart_rate, p_respiratory_rate, p_oxygen_saturation, p_temperature, p_glasgow,
                p_evaluator_user_id, p_is_reassessment, p_reassessment_reason)
        RETURNING ID INTO o_id;
    END create_assessment;

    PROCEDURE list_assessments_by_episode(p_episode_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT vt.PUBLIC_ID,
                   e.PUBLIC_ID AS EPISODIO_PUB_ID,
                   vt.VERSION,
                   vt.NIVEL,
                   vt.MOTIVO_CONSULTA,
                   vt.HALLAZGOS_CLINICOS,
                   vt.PRESION_ARTERIAL,
                   vt.FRECUENCIA_CARDIACA,
                   vt.FRECUENCIA_RESPIRATORIA,
                   vt.SATURACION_OXIGENO,
                   vt.TEMPERATURA,
                   vt.ESCALA_GLASGOW,
                   CASE WHEN prof.ID IS NOT NULL THEN (prof.NOMBRES || ' ' || prof.APELLIDOS) ELSE u.EMAIL END AS EVALUADOR_NOMBRE,
                   vt.ES_REEVALUACION,
                   vt.MOTIVO_REEVALUACION,
                   vt.CREADO_AT
              FROM VALORACION_TRIAJE vt
              JOIN EPISODIO_ATENCION e ON vt.EPISODIO_ID = e.ID
              JOIN USUARIO u ON vt.EVALUADOR_USUARIO_ID = u.ID
              LEFT JOIN PROFESIONAL prof ON prof.USUARIO_ID = u.ID
             WHERE vt.EPISODIO_ID = p_episode_id
             ORDER BY vt.VERSION ASC;
    END list_assessments_by_episode;

    PROCEDURE get_latest_assessment(p_episode_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT * FROM VALORACION_TRIAJE
             WHERE EPISODIO_ID = p_episode_id
             ORDER BY VERSION DESC
             FETCH FIRST 1 ROWS ONLY;
    END get_latest_assessment;
END PKG_TRIAGE;
/
