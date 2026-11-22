-- =============================================================================
-- MediTriaje - R__pkg_2_body_pharmacy: cuerpo de PKG_PHARMACY
-- Repetible. Sin COMMIT ni ROLLBACK (la transacción pertenece a la capa de aplicación).
-- =============================================================================
CREATE OR REPLACE PACKAGE BODY PKG_PHARMACY AS

    -- ------------------------------------------------------------- MEDICAMENTO
    PROCEDURE get_medication_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, CODIGO, NOMBRE_COMERCIAL, PRINCIPIO_ACTIVO, PRESENTACION, CONCENTRACION, ESTADO
              FROM MEDICAMENTO
             WHERE PUBLIC_ID = p_public_id;
    END get_medication_by_public_id;

    PROCEDURE get_medication_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, CODIGO, NOMBRE_COMERCIAL, PRINCIPIO_ACTIVO, PRESENTACION, CONCENTRACION, ESTADO
              FROM MEDICAMENTO
             WHERE ID = p_id;
    END get_medication_by_id;

    PROCEDURE list_active_medications(p_pattern IN VARCHAR2, p_offset IN NUMBER, p_size IN NUMBER,
                                      o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT PUBLIC_ID, CODIGO, NOMBRE_COMERCIAL, PRINCIPIO_ACTIVO, PRESENTACION, CONCENTRACION, ESTADO
              FROM MEDICAMENTO
             WHERE ESTADO = 'ACTIVO'
               AND (p_pattern IS NULL
                    OR UPPER(NOMBRE_COMERCIAL) LIKE p_pattern
                    OR UPPER(PRINCIPIO_ACTIVO) LIKE p_pattern
                    OR UPPER(CODIGO) LIKE p_pattern)
             ORDER BY NOMBRE_COMERCIAL ASC, CODIGO ASC
            OFFSET p_offset ROWS FETCH NEXT p_size ROWS ONLY;
    END list_active_medications;

    FUNCTION count_active_medications(p_pattern IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM MEDICAMENTO
         WHERE ESTADO = 'ACTIVO'
           AND (p_pattern IS NULL
                OR UPPER(NOMBRE_COMERCIAL) LIKE p_pattern
                OR UPPER(PRINCIPIO_ACTIVO) LIKE p_pattern
                OR UPPER(CODIGO) LIKE p_pattern);
        RETURN v_count;
    END count_active_medications;

    -- ------------------------------------------------------------------ RECETA
    PROCEDURE create_prescription(p_public_id IN VARCHAR2, p_encounter_id IN NUMBER, p_patient_id IN NUMBER,
                                  p_professional_id IN NUMBER, p_validity_days IN NUMBER, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO RECETA (PUBLIC_ID, ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID, VIGENCIA_DIAS)
        VALUES (p_public_id, p_encounter_id, p_patient_id, p_professional_id, p_validity_days)
        RETURNING ID INTO o_id;
    END create_prescription;

    PROCEDURE create_prescription_item(p_prescription_id IN NUMBER, p_medication_id IN NUMBER,
                                       p_snapshot_name IN VARCHAR2, p_snapshot_active_ingredient IN VARCHAR2,
                                       p_snapshot_presentation IN VARCHAR2, p_snapshot_concentration IN VARCHAR2,
                                       p_dose IN VARCHAR2, p_frequency IN VARCHAR2, p_duration_days IN NUMBER,
                                       p_quantity IN NUMBER, p_instructions IN VARCHAR2) IS
    BEGIN
        INSERT INTO RECETA_DETALLE (
            RECETA_ID, MEDICAMENTO_ID, SNAPSHOT_NOMBRE, SNAPSHOT_PRINCIPIO_ACTIVO,
            SNAPSHOT_PRESENTACION, SNAPSHOT_CONCENTRACION, DOSIS, FRECUENCIA,
            DURACION_DIAS, CANTIDAD, INDICACIONES
        )
        VALUES (
            p_prescription_id, p_medication_id, p_snapshot_name, p_snapshot_active_ingredient,
            p_snapshot_presentation, p_snapshot_concentration, p_dose, p_frequency,
            p_duration_days, p_quantity, p_instructions
        );
    END create_prescription_item;

    PROCEDURE get_prescription_view_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT r.ID AS RECETA_ID,
                   r.PUBLIC_ID AS RECETA_PUBLIC_ID,
                   a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   TRIM(p.NOMBRES || ' ' || p.APELLIDOS) AS PACIENTE_NOMBRE,
                   pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   TRIM(pr.NOMBRES || ' ' || pr.APELLIDOS) AS PROFESIONAL_NOMBRE,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   r.VIGENCIA_DIAS,
                   r.CREATED_AT
              FROM RECETA r
              JOIN ATENCION a ON r.ATENCION_ID = a.ID
              JOIN PACIENTE p ON r.PACIENTE_ID = p.ID
              JOIN PROFESIONAL pr ON r.PROFESIONAL_ID = pr.ID
              JOIN CITA c ON a.CITA_ID = c.ID
              JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
              JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
             WHERE r.PUBLIC_ID = p_public_id;
    END get_prescription_view_by_public_id;

    PROCEDURE get_prescription_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID, VIGENCIA_DIAS, CREATED_AT
              FROM RECETA
             WHERE PUBLIC_ID = p_public_id;
    END get_prescription_by_public_id;

    PROCEDURE list_prescription_items(p_prescription_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT m.PUBLIC_ID AS MEDICAMENTO_PUBLIC_ID,
                   m.CODIGO AS MEDICAMENTO_CODIGO,
                   rd.SNAPSHOT_NOMBRE,
                   rd.SNAPSHOT_PRINCIPIO_ACTIVO,
                   rd.SNAPSHOT_PRESENTACION,
                   rd.SNAPSHOT_CONCENTRACION,
                   rd.DOSIS,
                   rd.FRECUENCIA,
                   rd.DURACION_DIAS,
                   rd.CANTIDAD,
                   rd.INDICACIONES
              FROM RECETA_DETALLE rd
              JOIN MEDICAMENTO m ON rd.MEDICAMENTO_ID = m.ID
             WHERE rd.RECETA_ID = p_prescription_id
             ORDER BY rd.ID ASC;
    END list_prescription_items;

    FUNCTION count_prescriptions_by_encounter(p_encounter_id IN NUMBER) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM RECETA WHERE ATENCION_ID = p_encounter_id;
        RETURN v_count;
    END count_prescriptions_by_encounter;

    PROCEDURE list_prescriptions_by_patient(p_patient_id IN NUMBER, p_offset IN NUMBER, p_size IN NUMBER,
                                            o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT r.ID AS RECETA_ID,
                   r.PUBLIC_ID AS RECETA_PUBLIC_ID,
                   a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   TRIM(p.NOMBRES || ' ' || p.APELLIDOS) AS PACIENTE_NOMBRE,
                   pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   TRIM(pr.NOMBRES || ' ' || pr.APELLIDOS) AS PROFESIONAL_NOMBRE,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   r.VIGENCIA_DIAS,
                   r.CREATED_AT
              FROM RECETA r
              JOIN ATENCION a ON r.ATENCION_ID = a.ID
              JOIN PACIENTE p ON r.PACIENTE_ID = p.ID
              JOIN PROFESIONAL pr ON r.PROFESIONAL_ID = pr.ID
              JOIN CITA c ON a.CITA_ID = c.ID
              JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
              JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
             WHERE r.PACIENTE_ID = p_patient_id
             ORDER BY r.CREATED_AT DESC, r.ID DESC
            OFFSET p_offset ROWS FETCH NEXT p_size ROWS ONLY;
    END list_prescriptions_by_patient;

    FUNCTION count_prescriptions_by_patient(p_patient_id IN NUMBER) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM RECETA WHERE PACIENTE_ID = p_patient_id;
        RETURN v_count;
    END count_prescriptions_by_patient;

    -- ------------------------------------------------------------ DISPENSACION
    PROCEDURE create_dispensation(p_public_id IN VARCHAR2, p_prescription_id IN NUMBER, p_user_id IN NUMBER,
                                  p_notes IN VARCHAR2, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO DISPENSACION (PUBLIC_ID, RECETA_ID, USUARIO_ID, OBSERVACIONES)
        VALUES (p_public_id, p_prescription_id, p_user_id, p_notes)
        RETURNING ID INTO o_id;
    END create_dispensation;

    PROCEDURE create_dispensation_item(p_dispensation_id IN NUMBER, p_prescription_item_id IN NUMBER,
                                       p_delivered_quantity IN NUMBER, p_batch IN VARCHAR2,
                                       p_batch_expiry_date IN DATE) IS
    BEGIN
        INSERT INTO DISPENSACION_DETALLE (
            DISPENSACION_ID, RECETA_DETALLE_ID, CANTIDAD_ENTREGADA, LOTE, FECHA_VENCIMIENTO_LOTE
        )
        VALUES (
            p_dispensation_id, p_prescription_item_id, p_delivered_quantity, p_batch, p_batch_expiry_date
        );
    END create_dispensation_item;

    PROCEDURE get_dispensation_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, RECETA_ID, USUARIO_ID, OBSERVACIONES, CREATED_AT
              FROM DISPENSACION
             WHERE PUBLIC_ID = p_public_id;
    END get_dispensation_by_public_id;

    PROCEDURE get_dispensation_view_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT d.ID AS DISPENSACION_ID,
                   d.PUBLIC_ID AS DISPENSACION_PUBLIC_ID,
                   r.PUBLIC_ID AS RECETA_PUBLIC_ID,
                   cfg.PUBLIC_ID AS SEDE_PUBLIC_ID,
                   cfg.RAZON_SOCIAL AS SEDE_NOMBRE,
                   u.PUBLIC_ID AS DISPENSADOR_PUBLIC_ID,
                   COALESCE(TRIM(pr.NOMBRES || ' ' || pr.APELLIDOS), u.EMAIL) AS DISPENSADOR_NOMBRE,
                   d.OBSERVACIONES,
                   d.CREATED_AT
              FROM DISPENSACION d
              JOIN RECETA r ON d.RECETA_ID = r.ID
              CROSS JOIN CONFIGURACION_HOSPITAL cfg
              JOIN USUARIO u ON d.USUARIO_ID = u.ID
              LEFT JOIN PROFESIONAL pr ON u.ID = pr.USUARIO_ID
             WHERE d.PUBLIC_ID = p_public_id;
    END get_dispensation_view_by_public_id;

    PROCEDURE list_dispensation_items(p_dispensation_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT m.PUBLIC_ID AS MEDICAMENTO_PUBLIC_ID,
                   m.CODIGO AS MEDICAMENTO_CODIGO,
                   rd.SNAPSHOT_NOMBRE AS NOMBRE_COMERCIAL,
                   dd.CANTIDAD_ENTREGADA,
                   dd.LOTE,
                   dd.FECHA_VENCIMIENTO_LOTE,
                   dd.CREATED_AT
              FROM DISPENSACION_DETALLE dd
              JOIN RECETA_DETALLE rd ON dd.RECETA_DETALLE_ID = rd.ID
              JOIN MEDICAMENTO m ON rd.MEDICAMENTO_ID = m.ID
             WHERE dd.DISPENSACION_ID = p_dispensation_id
             ORDER BY dd.ID ASC;
    END list_dispensation_items;

    PROCEDURE list_dispensations_by_prescription(p_prescription_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT d.ID AS DISPENSACION_ID,
                   d.PUBLIC_ID AS DISPENSACION_PUBLIC_ID,
                   r.PUBLIC_ID AS RECETA_PUBLIC_ID,
                   cfg.PUBLIC_ID AS SEDE_PUBLIC_ID,
                   cfg.RAZON_SOCIAL AS SEDE_NOMBRE,
                   u.PUBLIC_ID AS DISPENSADOR_PUBLIC_ID,
                   COALESCE(TRIM(pr.NOMBRES || ' ' || pr.APELLIDOS), u.EMAIL) AS DISPENSADOR_NOMBRE,
                   d.OBSERVACIONES,
                   d.CREATED_AT
              FROM DISPENSACION d
              JOIN RECETA r ON d.RECETA_ID = r.ID
              CROSS JOIN CONFIGURACION_HOSPITAL cfg
              JOIN USUARIO u ON d.USUARIO_ID = u.ID
              LEFT JOIN PROFESIONAL pr ON u.ID = pr.USUARIO_ID
             WHERE d.RECETA_ID = p_prescription_id
             ORDER BY d.CREATED_AT ASC, d.ID ASC;
    END list_dispensations_by_prescription;

    PROCEDURE list_dispensed_totals(p_prescription_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT dd.RECETA_DETALLE_ID, SUM(dd.CANTIDAD_ENTREGADA) AS TOTAL_ENTREGADO
              FROM DISPENSACION_DETALLE dd
              JOIN DISPENSACION d ON dd.DISPENSACION_ID = d.ID
             WHERE d.RECETA_ID = p_prescription_id
             GROUP BY dd.RECETA_DETALLE_ID;
    END list_dispensed_totals;

    PROCEDURE list_prescription_balances(p_prescription_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT rd.ID AS RECETA_DETALLE_ID,
                   m.PUBLIC_ID AS MEDICAMENTO_PUBLIC_ID,
                   m.CODIGO AS MEDICAMENTO_CODIGO,
                   rd.SNAPSHOT_NOMBRE,
                   rd.SNAPSHOT_PRINCIPIO_ACTIVO,
                   rd.SNAPSHOT_PRESENTACION,
                   rd.SNAPSHOT_CONCENTRACION,
                   rd.DOSIS,
                   rd.FRECUENCIA,
                   rd.DURACION_DIAS,
                   rd.CANTIDAD AS CANTIDAD_PRESCRITA,
                   COALESCE(disp.TOTAL_ENTREGADO, 0) AS CANTIDAD_DISPENSADA,
                   rd.INDICACIONES
              FROM RECETA_DETALLE rd
              JOIN MEDICAMENTO m ON rd.MEDICAMENTO_ID = m.ID
              LEFT JOIN (
                  SELECT dd.RECETA_DETALLE_ID, SUM(dd.CANTIDAD_ENTREGADA) AS TOTAL_ENTREGADO
                    FROM DISPENSACION_DETALLE dd
                    JOIN DISPENSACION d ON dd.DISPENSACION_ID = d.ID
                   WHERE d.RECETA_ID = p_prescription_id
                   GROUP BY dd.RECETA_DETALLE_ID
              ) disp ON rd.ID = disp.RECETA_DETALLE_ID
             WHERE rd.RECETA_ID = p_prescription_id
             ORDER BY rd.ID ASC;
    END list_prescription_balances;

    PROCEDURE list_prescribed_items(p_prescription_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT rd.ID AS RECETA_DETALLE_ID,
                   m.PUBLIC_ID AS MEDICAMENTO_PUBLIC_ID,
                   rd.SNAPSHOT_NOMBRE,
                   rd.CANTIDAD AS CANTIDAD_PRESCRITA
              FROM RECETA_DETALLE rd
              JOIN MEDICAMENTO m ON rd.MEDICAMENTO_ID = m.ID
             WHERE rd.RECETA_ID = p_prescription_id
             ORDER BY rd.ID ASC;
    END list_prescribed_items;

    PROCEDURE get_dispensing_prescription(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT r.ID AS RECETA_ID,
                   r.PUBLIC_ID AS RECETA_PUBLIC_ID,
                   a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   p.TIPO_DOCUMENTO || ' ' || p.NUMERO_DOCUMENTO AS PACIENTE_DOCUMENTO,
                   TRIM(p.NOMBRES || ' ' || p.APELLIDOS) AS PACIENTE_NOMBRE,
                   pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   TRIM(pr.NOMBRES || ' ' || pr.APELLIDOS) AS PROFESIONAL_NOMBRE,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   r.VIGENCIA_DIAS,
                   r.CREATED_AT
              FROM RECETA r
              JOIN ATENCION a ON r.ATENCION_ID = a.ID
              JOIN PACIENTE p ON r.PACIENTE_ID = p.ID
              JOIN PROFESIONAL pr ON r.PROFESIONAL_ID = pr.ID
              JOIN CITA c ON a.CITA_ID = c.ID
              JOIN DISPONIBILIDAD_SLOT sl ON c.SLOT_ID = sl.ID
              JOIN ESPECIALIDAD e ON sl.ESPECIALIDAD_ID = e.ID
             WHERE r.PUBLIC_ID = p_public_id;
    END get_dispensing_prescription;

    PROCEDURE search_dispensing_prescriptions(p_term IN VARCHAR2, p_pattern IN VARCHAR2, p_id_pattern IN VARCHAR2,
                                              p_offset IN NUMBER, p_size IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT r.ID AS RECETA_ID,
                   r.PUBLIC_ID AS RECETA_PUBLIC_ID,
                   a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   p.TIPO_DOCUMENTO || ' ' || p.NUMERO_DOCUMENTO AS PACIENTE_DOCUMENTO,
                   TRIM(p.NOMBRES || ' ' || p.APELLIDOS) AS PACIENTE_NOMBRE,
                   pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   TRIM(pr.NOMBRES || ' ' || pr.APELLIDOS) AS PROFESIONAL_NOMBRE,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   r.VIGENCIA_DIAS,
                   r.CREATED_AT
              FROM RECETA r
              JOIN ATENCION a ON r.ATENCION_ID = a.ID
              JOIN PACIENTE p ON r.PACIENTE_ID = p.ID
              JOIN PROFESIONAL pr ON r.PROFESIONAL_ID = pr.ID
              JOIN CITA c ON a.CITA_ID = c.ID
              JOIN DISPONIBILIDAD_SLOT sl ON c.SLOT_ID = sl.ID
              JOIN ESPECIALIDAD e ON sl.ESPECIALIDAD_ID = e.ID
             WHERE (p_term IS NULL
                    OR LOWER(p.NUMERO_DOCUMENTO) LIKE LOWER(p_pattern)
                    OR LOWER(r.PUBLIC_ID) LIKE LOWER(p_id_pattern)
                    OR LOWER(TRIM(p.NOMBRES || ' ' || p.APELLIDOS)) LIKE LOWER(p_pattern))
             ORDER BY r.CREATED_AT DESC, r.ID DESC
            OFFSET p_offset ROWS FETCH NEXT p_size ROWS ONLY;
    END search_dispensing_prescriptions;

    FUNCTION count_dispensing_prescriptions(p_term IN VARCHAR2, p_pattern IN VARCHAR2,
                                            p_id_pattern IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM RECETA r
          JOIN PACIENTE p ON r.PACIENTE_ID = p.ID
         WHERE (p_term IS NULL
                OR LOWER(p.NUMERO_DOCUMENTO) LIKE LOWER(p_pattern)
                OR LOWER(r.PUBLIC_ID) LIKE LOWER(p_id_pattern)
                OR LOWER(TRIM(p.NOMBRES || ' ' || p.APELLIDOS)) LIKE LOWER(p_pattern));
        RETURN v_count;
    END count_dispensing_prescriptions;
END PKG_PHARMACY;
/
