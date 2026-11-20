-- =============================================================================
-- MediTriaje - R__pkg_2_body_common: cuerpo de PKG_COMMON
-- Repetible. Sin COMMIT ni ROLLBACK (la transacción pertenece a la capa de aplicación).
-- =============================================================================
CREATE OR REPLACE PACKAGE BODY PKG_COMMON AS

    PROCEDURE raise_error(p_code IN NUMBER, p_message IN VARCHAR2) IS
    BEGIN
        IF p_code IS NULL OR p_code NOT BETWEEN -20999 AND -20000 THEN
            RAISE_APPLICATION_ERROR(c_err_internal, 'INVALID_ERROR_CODE:' || NVL(TO_CHAR(p_code), 'NULL'));
        END IF;
        RAISE_APPLICATION_ERROR(p_code, SUBSTR(NVL(p_message, 'Error de aplicacion.'), 1, 2000));
    END raise_error;

    FUNCTION get_single_site_id RETURN NUMBER IS
        v_site_id SEDE.ID%TYPE;
    BEGIN
        SELECT MIN(ID) INTO v_site_id FROM SEDE;
        IF v_site_id IS NULL THEN
            raise_error(c_err_not_found, 'No hay una sede hospitalaria configurada.');
        END IF;
        RETURN v_site_id;
    END get_single_site_id;

    PROCEDURE get_single_site(o_site OUT SYS_REFCURSOR) IS
        v_site_id SEDE.ID%TYPE := get_single_site_id;
    BEGIN
        OPEN o_site FOR
            SELECT ID, INSTITUCION_ID, PUBLIC_ID, NOMBRE, DIRECCION, CIUDAD, ESTADO
              FROM SEDE
             WHERE ID = v_site_id;
    END get_single_site;

    FUNCTION new_public_id RETURN VARCHAR2 IS
    BEGIN
        RETURN LOWER(REGEXP_REPLACE(RAWTOHEX(SYS_GUID()), '(.{8})(.{4})(.{4})(.{4})(.{12})', '\1-\2-\3-\4-\5'));
    END new_public_id;
END PKG_COMMON;
/
