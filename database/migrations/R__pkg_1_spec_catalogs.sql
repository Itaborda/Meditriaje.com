-- =============================================================================
-- MediTriaje - R__pkg_1_spec_catalogs: especificación de PKG_CATALOGS
-- Repetible. Catálogo de diagnósticos CIE-10 (DIAGNOSTICO_CIE10), de solo lectura para la aplicación.
-- Derechos del definidor; nunca ejecuta COMMIT ni ROLLBACK (la transacción es de Java).
-- =============================================================================
CREATE OR REPLACE PACKAGE PKG_CATALOGS AUTHID DEFINER AS
    -- Diagnóstico por código exacto (ya normalizado en mayúsculas). Cursor de 0 o 1 fila:
    -- ID, CODIGO, DESCRIPCION, ESTADO.
    PROCEDURE get_diagnosis_by_code(p_code IN VARCHAR2, o_row OUT SYS_REFCURSOR);

    -- Diagnóstico por ID interno. Cursor de 0 o 1 fila: ID, CODIGO, DESCRIPCION, ESTADO.
    PROCEDURE get_diagnosis_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);

    -- Diagnósticos activos ordenados por código. Si p_pattern no es nulo filtra por
    -- UPPER(CODIGO) LIKE p_pattern o UPPER(DESCRIPCION) LIKE p_pattern. Columnas: CODIGO, DESCRIPCION.
    PROCEDURE list_active_diagnoses(p_pattern IN VARCHAR2, o_rows OUT SYS_REFCURSOR);
END PKG_CATALOGS;
/
