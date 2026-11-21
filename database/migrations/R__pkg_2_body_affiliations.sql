-- =============================================================================
-- MediTriaje - R__pkg_2_body_affiliations: cuerpo de PKG_AFFILIATIONS
-- Repetible. Sin COMMIT ni ROLLBACK (la transacción pertenece a la capa de aplicación).
-- =============================================================================
CREATE OR REPLACE PACKAGE BODY PKG_AFFILIATIONS AS

    -- ------------------------------------------------------------- ENTIDAD_EPS
    PROCEDURE get_eps_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, CODIGO_MINSALUD, NOMBRE, NIT, REGIMEN_HABITUAL, ESTADO, CREADO_AT
              FROM ENTIDAD_EPS
             WHERE ID = p_id;
    END get_eps_by_id;

    PROCEDURE get_eps_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, CODIGO_MINSALUD, NOMBRE, NIT, REGIMEN_HABITUAL, ESTADO, CREADO_AT
              FROM ENTIDAD_EPS
             WHERE PUBLIC_ID = p_public_id;
    END get_eps_by_public_id;

    PROCEDURE get_eps_by_code(p_code IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, CODIGO_MINSALUD, NOMBRE, NIT, REGIMEN_HABITUAL, ESTADO, CREADO_AT
              FROM ENTIDAD_EPS
             WHERE CODIGO_MINSALUD = p_code;
    END get_eps_by_code;

    PROCEDURE list_active_eps(o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ID, PUBLIC_ID, CODIGO_MINSALUD, NOMBRE, NIT, REGIMEN_HABITUAL, ESTADO, CREADO_AT
              FROM ENTIDAD_EPS
             WHERE ESTADO = 'ACTIVA'
             ORDER BY NOMBRE ASC;
    END list_active_eps;

    -- ----------------------------------------------------- AFILIACION_PACIENTE
    PROCEDURE create_affiliation(p_public_id IN VARCHAR2, p_patient_id IN NUMBER, p_document_type IN VARCHAR2,
                                 p_document_number IN VARCHAR2, p_eps_id IN NUMBER, p_regime IN VARCHAR2,
                                 p_affiliate_type IN VARCHAR2, p_status IN VARCHAR2, p_affiliation_date IN DATE,
                                 p_verification_source IN VARCHAR2,
                                 p_last_verification_at IN TIMESTAMP WITH TIME ZONE, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO AFILIACION_PACIENTE (
            PUBLIC_ID, PACIENTE_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
            EPS_ID, REGIMEN, TIPO_AFILIADO, ESTADO, FECHA_AFILIACION,
            FUENTE_VERIFICACION, ULTIMA_VERIFICACION_AT
        )
        VALUES (
            p_public_id,
            p_patient_id,
            p_document_type,
            p_document_number,
            p_eps_id,
            p_regime,
            NVL(p_affiliate_type, 'COTIZANTE'),
            NVL(p_status, 'ACTIVO'),
            p_affiliation_date,
            NVL(p_verification_source, 'CARGA_MASIVA'),
            NVL(p_last_verification_at, CURRENT_TIMESTAMP)
        )
        RETURNING ID INTO o_id;
    END create_affiliation;

    PROCEDURE get_affiliation_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, PACIENTE_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                   EPS_ID, REGIMEN, TIPO_AFILIADO, ESTADO, FECHA_AFILIACION,
                   FUENTE_VERIFICACION, ULTIMA_VERIFICACION_AT, CREADO_AT, UPDATED_AT
              FROM AFILIACION_PACIENTE
             WHERE ID = p_id;
    END get_affiliation_by_id;

    PROCEDURE get_affiliation_by_document(p_document_type IN VARCHAR2, p_document_number IN VARCHAR2,
                                          o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, PACIENTE_ID, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                   EPS_ID, REGIMEN, TIPO_AFILIADO, ESTADO, FECHA_AFILIACION,
                   FUENTE_VERIFICACION, ULTIMA_VERIFICACION_AT, CREADO_AT, UPDATED_AT
              FROM AFILIACION_PACIENTE
             WHERE TIPO_DOCUMENTO = p_document_type AND NUMERO_DOCUMENTO = p_document_number;
    END get_affiliation_by_document;

    PROCEDURE update_affiliation(p_id IN NUMBER, p_eps_id IN NUMBER, p_regime IN VARCHAR2,
                                 p_affiliate_type IN VARCHAR2, p_status IN VARCHAR2,
                                 p_last_verification_at IN TIMESTAMP WITH TIME ZONE) IS
    BEGIN
        UPDATE AFILIACION_PACIENTE
           SET EPS_ID = p_eps_id,
               REGIMEN = p_regime,
               TIPO_AFILIADO = p_affiliate_type,
               ESTADO = p_status,
               ULTIMA_VERIFICACION_AT = p_last_verification_at,
               UPDATED_AT = CURRENT_TIMESTAMP
         WHERE ID = p_id;
    END update_affiliation;

    PROCEDURE link_patient_affiliation(p_document_type IN VARCHAR2, p_document_number IN VARCHAR2,
                                       p_patient_id IN NUMBER) IS
    BEGIN
        UPDATE AFILIACION_PACIENTE
           SET PACIENTE_ID = p_patient_id,
               UPDATED_AT = CURRENT_TIMESTAMP
         WHERE TIPO_DOCUMENTO = p_document_type
           AND NUMERO_DOCUMENTO = p_document_number
           AND PACIENTE_ID IS NULL;
    END link_patient_affiliation;

    PROCEDURE get_affiliation_detail_by_document(p_document_type IN VARCHAR2, p_document_number IN VARCHAR2,
                                                 o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT a.PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUB_ID,
                   a.TIPO_DOCUMENTO,
                   a.NUMERO_DOCUMENTO,
                   CASE
                       WHEN p.ID IS NOT NULL THEN (p.NOMBRES || ' ' || p.APELLIDOS)
                       ELSE NULL
                   END AS PACIENTE_NOMBRE,
                   eps.CODIGO_MINSALUD AS EPS_CODIGO,
                   eps.NOMBRE AS EPS_NOMBRE,
                   a.REGIMEN,
                   a.TIPO_AFILIADO,
                   a.ESTADO,
                   a.FECHA_AFILIACION,
                   a.FUENTE_VERIFICACION,
                   a.ULTIMA_VERIFICACION_AT
              FROM AFILIACION_PACIENTE a
              JOIN ENTIDAD_EPS eps ON a.EPS_ID = eps.ID
              LEFT JOIN PACIENTE p ON a.PACIENTE_ID = p.ID
             WHERE a.TIPO_DOCUMENTO = p_document_type AND a.NUMERO_DOCUMENTO = p_document_number;
    END get_affiliation_detail_by_document;

    PROCEDURE get_affiliation_detail_by_patient_id(p_patient_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT a.PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUB_ID,
                   a.TIPO_DOCUMENTO,
                   a.NUMERO_DOCUMENTO,
                   (p.NOMBRES || ' ' || p.APELLIDOS) AS PACIENTE_NOMBRE,
                   eps.CODIGO_MINSALUD AS EPS_CODIGO,
                   eps.NOMBRE AS EPS_NOMBRE,
                   a.REGIMEN,
                   a.TIPO_AFILIADO,
                   a.ESTADO,
                   a.FECHA_AFILIACION,
                   a.FUENTE_VERIFICACION,
                   a.ULTIMA_VERIFICACION_AT
              FROM AFILIACION_PACIENTE a
              JOIN ENTIDAD_EPS eps ON a.EPS_ID = eps.ID
              JOIN PACIENTE p ON a.PACIENTE_ID = p.ID
             WHERE a.PACIENTE_ID = p_patient_id;
    END get_affiliation_detail_by_patient_id;

    -- ---------------------------------------------------- LOTE_IMPORTACION_EPS
    PROCEDURE create_import_batch(p_public_id IN VARCHAR2, p_file_name IN VARCHAR2, p_sha256_hash IN VARCHAR2,
                                  p_eps_id IN NUMBER, p_total_rows IN NUMBER, p_valid_rows IN NUMBER,
                                  p_failed_rows IN NUMBER, p_status IN VARCHAR2,
                                  p_uploaded_by_user_id IN NUMBER, o_id OUT NUMBER) IS
    BEGIN
        INSERT INTO LOTE_IMPORTACION_EPS (
            PUBLIC_ID, NOMBRE_ARCHIVO, HASH_SHA256, EPS_ID,
            TOTAL_FILAS, FILAS_VALIDAS, FILAS_FALLIDAS, ESTADO,
            SUBIDO_POR_USUARIO_ID
        )
        VALUES (
            p_public_id, p_file_name, p_sha256_hash, p_eps_id,
            p_total_rows, p_valid_rows, p_failed_rows, NVL(p_status, 'PREVIEW'),
            p_uploaded_by_user_id
        )
        RETURNING ID INTO o_id;
    END create_import_batch;

    PROCEDURE get_import_batch_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, NOMBRE_ARCHIVO, HASH_SHA256, EPS_ID,
                   TOTAL_FILAS, FILAS_VALIDAS, FILAS_FALLIDAS, ESTADO,
                   SUBIDO_POR_USUARIO_ID, CREADO_AT, PROCESADO_AT
              FROM LOTE_IMPORTACION_EPS
             WHERE ID = p_id;
    END get_import_batch_by_id;

    PROCEDURE get_import_batch_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, PUBLIC_ID, NOMBRE_ARCHIVO, HASH_SHA256, EPS_ID,
                   TOTAL_FILAS, FILAS_VALIDAS, FILAS_FALLIDAS, ESTADO,
                   SUBIDO_POR_USUARIO_ID, CREADO_AT, PROCESADO_AT
              FROM LOTE_IMPORTACION_EPS
             WHERE PUBLIC_ID = p_public_id;
    END get_import_batch_by_public_id;

    PROCEDURE create_import_batch_item(p_batch_id IN NUMBER, p_row_number IN NUMBER, p_document_type IN VARCHAR2,
                                       p_document_number IN VARCHAR2, p_first_names IN VARCHAR2,
                                       p_last_names IN VARCHAR2, p_regime IN VARCHAR2,
                                       p_affiliate_type IN VARCHAR2, p_row_status IN VARCHAR2,
                                       p_error_reason IN VARCHAR2) IS
    BEGIN
        INSERT INTO DETALLE_IMPORTACION_EPS (
            LOTE_ID, NUMERO_FILA, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
            NOMBRES, APELLIDOS, REGIMEN, TIPO_AFILIADO, ESTADO_FILA, ERROR_MOTIVO
        )
        VALUES (
            p_batch_id, p_row_number, p_document_type, p_document_number,
            p_first_names, p_last_names, p_regime, p_affiliate_type, p_row_status, p_error_reason
        );
    END create_import_batch_item;

    PROCEDURE update_import_batch_totals(p_batch_id IN NUMBER, p_total IN NUMBER, p_valid IN NUMBER,
                                         p_failed IN NUMBER) IS
    BEGIN
        UPDATE LOTE_IMPORTACION_EPS
           SET TOTAL_FILAS = p_total,
               FILAS_VALIDAS = p_valid,
               FILAS_FALLIDAS = p_failed
         WHERE ID = p_batch_id;
    END update_import_batch_totals;

    PROCEDURE update_import_batch_status(p_batch_id IN NUMBER, p_status IN VARCHAR2,
                                         p_processed_at IN TIMESTAMP WITH TIME ZONE) IS
    BEGIN
        UPDATE LOTE_IMPORTACION_EPS
           SET ESTADO = p_status,
               PROCESADO_AT = p_processed_at
         WHERE ID = p_batch_id;
    END update_import_batch_status;

    PROCEDURE list_import_batch_items(p_batch_id IN NUMBER, p_limit IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ID, LOTE_ID, NUMERO_FILA, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                   NOMBRES, APELLIDOS, REGIMEN, TIPO_AFILIADO, ESTADO_FILA, ERROR_MOTIVO
              FROM DETALLE_IMPORTACION_EPS
             WHERE LOTE_ID = p_batch_id
             ORDER BY NUMERO_FILA ASC
             FETCH FIRST p_limit ROWS ONLY;
    END list_import_batch_items;

    PROCEDURE list_valid_import_batch_items(p_batch_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ID, LOTE_ID, NUMERO_FILA, TIPO_DOCUMENTO, NUMERO_DOCUMENTO,
                   NOMBRES, APELLIDOS, REGIMEN, TIPO_AFILIADO, ESTADO_FILA, ERROR_MOTIVO
              FROM DETALLE_IMPORTACION_EPS
             WHERE LOTE_ID = p_batch_id
               AND ESTADO_FILA = 'VALIDO'
             ORDER BY NUMERO_FILA ASC;
    END list_valid_import_batch_items;
END PKG_AFFILIATIONS;
/
