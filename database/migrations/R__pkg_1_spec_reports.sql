-- =============================================================================
-- MediTriaje - R__pkg_1_spec_reports: especificación de PKG_REPORTS
-- Repetible. Métricas e indicadores operativos agregados (F2.6, RF-30, ADR-018): solo conteos y
-- distribuciones, sin datos clínicos identificables (ADR-007). Derechos del definidor; sin COMMIT ni ROLLBACK.
-- En todas las rutinas p_from y p_to filtran por fecha de creación (inclusive) y un valor nulo no filtra.
-- =============================================================================
CREATE OR REPLACE PACKAGE PKG_REPORTS AUTHID DEFINER AS

    -- ------------------------------------------------------------------ CITAS
    -- Citas agrupadas por estado. Columnas: ESTADO, TOTAL.
    PROCEDURE count_appointments_by_status(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                           o_rows OUT SYS_REFCURSOR);

    -- Citas por especialidad, de mayor a menor. Columnas: PUBLIC_ID, NOMBRE, TOTAL.
    PROCEDURE list_appointments_by_specialty(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                             o_rows OUT SYS_REFCURSOR);

    -- Citas por sede, de mayor a menor. Columnas: PUBLIC_ID, NOMBRE, TOTAL.
    PROCEDURE list_appointments_by_site(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                        o_rows OUT SYS_REFCURSOR);

    -- ---------------------------------------------------------------- TRIAJES
    -- Triajes agrupados por nivel de prioridad. Columnas: NIVEL_PRIORIDAD, TOTAL.
    PROCEDURE count_triages_by_level(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                     o_rows OUT SYS_REFCURSOR);

    -- Total de triajes marcados como emergencia (ES_EMERGENCIA = 1).
    FUNCTION count_emergency_triages(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE)
        RETURN NUMBER;

    -- Triajes por ruta sugerida, de mayor a menor. Columnas: RUTA_SUGERIDA, TOTAL.
    PROCEDURE list_triages_by_route(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                    o_rows OUT SYS_REFCURSOR);

    -- ------------------------------------------------------- RECETAS Y FARMACIA
    -- Total de recetas emitidas.
    FUNCTION count_prescriptions(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE)
        RETURN NUMBER;

    -- Total de unidades entregadas en dispensaciones (0 si no hay).
    FUNCTION sum_dispensed_units(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE)
        RETURN NUMBER;

    -- Recetas según su estado de dispensación (una fila). Columnas: PENDIENTES, PARCIALES, TOTALES.
    PROCEDURE count_dispensation_statuses(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                          o_rows OUT SYS_REFCURSOR);

    -- ------------------------------------------------------------ BREAK-GLASS
    -- Total de activaciones Break-Glass.
    FUNCTION count_break_glass_activations(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE)
        RETURN NUMBER;

    -- Total de accesos Break-Glass no expirados (FECHA_EXPIRACION > p_now).
    FUNCTION count_break_glass_active(p_now IN TIMESTAMP WITH TIME ZONE) RETURN NUMBER;

    -- Activaciones Break-Glass por especialidad del profesional, de mayor a menor. Columnas: PUBLIC_ID, NOMBRE, TOTAL.
    PROCEDURE list_break_glass_by_specialty(p_from IN TIMESTAMP WITH TIME ZONE, p_to IN TIMESTAMP WITH TIME ZONE,
                                            o_rows OUT SYS_REFCURSOR);
END PKG_REPORTS;
/
