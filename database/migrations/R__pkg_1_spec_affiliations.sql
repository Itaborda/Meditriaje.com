-- =============================================================================
-- MediTriaje - R__pkg_1_spec_affiliations: especificación de PKG_AFFILIATIONS
-- Repetible. Entidades EPS (ENTIDAD_EPS), afiliaciones de pacientes (AFILIACION_PACIENTE)
-- y lotes de importación masiva (LOTE_IMPORTACION_EPS, DETALLE_IMPORTACION_EPS).
-- Derechos del definidor; nunca ejecuta COMMIT ni ROLLBACK (la transacción es de Java).
-- =============================================================================
CREATE OR REPLACE PACKAGE PKG_AFFILIATIONS AUTHID DEFINER AS

    -- ------------------------------------------------------------- ENTIDAD_EPS
    -- Entidad EPS por ID interno, PUBLIC_ID o código MinSalud.
    PROCEDURE get_eps_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    PROCEDURE get_eps_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);
    PROCEDURE get_eps_by_code(p_code IN VARCHAR2, o_row OUT SYS_REFCURSOR);

    -- Entidades EPS activas ordenadas alfabéticamente por nombre.
    PROCEDURE list_active_eps(o_rows OUT SYS_REFCURSOR);

    -- ----------------------------------------------------- AFILIACION_PACIENTE
    -- Registra una afiliación EPS de paciente y devuelve su ID interno.
    PROCEDURE create_affiliation(p_public_id IN VARCHAR2, p_patient_id IN NUMBER, p_document_type IN VARCHAR2,
                                 p_document_number IN VARCHAR2, p_eps_id IN NUMBER, p_regime IN VARCHAR2,
                                 p_affiliate_type IN VARCHAR2, p_status IN VARCHAR2, p_affiliation_date IN DATE,
                                 p_verification_source IN VARCHAR2,
                                 p_last_verification_at IN TIMESTAMP WITH TIME ZONE, o_id OUT NUMBER);

    -- Afiliación por ID interno o por tipo y número de documento.
    PROCEDURE get_affiliation_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    PROCEDURE get_affiliation_by_document(p_document_type IN VARCHAR2, p_document_number IN VARCHAR2,
                                          o_row OUT SYS_REFCURSOR);

    -- Actualiza la cobertura EPS vigente de una afiliación.
    PROCEDURE update_affiliation(p_id IN NUMBER, p_eps_id IN NUMBER, p_regime IN VARCHAR2,
                                 p_affiliate_type IN VARCHAR2, p_status IN VARCHAR2,
                                 p_last_verification_at IN TIMESTAMP WITH TIME ZONE);

    -- Vincula el PACIENTE_ID a una afiliación precargada por documento cuando aún no tiene paciente asignado.
    PROCEDURE link_patient_affiliation(p_document_type IN VARCHAR2, p_document_number IN VARCHAR2,
                                       p_patient_id IN NUMBER);

    -- Vista detallada de afiliación con EPS y datos del paciente (si ya existe) por documento o por paciente.
    PROCEDURE get_affiliation_detail_by_document(p_document_type IN VARCHAR2, p_document_number IN VARCHAR2,
                                                 o_row OUT SYS_REFCURSOR);
    PROCEDURE get_affiliation_detail_by_patient_id(p_patient_id IN NUMBER, o_row OUT SYS_REFCURSOR);

    -- ---------------------------------------------------- LOTE_IMPORTACION_EPS
    -- Crea un lote de importación EPS (estado por defecto PREVIEW) y devuelve su ID interno.
    PROCEDURE create_import_batch(p_public_id IN VARCHAR2, p_file_name IN VARCHAR2, p_sha256_hash IN VARCHAR2,
                                  p_eps_id IN NUMBER, p_total_rows IN NUMBER, p_valid_rows IN NUMBER,
                                  p_failed_rows IN NUMBER, p_status IN VARCHAR2,
                                  p_uploaded_by_user_id IN NUMBER, o_id OUT NUMBER);

    -- Lote de importación por ID interno o PUBLIC_ID.
    PROCEDURE get_import_batch_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    PROCEDURE get_import_batch_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);

    -- Registra una fila de staging en DETALLE_IMPORTACION_EPS.
    PROCEDURE create_import_batch_item(p_batch_id IN NUMBER, p_row_number IN NUMBER, p_document_type IN VARCHAR2,
                                       p_document_number IN VARCHAR2, p_first_names IN VARCHAR2,
                                       p_last_names IN VARCHAR2, p_regime IN VARCHAR2,
                                       p_affiliate_type IN VARCHAR2, p_row_status IN VARCHAR2,
                                       p_error_reason IN VARCHAR2);

    -- Actualiza los contadores de filas de un lote.
    PROCEDURE update_import_batch_totals(p_batch_id IN NUMBER, p_total IN NUMBER, p_valid IN NUMBER,
                                         p_failed IN NUMBER);

    -- Actualiza el estado y fecha de procesamiento de un lote.
    PROCEDURE update_import_batch_status(p_batch_id IN NUMBER, p_status IN VARCHAR2,
                                         p_processed_at IN TIMESTAMP WITH TIME ZONE);

    -- Primeras p_limit filas de staging de un lote ordenadas por número de fila.
    PROCEDURE list_import_batch_items(p_batch_id IN NUMBER, p_limit IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Filas válidas (ESTADO_FILA = 'VALIDO') de un lote ordenadas por número de fila.
    PROCEDURE list_valid_import_batch_items(p_batch_id IN NUMBER, o_rows OUT SYS_REFCURSOR);
END PKG_AFFILIATIONS;
/
