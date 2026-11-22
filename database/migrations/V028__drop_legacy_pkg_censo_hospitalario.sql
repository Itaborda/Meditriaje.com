-- =============================================================================
-- MediTriaje - V028: eliminacion del paquete heredado PKG_CENSO_HOSPITALARIO
-- =============================================================================
-- Paquete heredado previo a la refactorizacion de hospital unico (V026) y
-- reemplazado por PKG_HOSPITAL. Puede permanecer invalido en entornos existentes
-- de Oracle ATP y bloquear la validacion de R__zz_recompile_and_grants.sql.
-- Idempotente y compatible con Oracle 19c (ATP) y 23ai.
-- =============================================================================
DECLARE
    v_spec_exists NUMBER;
    v_body_exists NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_spec_exists
      FROM USER_OBJECTS
     WHERE OBJECT_NAME = 'PKG_CENSO_HOSPITALARIO'
       AND OBJECT_TYPE = 'PACKAGE';

    IF v_spec_exists > 0 THEN
        EXECUTE IMMEDIATE 'DROP PACKAGE PKG_CENSO_HOSPITALARIO';
    ELSE
        SELECT COUNT(*)
          INTO v_body_exists
          FROM USER_OBJECTS
         WHERE OBJECT_NAME = 'PKG_CENSO_HOSPITALARIO'
           AND OBJECT_TYPE = 'PACKAGE BODY';

        IF v_body_exists > 0 THEN
            EXECUTE IMMEDIATE 'DROP PACKAGE BODY PKG_CENSO_HOSPITALARIO';
        END IF;
    END IF;
END;
/
