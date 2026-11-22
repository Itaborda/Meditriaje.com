-- =============================================================================
-- MediTriaje - V026: eliminación definitiva de SEDE_ID, SEDE e INSTITUCION
-- Hospital único (corrección del profesor + cierre de transición V023-V025).
--
-- Precondición: V024 debe haber poblado CONFIGURACION_HOSPITAL con exactamente una fila y ningún
-- paquete PL/SQL ni repositorio Java debe referenciar SEDE, INSTITUCION o SEDE_ID.
-- Todas las sentencias DDL se protegen con consultas al diccionario USER_* para ser idempotentes
-- y compatibles con Oracle 19c (ATP).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Precondición de seguridad: CONFIGURACION_HOSPITAL debe existir con exactamente 1 fila
-- -----------------------------------------------------------------------------
DECLARE
    v_table_exists NUMBER;
    v_count        NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_table_exists FROM USER_TABLES WHERE TABLE_NAME = 'CONFIGURACION_HOSPITAL';
    IF v_table_exists = 0 THEN
        RAISE_APPLICATION_ERROR(-20992, 'V026 abortada: no existe la tabla CONFIGURACION_HOSPITAL.');
    END IF;

    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM CONFIGURACION_HOSPITAL' INTO v_count;
    IF v_count <> 1 THEN
        RAISE_APPLICATION_ERROR(-20992, 'V026 abortada: CONFIGURACION_HOSPITAL debe tener exactamente 1 fila (tiene ' || v_count || ').');
    END IF;
END;
/

-- -----------------------------------------------------------------------------
-- 2. Eliminar restricciones FK hacia SEDE / INSTITUCION, índices heredados, columnas SEDE_ID,
--    y crear índices y unicidad de hospital único
-- -----------------------------------------------------------------------------
DECLARE
    TYPE t_names IS TABLE OF VARCHAR2(40);
    c_site_tables CONSTANT t_names := t_names(
        'DISPONIBILIDAD_SLOT', 'DISPENSACION', 'EPISODIO_ATENCION', 'AREA_HOSPITALARIA', 'ALERTA_OPERATIVA');
    c_legacy_indexes CONSTANT t_names := t_names(
        'IX_SLOT_SEDE', 'IX_DISPENSACION_SEDE', 'IX_EPISODIO_SEDE_ESTADO', 'IX_AREA_SEDE', 'IDX_ALERTA_SEDE_ESTADO');
    v_count NUMBER;
BEGIN
    -- 2a. Toda clave foránea que apunte a SEDE o INSTITUCION (incluye la FK autogenerada de ALERTA_OPERATIVA)
    FOR r IN (
        SELECT c.TABLE_NAME, c.CONSTRAINT_NAME
          FROM USER_CONSTRAINTS c
          JOIN USER_CONSTRAINTS p ON c.R_CONSTRAINT_NAME = p.CONSTRAINT_NAME
         WHERE c.CONSTRAINT_TYPE = 'R'
           AND p.TABLE_NAME IN ('SEDE', 'INSTITUCION')
         ORDER BY c.TABLE_NAME, c.CONSTRAINT_NAME
    ) LOOP
        EXECUTE IMMEDIATE 'ALTER TABLE "' || r.TABLE_NAME || '" DROP CONSTRAINT "' || r.CONSTRAINT_NAME || '"';
    END LOOP;

    -- 2b. Unicidad compuesta anterior en AREA_HOSPITALARIA (SEDE_ID, CODIGO) e índice asociado si sobrevive
    SELECT COUNT(*) INTO v_count FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'UQ_AREA_SEDE_CODIGO';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE AREA_HOSPITALARIA DROP CONSTRAINT UQ_AREA_SEDE_CODIGO';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'UQ_AREA_SEDE_CODIGO';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP INDEX UQ_AREA_SEDE_CODIGO';
    END IF;

    -- 2c. Índice compuesto antiguo de DISPONIBILIDAD_SLOT si aún incluye SEDE_ID
    SELECT COUNT(*) INTO v_count
      FROM USER_IND_COLUMNS
     WHERE INDEX_NAME = 'IX_SLOT_BUSQUEDA'
       AND COLUMN_NAME = 'SEDE_ID';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP INDEX IX_SLOT_BUSQUEDA';
    END IF;

    -- 2d. Índices por SEDE_ID ya innecesarios
    FOR i IN 1 .. c_legacy_indexes.COUNT LOOP
        SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = c_legacy_indexes(i);
        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'DROP INDEX "' || c_legacy_indexes(i) || '"';
        END IF;
    END LOOP;

    -- 2e. Eliminar columna SEDE_ID de las 5 tablas heredadas
    FOR i IN 1 .. c_site_tables.COUNT LOOP
        SELECT COUNT(*) INTO v_count
          FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = c_site_tables(i)
           AND COLUMN_NAME = 'SEDE_ID';
        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE "' || c_site_tables(i) || '" DROP COLUMN SEDE_ID';
        END IF;
    END LOOP;

    -- 2f. Reemplazos de hospital único: unicidad global de código de área e índices de consulta
    SELECT COUNT(*) INTO v_count FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'UQ_AREA_CODIGO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE AREA_HOSPITALARIA ADD CONSTRAINT UQ_AREA_CODIGO UNIQUE (CODIGO)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'IX_SLOT_BUSQUEDA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_SLOT_BUSQUEDA ON DISPONIBILIDAD_SLOT (ESPECIALIDAD_ID, ESTADO, FECHA_HORA_INICIO)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'IX_EPISODIO_ESTADO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_EPISODIO_ESTADO ON EPISODIO_ATENCION (ESTADO)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_ALERTA_ESTADO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_ALERTA_ESTADO ON ALERTA_OPERATIVA (ESTADO)';
    END IF;
END;
/

-- -----------------------------------------------------------------------------
-- 3. Eliminar triggers de sede/institución única y tablas SEDE e INSTITUCION
-- -----------------------------------------------------------------------------
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TRIGGERS WHERE TRIGGER_NAME = 'TR_SEDE_UNICA';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP TRIGGER TR_SEDE_UNICA';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_TRIGGERS WHERE TRIGGER_NAME = 'TR_INSTITUCION_UNICA';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP TRIGGER TR_INSTITUCION_UNICA';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'SEDE';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLE SEDE CASCADE CONSTRAINTS PURGE';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'INSTITUCION';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLE INSTITUCION CASCADE CONSTRAINTS PURGE';
    END IF;
END;
/

-- -----------------------------------------------------------------------------
-- 4. Privilegio mínimo de lectura sobre CONFIGURACION_HOSPITAL para MEDITRIAJE_APP
-- -----------------------------------------------------------------------------
DECLARE
    v_app_user CONSTANT VARCHAR2(30) := 'MEDITRIAJE_APP';
    v_count    NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_USERS WHERE USERNAME = v_app_user;
    IF v_count > 0 AND v_app_user <> SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') THEN
        EXECUTE IMMEDIATE 'GRANT SELECT ON CONFIGURACION_HOSPITAL TO ' || v_app_user;
    END IF;
END;
/
