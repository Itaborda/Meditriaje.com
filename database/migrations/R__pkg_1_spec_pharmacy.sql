-- =============================================================================
-- MediTriaje - R__pkg_1_spec_pharmacy: especificación de PKG_PHARMACY
-- Repetible. Catálogo de medicamentos (MEDICAMENTO), recetas (RECETA, RECETA_DETALLE) y dispensación
-- farmacéutica (DISPENSACION, DISPENSACION_DETALLE). Los triggers de inmutabilidad siguen activos.
-- Derechos del definidor; nunca ejecuta COMMIT ni ROLLBACK (la transacción es de Java).
-- Los cursores de 0 o 1 fila los consume el repositorio con PackageGateway.single o tomando la primera fila.
-- =============================================================================
CREATE OR REPLACE PACKAGE PKG_PHARMACY AUTHID DEFINER AS

    -- ------------------------------------------------------------- MEDICAMENTO
    -- Medicamento por PUBLIC_ID o por ID interno. Columnas: ID, PUBLIC_ID, CODIGO, NOMBRE_COMERCIAL,
    -- PRINCIPIO_ACTIVO, PRESENTACION, CONCENTRACION, ESTADO.
    PROCEDURE get_medication_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);
    PROCEDURE get_medication_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);

    -- Medicamentos ACTIVO paginados, ordenados por nombre comercial y código. Si p_pattern no es nulo filtra
    -- por UPPER(NOMBRE_COMERCIAL), UPPER(PRINCIPIO_ACTIVO) o UPPER(CODIGO) LIKE p_pattern.
    PROCEDURE list_active_medications(p_pattern IN VARCHAR2, p_offset IN NUMBER, p_size IN NUMBER,
                                      o_rows OUT SYS_REFCURSOR);

    -- Total de medicamentos ACTIVO con el mismo filtro opcional de list_active_medications.
    FUNCTION count_active_medications(p_pattern IN VARCHAR2) RETURN NUMBER;

    -- ------------------------------------------------------------------ RECETA
    -- Registra la cabecera de una receta y devuelve su ID interno.
    PROCEDURE create_prescription(p_public_id IN VARCHAR2, p_encounter_id IN NUMBER, p_patient_id IN NUMBER,
                                  p_professional_id IN NUMBER, p_validity_days IN NUMBER, o_id OUT NUMBER);

    -- Registra un ítem de receta con los datos del medicamento congelados (SNAPSHOT_*).
    PROCEDURE create_prescription_item(p_prescription_id IN NUMBER, p_medication_id IN NUMBER,
                                       p_snapshot_name IN VARCHAR2, p_snapshot_active_ingredient IN VARCHAR2,
                                       p_snapshot_presentation IN VARCHAR2, p_snapshot_concentration IN VARCHAR2,
                                       p_dose IN VARCHAR2, p_frequency IN VARCHAR2, p_duration_days IN NUMBER,
                                       p_quantity IN NUMBER, p_instructions IN VARCHAR2);

    -- Vista de la receta (atención, paciente, profesional, especialidad) por PUBLIC_ID.
    PROCEDURE get_prescription_view_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);

    -- Receta (fila de RECETA) por PUBLIC_ID.
    PROCEDURE get_prescription_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);

    -- Ítems de una receta (datos congelados) en orden de registro.
    PROCEDURE list_prescription_items(p_prescription_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Cantidad de recetas emitidas para la atención.
    FUNCTION count_prescriptions_by_encounter(p_encounter_id IN NUMBER) RETURN NUMBER;

    -- Recetas paginadas del paciente (más recientes primero), con la misma vista de
    -- get_prescription_view_by_public_id.
    PROCEDURE list_prescriptions_by_patient(p_patient_id IN NUMBER, p_offset IN NUMBER, p_size IN NUMBER,
                                            o_rows OUT SYS_REFCURSOR);

    -- Total de recetas del paciente.
    FUNCTION count_prescriptions_by_patient(p_patient_id IN NUMBER) RETURN NUMBER;

    -- ------------------------------------------------------------ DISPENSACION
    -- Registra la cabecera de una dispensación en la única sede del hospital y devuelve su ID interno.
    PROCEDURE create_dispensation(p_public_id IN VARCHAR2, p_prescription_id IN NUMBER, p_user_id IN NUMBER,
                                  p_notes IN VARCHAR2, o_id OUT NUMBER);

    -- Registra un ítem entregado (lote y vencimiento opcional).
    PROCEDURE create_dispensation_item(p_dispensation_id IN NUMBER, p_prescription_item_id IN NUMBER,
                                       p_delivered_quantity IN NUMBER, p_batch IN VARCHAR2,
                                       p_batch_expiry_date IN DATE);

    -- Dispensación (fila de DISPENSACION) por PUBLIC_ID.
    PROCEDURE get_dispensation_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);

    -- Vista de la dispensación (receta, sede y dispensador) por PUBLIC_ID.
    PROCEDURE get_dispensation_view_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);

    -- Ítems entregados en una dispensación en orden de registro.
    PROCEDURE list_dispensation_items(p_dispensation_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Dispensaciones de una receta en orden cronológico, con la misma vista de
    -- get_dispensation_view_by_public_id.
    PROCEDURE list_dispensations_by_prescription(p_prescription_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Total entregado por ítem de receta. Columnas: RECETA_DETALLE_ID, TOTAL_ENTREGADO.
    PROCEDURE list_dispensed_totals(p_prescription_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Ítems de la receta con cantidad prescrita y cantidad ya dispensada (CANTIDAD_DISPENSADA).
    PROCEDURE list_prescription_balances(p_prescription_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Ítems prescritos (ID de detalle, medicamento, nombre congelado y cantidad prescrita).
    PROCEDURE list_prescribed_items(p_prescription_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Vista de receta para la ventanilla de farmacia por PUBLIC_ID.
    PROCEDURE get_dispensing_prescription(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);

    -- Recetas para la ventanilla de farmacia paginadas (más recientes primero). p_term nulo no filtra; si no,
    -- filtra por documento o nombre del paciente (LIKE p_pattern) o PUBLIC_ID de la receta (LIKE p_id_pattern),
    -- sin distinguir mayúsculas.
    PROCEDURE search_dispensing_prescriptions(p_term IN VARCHAR2, p_pattern IN VARCHAR2, p_id_pattern IN VARCHAR2,
                                              p_offset IN NUMBER, p_size IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Total de recetas con el mismo filtro de search_dispensing_prescriptions.
    FUNCTION count_dispensing_prescriptions(p_term IN VARCHAR2, p_pattern IN VARCHAR2,
                                            p_id_pattern IN VARCHAR2) RETURN NUMBER;
END PKG_PHARMACY;
/
