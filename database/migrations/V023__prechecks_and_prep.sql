-- =============================================================================
-- MediTriaje - V023: fusión de hospital único, parte 1 (prechequeos + captura ANTES + preparación DDL)
-- Slice 1 (db-single-hospital), unidad de trabajo WU1a. Diseño: 02-oracle-design 2.3.
--
-- Los nombres de los objetos creados aquí son españoles. Cada sentencia DDL se protege con consultas
-- al diccionario USER_*, por lo que reejecutar esta migración tras "flyway repair" es seguro.
-- Como el DDL de Oracle confirma de forma implícita, V024 (la fusión) es solo DML y esta
-- migración hace todo el trabajo estructural ANTES de la fusión:
--   1. CONCILIACION_MIGRACION (tabla de evidencia de la migración).
--   2. Prechequeos: lanzan -20990 y detienen la migración antes de cualquier otro cambio cuando
--      la fusión no puede ejecutarse con seguridad. El reporte de colisiones se persiste
--      (transacción autónoma) incluso si la migración falla, para que el operador corrija
--      los datos y reintente.
--   3. Captura ANTES: COUNT(*) y una suma de control de PUBLIC_ID por tabla, más las filas por
--      SEDE_ID de las cinco tablas con clave de sede.
--   4. DDL: singleton CONFIGURACION_HOSPITAL, columnas de alerta, eliminación de UQ_ALERTA_SEDE_TIPO_ACTIVA
--      y un trigger de inmutabilidad de DISPENSACION relajado de forma acotada (ver sección 4d).
-- Los códigos -20990..-20999 se reservan para las aserciones de migración (diseño 2.6).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. CONCILIACION_MIGRACION
-- FASE: ANTES (V023), DESPUES (V024), POST_RENOMBRE (V028), MAPA_FUSION (evidencia de V024).
-- Las filas ANTES/DESPUES por tabla llevan DETALLE = 'BASE=<expresión de la suma de control>'.
-- Las filas por sede llevan DETALLE = 'SEDE_ID=<id>'. Las filas del reporte de prechequeo usan NOMBRE_TABLA = 'PRECHECK'.
-- -----------------------------------------------------------------------------
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'CONCILIACION_MIGRACION';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE q'[CREATE TABLE CONCILIACION_MIGRACION (
            ID NUMBER GENERATED ALWAYS AS IDENTITY,
            MIGRACION VARCHAR2(30 CHAR) NOT NULL,
            NOMBRE_TABLA VARCHAR2(128 CHAR) NOT NULL,
            FASE VARCHAR2(20 CHAR) NOT NULL,
            CANTIDAD_FILAS NUMBER,
            SUMA_CONTROL_PUBLIC_ID NUMBER,
            DETALLE VARCHAR2(4000 CHAR),
            CAPTURADO_AT TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
            CONSTRAINT PK_CONCILIACION_MIGRACION PRIMARY KEY (ID),
            CONSTRAINT CK_CONCILIACION_MIGRACION_FASE CHECK (FASE IN ('ANTES', 'DESPUES', 'POST_RENOMBRE', 'MAPA_FUSION'))
        )]';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'IX_CONCILIACION_MIGRACION_BUSQUEDA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_CONCILIACION_MIGRACION_BUSQUEDA ON CONCILIACION_MIGRACION (MIGRACION, FASE, NOMBRE_TABLA)';
    END IF;
END;
/

-- -----------------------------------------------------------------------------
-- 2. PRECHEQUEOS (nada de lo que sigue se ejecuta si alguno falla)
-- -----------------------------------------------------------------------------
DECLARE
    TYPE t_names IS TABLE OF VARCHAR2(30);
    c_site_tables CONSTANT t_names := t_names(
        'DISPONIBILIDAD_SLOT', 'DISPENSACION', 'EPISODIO_ATENCION', 'AREA_HOSPITALARIA', 'ALERTA_OPERATIVA');
    c_max_area_code_length CONSTANT PLS_INTEGER := 30;

    v_failures      VARCHAR2(4000);
    v_count         NUMBER;
    v_institutions  NUMBER;
    v_sites         NUMBER;
    v_canon_inst    NUMBER;
    v_canon_site    NUMBER;
    v_episode_expr  VARCHAR2(40);
    v_cursor        SYS_REFCURSOR;
    v_type          VARCHAR2(100);
    v_episode       NUMBER;
    v_dup           NUMBER;

    -- Las filas del reporte deben sobrevivir a la falla de esta migración: transacción autónoma.
    PROCEDURE purge_report IS
        PRAGMA AUTONOMOUS_TRANSACTION;
    BEGIN
        DELETE FROM CONCILIACION_MIGRACION WHERE MIGRACION = 'V023' AND NOMBRE_TABLA = 'PRECHECK';
        COMMIT;
    END purge_report;

    PROCEDURE report(p_detail IN VARCHAR2) IS
        PRAGMA AUTONOMOUS_TRANSACTION;
    BEGIN
        INSERT INTO CONCILIACION_MIGRACION (MIGRACION, NOMBRE_TABLA, FASE, DETALLE)
        VALUES ('V023', 'PRECHECK', 'ANTES', SUBSTR(p_detail, 1, 4000));
        COMMIT;
    END report;

    PROCEDURE fail(p_detail IN VARCHAR2) IS
    BEGIN
        report(p_detail);
        IF v_failures IS NULL OR LENGTH(v_failures) < 1700 THEN
            v_failures := SUBSTR(v_failures || p_detail || '; ', 1, 1900);
        END IF;
    END fail;
BEGIN
    purge_report;

    -- 2a. Todo SEDE_ID debe apuntar a una SEDE existente.
    FOR i IN 1 .. c_site_tables.COUNT LOOP
        EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM ' || c_site_tables(i)
            || ' WHERE SEDE_ID NOT IN (SELECT ID FROM SEDE)' INTO v_count;
        IF v_count > 0 THEN
            fail('DANGLING_SEDE_ID table=' || c_site_tables(i) || ' rows=' || v_count);
        END IF;
    END LOOP;
    SELECT COUNT(*) INTO v_count FROM SEDE WHERE INSTITUCION_ID NOT IN (SELECT ID FROM INSTITUCION);
    IF v_count > 0 THEN
        fail('DANGLING_INSTITUCION_ID table=SEDE rows=' || v_count);
    END IF;

    -- 2b. Debe poder derivarse una institución y una sede canónicas.
    SELECT COUNT(*) INTO v_institutions FROM INSTITUCION;
    SELECT COUNT(*) INTO v_sites FROM SEDE;
    SELECT MIN(ID) INTO v_canon_inst FROM INSTITUCION WHERE ESTADO = 'ACTIVO';
    IF v_canon_inst IS NULL AND v_institutions > 0 AND v_sites = 0 THEN
        fail('NO_CANONICAL institutions=' || v_institutions || ' active=0 sites=0');
    ELSIF v_canon_inst IS NULL AND v_sites > 0 THEN
        SELECT INSTITUCION_ID INTO v_canon_inst FROM (
            SELECT INSTITUCION_ID FROM SEDE ORDER BY CASE ESTADO WHEN 'ACTIVO' THEN 0 ELSE 1 END, ID)
        WHERE ROWNUM = 1;
    END IF;
    IF v_canon_inst IS NOT NULL THEN
        SELECT MIN(ID) INTO v_canon_site FROM SEDE WHERE INSTITUCION_ID = v_canon_inst AND ESTADO = 'ACTIVO';
    END IF;
    report('CANONICAL institution_id=' || NVL(TO_CHAR(v_canon_inst), 'NONE (derived in V024)')
        || ' site_id=' || NVL(TO_CHAR(v_canon_site), 'NONE (inserted in V024)'));

    -- 2c. Reporte de colisiones: alertas activas duplicadas por tipo (y episodio, cuando la columna ya existe).
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'ALERTA_OPERATIVA' AND COLUMN_NAME = 'EPISODIO_ID';
    v_episode_expr := CASE WHEN v_count > 0 THEN 'NVL(EPISODIO_ID, 0)' ELSE '0' END;
    OPEN v_cursor FOR
        'SELECT TIPO_ALERTA, ' || v_episode_expr || ', COUNT(*) FROM ALERTA_OPERATIVA WHERE ESTADO = ''ACTIVA'' '
        || 'GROUP BY TIPO_ALERTA, ' || v_episode_expr || ' HAVING COUNT(*) > 1 ORDER BY 1, 2';
    LOOP
        FETCH v_cursor INTO v_type, v_episode, v_dup;
        EXIT WHEN v_cursor%NOTFOUND;
        report('DUPLICATE_ACTIVE_ALERT type=' || v_type || ' episode=' || v_episode || ' count=' || v_dup);
    END LOOP;
    CLOSE v_cursor;

    -- 2d. Reporte de colisiones: códigos de área compartidos por varias sedes.
    FOR r IN (SELECT CODIGO, COUNT(*) AS n,
                     LISTAGG(SEDE_ID, ',') WITHIN GROUP (ORDER BY SEDE_ID) AS sites
                FROM AREA_HOSPITALARIA GROUP BY CODIGO HAVING COUNT(*) > 1 ORDER BY CODIGO) LOOP
        report('AREA_CODE_COLLISION code=' || r.CODIGO || ' sites=' || r.sites || ' count=' || r.n);
    END LOOP;

    -- 2e. Recodificar 'CODIGO-<id de sede>' debe caber en la columna y no chocar con un código existente.
    --     El conservador de cada código es la fila de la sede canónica; si no hay, la sede menor (misma regla que V024).
    FOR r IN (SELECT x.CODIGO, x.SEDE_ID, x.CODIGO || '-' || x.SEDE_ID AS new_code
                FROM (SELECT a.CODIGO, a.SEDE_ID,
                             ROW_NUMBER() OVER (PARTITION BY a.CODIGO
                                 ORDER BY CASE WHEN a.SEDE_ID = v_canon_site THEN 0 ELSE 1 END, a.SEDE_ID, a.ID) AS rn
                        FROM AREA_HOSPITALARIA a) x
               WHERE x.rn > 1) LOOP
        IF LENGTH(r.new_code) > c_max_area_code_length THEN
            fail('AREA_CODE_OVERFLOW code=' || r.CODIGO || ' site=' || r.SEDE_ID || ' recoded=' || r.new_code);
        END IF;
        SELECT COUNT(*) INTO v_count FROM AREA_HOSPITALARIA WHERE CODIGO = r.new_code;
        IF v_count > 0 THEN
            fail('AREA_CODE_CLASH code=' || r.CODIGO || ' site=' || r.SEDE_ID || ' recoded=' || r.new_code);
        END IF;
    END LOOP;

    IF v_failures IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20990, 'V023 precheck failed, nothing was merged: ' || v_failures);
    END IF;
END;
/

-- -----------------------------------------------------------------------------
-- 3. Captura ANTES: conteo + suma de control por tabla, filas por sede de las 5 tablas con clave de sede
-- Base de la suma de control: SUM(ORA_HASH(PUBLIC_ID)); SUM(ORA_HASH(ID)) si no hay PUBLIC_ID;
-- las columnas de la clave primaria si no hay ninguno; ROWID como último recurso. La expresión base
-- se guarda en DETALLE para que V024 (y V028) calculen exactamente lo mismo.
-- -----------------------------------------------------------------------------
DECLARE
    TYPE t_names IS TABLE OF VARCHAR2(30);
    c_site_tables CONSTANT t_names := t_names(
        'DISPONIBILIDAD_SLOT', 'DISPENSACION', 'EPISODIO_ATENCION', 'AREA_HOSPITALARIA', 'ALERTA_OPERATIVA');
    v_expr  VARCHAR2(4000);
    v_count NUMBER;
    v_cnt   NUMBER;
    v_sum   NUMBER;
BEGIN
    DELETE FROM CONCILIACION_MIGRACION
     WHERE MIGRACION = 'V023' AND FASE = 'ANTES' AND NOMBRE_TABLA <> 'PRECHECK';

    FOR t IN (SELECT TABLE_NAME FROM USER_TABLES
               WHERE UPPER(TABLE_NAME) <> 'FLYWAY_SCHEMA_HISTORY'
                 AND TABLE_NAME NOT IN ('CONCILIACION_MIGRACION', 'CONFIGURACION_HOSPITAL')
                 AND TABLE_NAME NOT LIKE 'BIN$%'
                 AND TEMPORARY = 'N' AND DROPPED = 'NO'
               ORDER BY TABLE_NAME) LOOP
        SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS WHERE TABLE_NAME = t.TABLE_NAME AND COLUMN_NAME = 'PUBLIC_ID';
        IF v_count > 0 THEN
            v_expr := 'ORA_HASH(PUBLIC_ID)';
        ELSE
            SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS WHERE TABLE_NAME = t.TABLE_NAME AND COLUMN_NAME = 'ID';
            IF v_count > 0 THEN
                v_expr := 'ORA_HASH(ID)';
            ELSE
                SELECT LISTAGG('"' || cc.COLUMN_NAME || '"', ' || ''|'' || ') WITHIN GROUP (ORDER BY cc.POSITION)
                  INTO v_expr
                  FROM USER_CONSTRAINTS c
                  JOIN USER_CONS_COLUMNS cc ON cc.CONSTRAINT_NAME = c.CONSTRAINT_NAME
                 WHERE c.TABLE_NAME = t.TABLE_NAME AND c.CONSTRAINT_TYPE = 'P';
                v_expr := CASE WHEN v_expr IS NULL THEN 'ORA_HASH(ROWID)' ELSE 'ORA_HASH(' || v_expr || ')' END;
            END IF;
        END IF;

        EXECUTE IMMEDIATE 'SELECT COUNT(*), NVL(SUM(' || v_expr || '), 0) FROM "' || t.TABLE_NAME || '"'
            INTO v_cnt, v_sum;
        INSERT INTO CONCILIACION_MIGRACION (MIGRACION, NOMBRE_TABLA, FASE, CANTIDAD_FILAS, SUMA_CONTROL_PUBLIC_ID, DETALLE)
        VALUES ('V023', t.TABLE_NAME, 'ANTES', v_cnt, v_sum, 'BASE=' || v_expr);
    END LOOP;

    -- Filas por sede (una fila por tabla y sede) para las cinco tablas con clave de sede.
    FOR i IN 1 .. c_site_tables.COUNT LOOP
        FOR s IN (SELECT ID FROM SEDE ORDER BY ID) LOOP
            EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM ' || c_site_tables(i) || ' WHERE SEDE_ID = :1' INTO v_cnt USING s.ID;
            INSERT INTO CONCILIACION_MIGRACION (MIGRACION, NOMBRE_TABLA, FASE, CANTIDAD_FILAS, DETALLE)
            VALUES ('V023', c_site_tables(i), 'ANTES', v_cnt, 'SEDE_ID=' || s.ID);
        END LOOP;
    END LOOP;
    COMMIT;
END;
/

-- -----------------------------------------------------------------------------
-- 4a. Singleton CONFIGURACION_HOSPITAL (lo llena V024)
-- -----------------------------------------------------------------------------
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'CONFIGURACION_HOSPITAL';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE q'[CREATE TABLE CONFIGURACION_HOSPITAL (
            ID NUMBER NOT NULL,
            PUBLIC_ID VARCHAR2(36 CHAR) NOT NULL,
            NIT VARCHAR2(20 CHAR) NOT NULL,
            RAZON_SOCIAL VARCHAR2(120 CHAR) NOT NULL,
            DIRECCION VARCHAR2(120 CHAR) NOT NULL,
            CIUDAD VARCHAR2(60 CHAR) NOT NULL,
            TELEFONO VARCHAR2(30 CHAR),
            CREATED_AT TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
            UPDATED_AT TIMESTAMP WITH TIME ZONE,
            CONSTRAINT PK_CONFIGURACION_HOSPITAL PRIMARY KEY (ID),
            CONSTRAINT UQ_CONFIG_HOSPITAL_PUBLIC_ID UNIQUE (PUBLIC_ID),
            CONSTRAINT CK_CONFIG_HOSPITAL_ID CHECK (ID = 1)
        )]';
    END IF;
END;
/

-- -----------------------------------------------------------------------------
-- 4b. ALERTA_OPERATIVA: vínculo con el episodio y nivel de triaje (nombres en español
-- desde V023; sustituyen a los nombres en inglés que tuvo esta migración antes de aplicarse).
-- -----------------------------------------------------------------------------
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'ALERTA_OPERATIVA' AND COLUMN_NAME = 'EPISODIO_ID';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ALERTA_OPERATIVA ADD (EPISODIO_ID NUMBER)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'ALERTA_OPERATIVA' AND COLUMN_NAME = 'NIVEL_TRIAJE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ALERTA_OPERATIVA ADD (NIVEL_TRIAJE VARCHAR2(10 CHAR))';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'FK_ALERTA_EPISODIO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ALERTA_OPERATIVA ADD CONSTRAINT FK_ALERTA_EPISODIO '
            || 'FOREIGN KEY (EPISODIO_ID) REFERENCES EPISODIO_ATENCION (ID)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'IX_ALERTA_EPISODIO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_ALERTA_EPISODIO ON ALERTA_OPERATIVA (EPISODIO_ID)';
    END IF;
END;
/

-- -----------------------------------------------------------------------------
-- 4c. Elimina el índice de alertas activas con clave de sede: colisiona cuando todas las alertas comparten una sede.
-- V025 lo reemplaza por UQ_ALERTA_OPERATIVA_ACTIVA (tipo + episodio).
-- -----------------------------------------------------------------------------
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'UQ_ALERTA_SEDE_TIPO_ACTIVA';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP INDEX UQ_ALERTA_SEDE_TIPO_ACTIVA';
    END IF;
END;
/

-- -----------------------------------------------------------------------------
-- 4d. Trigger de inmutabilidad de DISPENSACION, relajado de forma acotada para la fusión.
-- V024 es solo DML y debe reasignar DISPENSACION.SEDE_ID, pero el trigger de V013 rechaza todo
-- UPDATE. El reemplazo mantiene -20030/-20031 para todos, salvo que el PROPIETARIO DEL ESQUEMA
-- (el usuario de Flyway; el usuario de ejecución no tiene UPDATE sobre la tabla) puede cambiar SEDE_ID cuando
-- ninguna otra columna cambia. V025 restaura el comportamiento estricto.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TR_DISPENSACION_INMUTABILIDAD
BEFORE UPDATE OR DELETE ON DISPENSACION
FOR EACH ROW
BEGIN
    IF DELETING THEN
        RAISE_APPLICATION_ERROR(-20030, 'Prohibido eliminar registros de dispensacion farmaceutica.');
    END IF;
    IF UPDATING THEN
        IF SYS_CONTEXT('USERENV', 'SESSION_USER') = SYS_CONTEXT('USERENV', 'CURRENT_USER')
           AND :OLD.ID = :NEW.ID
           AND :OLD.PUBLIC_ID = :NEW.PUBLIC_ID
           AND :OLD.RECETA_ID = :NEW.RECETA_ID
           AND :OLD.USUARIO_ID = :NEW.USUARIO_ID
           AND (:OLD.OBSERVACIONES = :NEW.OBSERVACIONES OR (:OLD.OBSERVACIONES IS NULL AND :NEW.OBSERVACIONES IS NULL))
           AND :OLD.CREATED_AT = :NEW.CREATED_AT
           AND :OLD.SEDE_ID <> :NEW.SEDE_ID THEN
            NULL; -- el propietario reasigna SEDE_ID durante la fusión de hospital único
        ELSE
            RAISE_APPLICATION_ERROR(-20031, 'Los registros de dispensacion son inmutables tras su emision.');
        END IF;
    END IF;
END;
/
