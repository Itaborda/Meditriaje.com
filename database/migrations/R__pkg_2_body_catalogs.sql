-- =============================================================================
-- MediTriaje - R__pkg_2_body_catalogs: cuerpo de PKG_CATALOGS
-- Repetible. Sin COMMIT ni ROLLBACK (la transacción pertenece a la capa de aplicación).
-- =============================================================================
CREATE OR REPLACE PACKAGE BODY PKG_CATALOGS AS

    PROCEDURE get_diagnosis_by_code(p_code IN VARCHAR2, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, CODIGO, DESCRIPCION, ESTADO
              FROM DIAGNOSTICO_CIE10
             WHERE CODIGO = p_code;
    END get_diagnosis_by_code;

    PROCEDURE get_diagnosis_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_row FOR
            SELECT ID, CODIGO, DESCRIPCION, ESTADO
              FROM DIAGNOSTICO_CIE10
             WHERE ID = p_id;
    END get_diagnosis_by_id;

    PROCEDURE list_active_diagnoses(p_pattern IN VARCHAR2, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT CODIGO, DESCRIPCION
              FROM DIAGNOSTICO_CIE10
             WHERE ESTADO = 'ACTIVO'
               AND (p_pattern IS NULL
                    OR UPPER(CODIGO) LIKE p_pattern
                    OR UPPER(DESCRIPCION) LIKE p_pattern)
             ORDER BY CODIGO ASC;
    END list_active_diagnoses;
END PKG_CATALOGS;
/
