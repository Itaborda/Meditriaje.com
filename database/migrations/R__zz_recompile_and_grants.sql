-- =============================================================================
-- MediTriaje - R__zz_recompile_and_grants: recompilación del esquema y permisos EXECUTE
-- Repetible y siempre al final (el nombre ordena después de R__pkg_*). La línea de marca de tiempo
-- cambia en cada ejecución de Flyway y fuerza que el script se vuelva a aplicar con cada migrate:
-- ${flyway:timestamp}
--
-- 1. Recompila los objetos inválidos del esquema (dependencias entre paquetes, triggers y vistas).
-- 2. Concede EXECUTE sobre TODOS los paquetes del esquema al usuario de aplicación MEDITRIAJE_APP
--    (si existe). Un paquete nuevo no necesita tocar este archivo.
-- 3. Falla (-20998) si queda algún paquete inválido: un error de compilación detiene el despliegue
--    en lugar de aparecer como error 500 en tiempo de ejecución.
-- =============================================================================
DECLARE
    v_app_user CONSTANT VARCHAR2(30) := 'MEDITRIAJE_APP';
    v_count    NUMBER;
    v_invalid  VARCHAR2(2000);
BEGIN
    DBMS_UTILITY.COMPILE_SCHEMA(schema => SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA'), compile_all => FALSE);

    SELECT COUNT(*) INTO v_count FROM ALL_USERS WHERE USERNAME = v_app_user;
    IF v_count > 0 AND v_app_user <> SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') THEN
        FOR p IN (SELECT OBJECT_NAME FROM USER_OBJECTS WHERE OBJECT_TYPE = 'PACKAGE' ORDER BY OBJECT_NAME) LOOP
            EXECUTE IMMEDIATE 'GRANT EXECUTE ON "' || p.OBJECT_NAME || '" TO ' || v_app_user;
        END LOOP;
    END IF;

    SELECT LISTAGG(OBJECT_NAME || ' ' || OBJECT_TYPE, ', ') WITHIN GROUP (ORDER BY OBJECT_NAME, OBJECT_TYPE)
      INTO v_invalid
      FROM USER_OBJECTS
     WHERE STATUS = 'INVALID' AND OBJECT_TYPE IN ('PACKAGE', 'PACKAGE BODY');
    IF v_invalid IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20998, 'Paquetes invalidos tras compilar: ' || SUBSTR(v_invalid, 1, 1900));
    END IF;
END;
/
