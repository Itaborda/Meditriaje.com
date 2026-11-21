-- =============================================================================
-- MediTriaje - R__pkg_2_body_reports: cuerpo de PKG_REPORTS
-- Repetible. Sin COMMIT ni ROLLBACK (la transacción pertenece a la capa de aplicación).
-- =============================================================================
CREATE OR REPLACE PACKAGE BODY PKG_REPORTS AS

    -- ------------------------------------------------------------------ CITAS

    PROCEDURE count_appointments_by_status(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                           o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ESTADO, COUNT(*) AS TOTAL
              FROM CITA
             WHERE (p_from IS NULL OR CREATED_AT >= p_from)
               AND (p_to IS NULL OR CREATED_AT <= p_to)
             GROUP BY ESTADO;
    END count_appointments_by_status;

    PROCEDURE list_appointments_by_specialty(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                             o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT esp.PUBLIC_ID, esp.NOMBRE, COUNT(c.ID) AS TOTAL
              FROM CITA c
              JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
              JOIN ESPECIALIDAD esp ON s.ESPECIALIDAD_ID = esp.ID
             WHERE (p_from IS NULL OR c.CREATED_AT >= p_from)
               AND (p_to IS NULL OR c.CREATED_AT <= p_to)
             GROUP BY esp.PUBLIC_ID, esp.NOMBRE
             ORDER BY TOTAL DESC;
    END list_appointments_by_specialty;

    PROCEDURE list_appointments_by_site(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                        o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT cfg.PUBLIC_ID, cfg.RAZON_SOCIAL AS NOMBRE, COUNT(c.ID) AS TOTAL
              FROM CITA c
              CROSS JOIN CONFIGURACION_HOSPITAL cfg
             WHERE (p_from IS NULL OR c.CREATED_AT >= p_from)
               AND (p_to IS NULL OR c.CREATED_AT <= p_to)
             GROUP BY cfg.PUBLIC_ID, cfg.RAZON_SOCIAL
             ORDER BY TOTAL DESC;
    END list_appointments_by_site;

    -- ---------------------------------------------------------------- TRIAJES

    PROCEDURE count_triages_by_level(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                     o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT NIVEL_PRIORIDAD, COUNT(*) AS TOTAL
              FROM TRIAJE
             WHERE (p_from IS NULL OR CREATED_AT >= p_from)
               AND (p_to IS NULL OR CREATED_AT <= p_to)
             GROUP BY NIVEL_PRIORIDAD;
    END count_triages_by_level;

    FUNCTION count_emergency_triages(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE)
        RETURN NUMBER IS
        v_total NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_total
          FROM TRIAJE
         WHERE ES_EMERGENCIA = 1
           AND (p_from IS NULL OR CREATED_AT >= p_from)
           AND (p_to IS NULL OR CREATED_AT <= p_to);
        RETURN v_total;
    END count_emergency_triages;

    PROCEDURE list_triages_by_route(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                    o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT RUTA_SUGERIDA, COUNT(*) AS TOTAL
              FROM TRIAJE
             WHERE (p_from IS NULL OR CREATED_AT >= p_from)
               AND (p_to IS NULL OR CREATED_AT <= p_to)
             GROUP BY RUTA_SUGERIDA
             ORDER BY TOTAL DESC;
    END list_triages_by_route;

    -- ------------------------------------------------------- RECETAS Y FARMACIA

    FUNCTION count_prescriptions(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE)
        RETURN NUMBER IS
        v_total NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_total
          FROM RECETA
         WHERE (p_from IS NULL OR CREATED_AT >= p_from)
           AND (p_to IS NULL OR CREATED_AT <= p_to);
        RETURN v_total;
    END count_prescriptions;

    FUNCTION sum_dispensed_units(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE)
        RETURN NUMBER IS
        v_total NUMBER;
    BEGIN
        SELECT COALESCE(SUM(dd.CANTIDAD_ENTREGADA), 0)
          INTO v_total
          FROM DISPENSACION_DETALLE dd
          JOIN DISPENSACION d ON dd.DISPENSACION_ID = d.ID
         WHERE (p_from IS NULL OR d.CREATED_AT >= p_from)
           AND (p_to IS NULL OR d.CREATED_AT <= p_to);
        RETURN v_total;
    END sum_dispensed_units;

    PROCEDURE count_dispensation_statuses(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                          o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT COALESCE(SUM(CASE WHEN entregado = 0 THEN 1 ELSE 0 END), 0) AS PENDIENTES,
                   COALESCE(SUM(CASE WHEN entregado > 0 AND entregado < prescrito THEN 1 ELSE 0 END), 0) AS PARCIALES,
                   COALESCE(SUM(CASE WHEN entregado >= prescrito AND prescrito > 0 THEN 1 ELSE 0 END), 0) AS TOTALES
              FROM (
                  SELECT r.ID,
                         SUM(rd.CANTIDAD) AS prescrito,
                         COALESCE(SUM(dd_tot.TOTAL_ENTREGADO), 0) AS entregado
                    FROM RECETA r
                    JOIN RECETA_DETALLE rd ON rd.RECETA_ID = r.ID
                    LEFT JOIN (
                        SELECT dd.RECETA_DETALLE_ID, SUM(dd.CANTIDAD_ENTREGADA) AS TOTAL_ENTREGADO
                          FROM DISPENSACION_DETALLE dd
                         GROUP BY dd.RECETA_DETALLE_ID
                    ) dd_tot ON dd_tot.RECETA_DETALLE_ID = rd.ID
                   WHERE (p_from IS NULL OR r.CREATED_AT >= p_from)
                     AND (p_to IS NULL OR r.CREATED_AT <= p_to)
                   GROUP BY r.ID
              ) subq;
    END count_dispensation_statuses;

    -- ------------------------------------------------------------ BREAK-GLASS

    FUNCTION count_break_glass_activations(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE)
        RETURN NUMBER IS
        v_total NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_total
          FROM ACCESO_BREAK_GLASS
         WHERE (p_from IS NULL OR CREATED_AT >= p_from)
           AND (p_to IS NULL OR CREATED_AT <= p_to);
        RETURN v_total;
    END count_break_glass_activations;

    FUNCTION count_break_glass_active(p_now IN TIMESTAMP WITH TIME ZONE) RETURN NUMBER IS
        v_total NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_total
          FROM ACCESO_BREAK_GLASS
         WHERE FECHA_EXPIRACION > p_now;
        RETURN v_total;
    END count_break_glass_active;

    PROCEDURE list_break_glass_by_specialty(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                            o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT esp.PUBLIC_ID, esp.NOMBRE, COUNT(bg.ID) AS TOTAL
              FROM ACCESO_BREAK_GLASS bg
              JOIN PROFESIONAL p ON bg.PROFESIONAL_ID = p.ID
              JOIN ESPECIALIDAD esp ON p.ESPECIALIDAD_ID = esp.ID
             WHERE (p_from IS NULL OR bg.CREATED_AT >= p_from)
               AND (p_to IS NULL OR bg.CREATED_AT <= p_to)
             GROUP BY esp.PUBLIC_ID, esp.NOMBRE
             ORDER BY TOTAL DESC;
    END list_break_glass_by_specialty;
END PKG_REPORTS;
/
