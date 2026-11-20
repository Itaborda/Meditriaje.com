-- =============================================================================
-- MediTriaje - V025: compañera obligatoria de V023/V024 (hospital único)
-- Slice 1 (db-single-hospital), unidad de trabajo WU1b. Diseño: 02-oracle-design 2.3.
--
-- V023/V024 NO deben desplegarse sin esta migración; las tres se aplican en el mismo despliegue.
--   1. Restaura el trigger estricto TR_DISPENSACION_INMUTABILIDAD (V023 lo relajó solo para que V024
--      reasignara SEDE_ID). Comportamiento idéntico al de V013: DELETE -20030, cualquier UPDATE -20031.
--   2. Crea UQ_ALERTA_OPERATIVA_ACTIVA (reemplaza al índice con clave de sede eliminado en V023).
--   3. Crea los triggers de sentencia TR_SEDE_UNICA y TR_INSTITUCION_UNICA (-20060): una segunda
--      sede o institución ya no puede aparecer.
-- Todas las sentencias DDL son idempotentes (consultas al diccionario USER_*).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. TR_DISPENSACION_INMUTABILIDAD estricto (equivalente a V013)
-- Ninguna sesión, tampoco la del propietario del esquema, puede modificar o borrar una dispensación.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TR_DISPENSACION_INMUTABILIDAD
BEFORE UPDATE OR DELETE ON DISPENSACION
FOR EACH ROW
BEGIN
    IF DELETING THEN
        RAISE_APPLICATION_ERROR(-20030, 'Prohibido eliminar registros de dispensacion farmaceutica.');
    END IF;
    IF UPDATING THEN
        RAISE_APPLICATION_ERROR(-20031, 'Los registros de dispensacion son inmutables tras su emision.');
    END IF;
END;
/

-- -----------------------------------------------------------------------------
-- 2. UQ_ALERTA_OPERATIVA_ACTIVA
-- Una alerta ACTIVA por tipo para las alertas de hospital (EPISODIO_ID nulo) y una por (tipo, episodio)
-- para las alertas ligadas a un episodio, p. ej. TRIAJE_VENCIDO. Las alertas RECONOCIDA o RESUELTA
-- no participan: ambas expresiones valen NULL y Oracle no indexa claves totalmente nulas.
-- -----------------------------------------------------------------------------
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'UQ_ALERTA_OPERATIVA_ACTIVA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UQ_ALERTA_OPERATIVA_ACTIVA ON ALERTA_OPERATIVA ('
            || 'CASE WHEN ESTADO = ''ACTIVA'' THEN TIPO_ALERTA END, '
            || 'CASE WHEN ESTADO = ''ACTIVA'' THEN NVL(EPISODIO_ID, 0) END)';
    END IF;
END;
/

-- -----------------------------------------------------------------------------
-- 3. TR_SEDE_UNICA y TR_INSTITUCION_UNICA
-- Triggers de sentencia AFTER INSERT: cuentan las filas resultantes, de modo que tanto un INSERT
-- simple como un INSERT ... SELECT de varias filas que deje más de una se rechazan con -20060.
-- Se eliminan junto con SEDE e INSTITUCION en la limpieza final (WU-C).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TR_SEDE_UNICA
AFTER INSERT ON SEDE
DECLARE
    v_filas NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_filas FROM SEDE;
    IF v_filas > 1 THEN
        RAISE_APPLICATION_ERROR(-20060, 'El sistema atiende un unico hospital: no se permite mas de una sede.');
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TR_INSTITUCION_UNICA
AFTER INSERT ON INSTITUCION
DECLARE
    v_filas NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_filas FROM INSTITUCION;
    IF v_filas > 1 THEN
        RAISE_APPLICATION_ERROR(-20060, 'El sistema atiende un unico hospital: no se permite mas de una institucion.');
    END IF;
END;
/
