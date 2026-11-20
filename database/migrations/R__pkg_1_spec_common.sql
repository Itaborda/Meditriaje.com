-- =============================================================================
-- MediTriaje - R__pkg_1_spec_common: especificación de PKG_COMMON
-- Repetible. Todas las especificaciones (R__pkg_1_spec_*) se compilan antes que cualquier cuerpo
-- (R__pkg_2_body_*), de modo que las llamadas entre paquetes no dependen del orden de compilación.
--
-- PKG_COMMON reúne utilidades compartidas por los paquetes de módulo: errores de aplicación,
-- resolución del hospital único y generación de identificadores públicos.
-- Derechos del definidor; nunca ejecuta COMMIT ni ROLLBACK (la transacción es de Java).
-- =============================================================================
CREATE OR REPLACE PACKAGE PKG_COMMON AUTHID DEFINER AS
    -- Rangos de errores de aplicación. La capa Java (OracleErrorTranslator) los convierte en respuestas HTTP:
    c_err_integrity     CONSTANT PLS_INTEGER := -20000; -- -20000..-20099 integridad/inmutabilidad (409)
    c_err_not_found     CONSTANT PLS_INTEGER := -20100; -- -20100..-20199 no encontrado (404)
    c_err_conflict      CONSTANT PLS_INTEGER := -20200; -- -20200..-20299 conflicto de estado (409)
    c_err_business_rule CONSTANT PLS_INTEGER := -20300; -- -20300..-20399 regla de negocio/validación (400)
    c_err_access_denied CONSTANT PLS_INTEGER := -20400; -- -20400..-20499 acceso denegado (403)
    c_err_catalog       CONSTANT PLS_INTEGER := -20500; -- -20500..-20599 catálogo/importación (400)
    c_err_internal      CONSTANT PLS_INTEGER := -20999; -- error interno (500, el mensaje no se expone)

    -- Lanza un error de aplicación. p_code debe estar entre -20999 y -20000; p_message es un texto en
    -- español apto para el usuario final (la API lo devuelve tal cual). Un código fuera de rango
    -- produce -20999 INVALID_ERROR_CODE.
    PROCEDURE raise_error(p_code IN NUMBER, p_message IN VARCHAR2);

    -- ID interno de la única sede del hospital (V024/V025 garantizan que existe exactamente una).
    -- Lanza -20100 si no hay ninguna sede configurada.
    FUNCTION get_single_site_id RETURN NUMBER;

    -- Datos de la única sede: ID, INSTITUCION_ID, PUBLIC_ID, NOMBRE, DIRECCION, CIUDAD, ESTADO.
    -- Lanza -20100 si no hay ninguna sede configurada.
    PROCEDURE get_single_site(o_site OUT SYS_REFCURSOR);

    -- Nuevo identificador público en formato UUID (minúsculas, con guiones) para la columna PUBLIC_ID.
    FUNCTION new_public_id RETURN VARCHAR2;
END PKG_COMMON;
/
