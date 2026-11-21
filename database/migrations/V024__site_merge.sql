-- =============================================================================
-- MediTriaje - V024: fusión de hospital único, parte 2 (solo DML, un único bloque atómico)
-- Slice 1 (db-single-hospital), unidad de trabajo WU1a. Diseño: 02-oracle-design 2.3.
--
-- Toda la fusión es UN solo bloque anónimo: una excepción no controlada (incluidas las aserciones
-- de conciliación -20991) revierte todos los cambios del bloque, de modo que una aserción fallida
-- deja los datos exactamente como los dejó V023. Aquí no hay DDL ni COMMIT.
--
-- Pasos (diseño 2.3, V024):
--   1. Institución y sede canónicas + CONFIGURACION_HOSPITAL.
--   2. Eliminar duplicados de alertas activas (se resuelven, nunca se borran).
--   3. Recodificar los códigos de área en colisión como CODIGO-<SEDE_ID anterior>.
--   4. Reasignar SEDE_ID de las cinco tablas con clave de sede a la sede canónica.
--   5. Eliminar las filas SEDE e INSTITUCION no canónicas.
--   6. Capturar DESPUES y lanzar -20991 salvo que se cumplan todas las aserciones de conciliación.
--   7. Solo tras superar todas las aserciones: escribir la evidencia de la fusión (al final, de modo
--      que CONTROL_SISTEMA sea igual a su estado ANTES mientras corre el paso 6).
-- =============================================================================
DECLARE
    TYPE t_names IS TABLE OF VARCHAR2(30);
    TYPE t_ids   IS TABLE OF NUMBER;
    TYPE t_texts IS TABLE OF VARCHAR2(400);
    c_site_tables CONSTANT t_names := t_names(
        'DISPONIBILIDAD_SLOT', 'DISPENSACION', 'EPISODIO_ATENCION', 'AREA_HOSPITALARIA', 'ALERTA_OPERATIVA');

    v_canon_inst   NUMBER;
    v_canon_site   NUMBER;
    v_count        NUMBER;
    v_cnt          NUMBER;
    v_sum          NUMBER;
    v_before_total NUMBER;
    v_expr         VARCHAR2(4000);
    v_failures     VARCHAR2(4000);
    v_json         VARCHAR2(4000);
    v_site_ids     t_ids   := t_ids();
    v_inst_ids     t_ids   := t_ids();
    v_site_map     t_texts := t_texts();
    v_inst_map     t_texts := t_texts();
    v_site_name    VARCHAR2(80 CHAR);
    v_uuid         VARCHAR2(36 CHAR);
    v_site_list    VARCHAR2(1000);
    v_inst_list    VARCHAR2(1000);

    FUNCTION new_uuid RETURN VARCHAR2 IS
    BEGIN
        RETURN LOWER(REGEXP_REPLACE(RAWTOHEX(SYS_GUID()), '(.{8})(.{4})(.{4})(.{4})(.{12})', '\1-\2-\3-\4-\5'));
    END new_uuid;

    PROCEDURE fail(p_detail IN VARCHAR2) IS
    BEGIN
        IF v_failures IS NULL OR LENGTH(v_failures) < 1700 THEN
            v_failures := SUBSTR(v_failures || p_detail || '; ', 1, 1900);
        END IF;
    END fail;
BEGIN
    -- La captura ANTES de V023 debe existir; sin ella no se puede conciliar nada.
    SELECT COUNT(*) INTO v_count FROM CONCILIACION_MIGRACION
     WHERE MIGRACION = 'V023' AND FASE = 'ANTES' AND DETALLE LIKE 'BASE=%';
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20991, 'V024 reconciliation failed: no V023 BEFORE capture found');
    END IF;
    DELETE FROM CONCILIACION_MIGRACION WHERE MIGRACION = 'V024';

    -- 1. Institución canónica = la institución ACTIVO más antigua; sede canónica = la sede ACTIVO de menor ID de esa institución.
    SELECT MIN(ID) INTO v_canon_inst FROM INSTITUCION WHERE ESTADO = 'ACTIVO';
    IF v_canon_inst IS NULL THEN
        BEGIN
            SELECT INSTITUCION_ID INTO v_canon_inst FROM (
                SELECT INSTITUCION_ID FROM SEDE ORDER BY CASE ESTADO WHEN 'ACTIVO' THEN 0 ELSE 1 END, ID)
            WHERE ROWNUM = 1;
        EXCEPTION WHEN NO_DATA_FOUND THEN
            v_canon_inst := NULL;
        END;
    END IF;
    IF v_canon_inst IS NULL THEN
        -- Base vacía (nada que fusionar): crea el único hospital provisional que luego configura un administrador.
        SELECT COUNT(*) INTO v_count FROM INSTITUCION;
        IF v_count > 0 THEN
            RAISE_APPLICATION_ERROR(-20990, 'V024: no canonical institution can be derived');
        END IF;
        v_uuid := new_uuid();
        INSERT INTO INSTITUCION (PUBLIC_ID, NIT, RAZON_SOCIAL, ESTADO)
        VALUES (v_uuid, '000000000-0', 'HOSPITAL PENDING CONFIGURATION', 'ACTIVO')
        RETURNING ID INTO v_canon_inst;
    END IF;

    SELECT MIN(ID) INTO v_canon_site FROM SEDE WHERE INSTITUCION_ID = v_canon_inst AND ESTADO = 'ACTIVO';
    IF v_canon_site IS NULL THEN
        v_uuid := new_uuid();
        SELECT SUBSTR(RAZON_SOCIAL, 1, 80) INTO v_site_name FROM INSTITUCION WHERE ID = v_canon_inst;
        INSERT INTO SEDE (INSTITUCION_ID, PUBLIC_ID, NOMBRE, DIRECCION, CIUDAD, ESTADO)
        VALUES (v_canon_inst, v_uuid, v_site_name, 'N/A', 'N/A', 'ACTIVO')
        RETURNING ID INTO v_canon_site;
    END IF;

    SELECT COUNT(*) INTO v_count FROM CONFIGURACION_HOSPITAL;
    IF v_count = 0 THEN
        v_uuid := new_uuid();
        INSERT INTO CONFIGURACION_HOSPITAL (ID, PUBLIC_ID, NIT, RAZON_SOCIAL, DIRECCION, CIUDAD)
        SELECT 1, v_uuid, i.NIT, i.RAZON_SOCIAL, s.DIRECCION, s.CIUDAD
          FROM INSTITUCION i, SEDE s
         WHERE i.ID = v_canon_inst AND s.ID = v_canon_site;
    END IF;

    -- 2. Conserva la alerta activa más reciente por tipo (y episodio); resuelve las demás. UPDATE, no DELETE.
    UPDATE ALERTA_OPERATIVA
       SET ESTADO = 'RESUELTA', MOTIVO_RECONOCIMIENTO = 'MERGED_SITE'
     WHERE ID IN (SELECT ID FROM (
                      SELECT ID, ROW_NUMBER() OVER (PARTITION BY TIPO_ALERTA, NVL(EPISODIO_ID, 0)
                                                    ORDER BY CREADO_AT DESC, ID DESC) AS rn
                        FROM ALERTA_OPERATIVA WHERE ESTADO = 'ACTIVA')
                   WHERE rn > 1);

    -- 3. Recodifica los códigos de área en colisión. El conservador de un código es la fila de la sede canónica,
    --    si no hay, la de la sede menor; las demás pasan a CODIGO-<SEDE_ID anterior>.
    UPDATE AREA_HOSPITALARIA
       SET CODIGO = CODIGO || '-' || SEDE_ID
     WHERE ID IN (SELECT ID FROM (
                      SELECT ID, ROW_NUMBER() OVER (PARTITION BY CODIGO
                                 ORDER BY CASE WHEN SEDE_ID = v_canon_site THEN 0 ELSE 1 END, SEDE_ID, ID) AS rn
                        FROM AREA_HOSPITALARIA)
                   WHERE rn > 1);

    -- 4. Reasigna toda fila con clave de sede a la sede canónica.
    FOR i IN 1 .. c_site_tables.COUNT LOOP
        EXECUTE IMMEDIATE 'UPDATE ' || c_site_tables(i) || ' SET SEDE_ID = :1 WHERE SEDE_ID <> :2'
            USING v_canon_site, v_canon_site;
    END LOOP;

    -- 5. Registra lo que se fusiona (para la evidencia) y luego elimina las filas no canónicas.
    FOR s IN (SELECT ID, PUBLIC_ID FROM SEDE WHERE ID <> v_canon_site ORDER BY ID) LOOP
        v_site_ids.EXTEND;
        v_site_ids(v_site_ids.COUNT) := s.ID;
        v_site_map.EXTEND;
        v_site_map(v_site_map.COUNT) := 'REMOVED_ID=' || s.ID || ';PUBLIC_ID=' || s.PUBLIC_ID || ';MERGED_INTO=' || v_canon_site;
    END LOOP;
    FOR i IN (SELECT ID, PUBLIC_ID FROM INSTITUCION WHERE ID <> v_canon_inst ORDER BY ID) LOOP
        v_inst_ids.EXTEND;
        v_inst_ids(v_inst_ids.COUNT) := i.ID;
        v_inst_map.EXTEND;
        v_inst_map(v_inst_map.COUNT) := 'REMOVED_ID=' || i.ID || ';PUBLIC_ID=' || i.PUBLIC_ID || ';MERGED_INTO=' || v_canon_inst;
    END LOOP;
    DELETE FROM SEDE WHERE ID <> v_canon_site;
    DELETE FROM INSTITUCION WHERE ID <> v_canon_inst;

    -- 6. Captura DESPUES (misma base de suma de control que V023) y aserciones de conciliación.
    v_before_total := 0;
    FOR b IN (SELECT NOMBRE_TABLA, CANTIDAD_FILAS, SUMA_CONTROL_PUBLIC_ID, DETALLE
                FROM CONCILIACION_MIGRACION
               WHERE MIGRACION = 'V023' AND FASE = 'ANTES' AND DETALLE LIKE 'BASE=%'
               ORDER BY NOMBRE_TABLA) LOOP
        v_before_total := v_before_total + 1;
        v_expr := SUBSTR(b.DETALLE, 6);
        SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = b.NOMBRE_TABLA;
        IF v_count = 0 THEN
            fail('MISSING_TABLE table=' || b.NOMBRE_TABLA);
        ELSE
            EXECUTE IMMEDIATE 'SELECT COUNT(*), NVL(SUM(' || v_expr || '), 0) FROM "' || b.NOMBRE_TABLA || '"'
                INTO v_cnt, v_sum;
            INSERT INTO CONCILIACION_MIGRACION (MIGRACION, NOMBRE_TABLA, FASE, CANTIDAD_FILAS, SUMA_CONTROL_PUBLIC_ID, DETALLE)
            VALUES ('V024', b.NOMBRE_TABLA, 'DESPUES', v_cnt, v_sum, b.DETALLE);

            IF b.NOMBRE_TABLA IN ('SEDE', 'INSTITUCION') THEN
                IF v_cnt <> 1 THEN
                    fail('NOT_SINGLE table=' || b.NOMBRE_TABLA || ' rows=' || v_cnt);
                END IF;
            ELSIF v_cnt <> b.CANTIDAD_FILAS OR v_sum <> b.SUMA_CONTROL_PUBLIC_ID THEN
                fail('MISMATCH table=' || b.NOMBRE_TABLA || ' count=' || b.CANTIDAD_FILAS || '/' || v_cnt
                     || ' checksum=' || b.SUMA_CONTROL_PUBLIC_ID || '/' || v_sum);
            END IF;
        END IF;
    END LOOP;

    -- Ninguna tabla puede aparecer ni desaparecer entre ANTES y DESPUES (se excluyen las tablas de contabilidad).
    SELECT COUNT(*) INTO v_count FROM USER_TABLES
     WHERE UPPER(TABLE_NAME) <> 'FLYWAY_SCHEMA_HISTORY'
       AND TABLE_NAME NOT IN ('CONCILIACION_MIGRACION', 'CONFIGURACION_HOSPITAL')
       AND TABLE_NAME NOT LIKE 'BIN$%' AND TEMPORARY = 'N' AND DROPPED = 'NO';
    IF v_count <> v_before_total THEN
        fail('TABLE_SET tables_before=' || v_before_total || ' tables_after=' || v_count);
    END IF;

    -- Por tabla: ninguna fila en una sede no canónica y los conteos por sede ANTES suman el conteo DESPUES.
    FOR i IN 1 .. c_site_tables.COUNT LOOP
        EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM ' || c_site_tables(i) || ' WHERE SEDE_ID <> :1' INTO v_cnt USING v_canon_site;
        IF v_cnt > 0 THEN
            fail('STRAY_SITE table=' || c_site_tables(i) || ' rows=' || v_cnt);
        END IF;
        SELECT NVL(SUM(CANTIDAD_FILAS), 0) INTO v_sum FROM CONCILIACION_MIGRACION
         WHERE MIGRACION = 'V023' AND FASE = 'ANTES' AND NOMBRE_TABLA = c_site_tables(i) AND DETALLE LIKE 'SEDE_ID=%';
        SELECT CANTIDAD_FILAS INTO v_cnt FROM CONCILIACION_MIGRACION
         WHERE MIGRACION = 'V024' AND FASE = 'DESPUES' AND NOMBRE_TABLA = c_site_tables(i) AND DETALLE LIKE 'BASE=%';
        IF v_sum <> v_cnt THEN
            fail('PER_SITE_SUM table=' || c_site_tables(i) || ' before_sites=' || v_sum || ' after=' || v_cnt);
        END IF;
    END LOOP;

    -- Una sede, una institución, una fila de configuración, parentesco consistente.
    SELECT COUNT(*) INTO v_count FROM SEDE WHERE ID = v_canon_site AND INSTITUCION_ID = v_canon_inst;
    IF v_count <> 1 THEN
        fail('CANONICAL_PARENTAGE site=' || v_canon_site || ' institution=' || v_canon_inst);
    END IF;
    SELECT COUNT(*) INTO v_count FROM CONFIGURACION_HOSPITAL;
    IF v_count <> 1 THEN
        fail('SETTINGS_ROWS rows=' || v_count);
    END IF;

    -- Sin alertas activas duplicadas ni (sede, código de área) duplicados.
    SELECT COUNT(*) INTO v_count FROM (
        SELECT 1 FROM ALERTA_OPERATIVA WHERE ESTADO = 'ACTIVA'
         GROUP BY TIPO_ALERTA, NVL(EPISODIO_ID, 0) HAVING COUNT(*) > 1);
    IF v_count > 0 THEN
        fail('DUPLICATE_ACTIVE_ALERT groups=' || v_count);
    END IF;
    SELECT COUNT(*) INTO v_count FROM (
        SELECT 1 FROM AREA_HOSPITALARIA GROUP BY SEDE_ID, CODIGO HAVING COUNT(*) > 1);
    IF v_count > 0 THEN
        fail('DUPLICATE_AREA_CODE groups=' || v_count);
    END IF;

    IF v_failures IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20991, 'V024 reconciliation failed, merge rolled back: ' || v_failures);
    END IF;

    -- 7. Evidencia, escrita al final. CONCILIACION_MIGRACION y CONFIGURACION_HOSPITAL nunca forman parte de la
    --    captura, y CONTROL_SISTEMA se escribe solo después de comparar su estado DESPUES.
    FOR i IN 1 .. v_site_map.COUNT LOOP
        INSERT INTO CONCILIACION_MIGRACION (MIGRACION, NOMBRE_TABLA, FASE, DETALLE)
        VALUES ('V024', 'SEDE', 'MAPA_FUSION', v_site_map(i));
        v_site_list := v_site_list || CASE WHEN i > 1 THEN ',' END || v_site_ids(i);
    END LOOP;
    FOR i IN 1 .. v_inst_map.COUNT LOOP
        INSERT INTO CONCILIACION_MIGRACION (MIGRACION, NOMBRE_TABLA, FASE, DETALLE)
        VALUES ('V024', 'INSTITUCION', 'MAPA_FUSION', v_inst_map(i));
        v_inst_list := v_inst_list || CASE WHEN i > 1 THEN ',' END || v_inst_ids(i);
    END LOOP;

    v_json := '{"canonicalSiteId":' || v_canon_site || ',"canonicalInstitutionId":' || v_canon_inst
        || ',"mergedSiteIds":[' || v_site_list || '],"mergedInstitutionIds":[' || v_inst_list || ']}';
    IF LENGTH(v_json) > 255 THEN
        -- El mapa completo está en CONCILIACION_MIGRACION (FASE = MAPA_FUSION); VALOR se mantiene como JSON válido y corto.
        v_json := '{"canonicalSiteId":' || v_canon_site || ',"canonicalInstitutionId":' || v_canon_inst
            || ',"mergedSiteCount":' || v_site_ids.COUNT || ',"mergedInstitutionCount":' || v_inst_ids.COUNT || '}';
    END IF;
    INSERT INTO CONTROL_SISTEMA (CLAVE, VALOR, DESCRIPCION)
    VALUES ('SITE_MERGE_EVIDENCE', v_json,
            'V024 single-hospital merge: ids of the merged sites and institutions (full map in CONCILIACION_MIGRACION)');
END;
/
