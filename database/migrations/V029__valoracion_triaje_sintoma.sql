-- =============================================================================
-- MediTriaje 2.0 — Migración V029: Síntomas de valoración clínica de triaje
-- =============================================================================
-- Persiste los síntomas reportados en cada valoración presencial de urgencias
-- (VALORACION_TRIAJE) para cálculo determinista automático del nivel de prioridad
-- e historial clínico inmutable (append-only).
-- =============================================================================

CREATE TABLE VALORACION_TRIAJE_SINTOMA (
    ID NUMBER GENERATED ALWAYS AS IDENTITY,
    VALORACION_ID NUMBER NOT NULL,
    SINTOMA_ID NUMBER NOT NULL,
    DURACION_HORAS NUMBER NOT NULL,
    INTENSIDAD NUMBER NOT NULL,
    CONSTRAINT PK_VAL_TRIAJE_SINTOMA PRIMARY KEY (ID),
    CONSTRAINT UQ_VAL_TRIAJE_SINTOMA UNIQUE (VALORACION_ID, SINTOMA_ID),
    CONSTRAINT FK_VTS_VALORACION FOREIGN KEY (VALORACION_ID) REFERENCES VALORACION_TRIAJE(ID),
    CONSTRAINT FK_VTS_SINTOMA FOREIGN KEY (SINTOMA_ID) REFERENCES SINTOMA(ID),
    CONSTRAINT CK_VTS_INTENSIDAD CHECK (INTENSIDAD BETWEEN 0 AND 10),
    CONSTRAINT CK_VTS_DURACION CHECK (DURACION_HORAS >= 0)
);

CREATE INDEX IX_VAL_TRIAJE_SINTOMA_VAL ON VALORACION_TRIAJE_SINTOMA (VALORACION_ID);

-- Trigger de inmutabilidad estricta: bloquea UPDATE y DELETE
CREATE OR REPLACE TRIGGER TR_VAL_TRIAJE_SINTOMA_INMUT
BEFORE UPDATE OR DELETE ON VALORACION_TRIAJE_SINTOMA
FOR EACH ROW
BEGIN
    RAISE_APPLICATION_ERROR(-20045, 'Los sintomas registrados en una valoracion clinica de triaje son inmutables.');
END;
/

DECLARE
    v_app_user CONSTANT VARCHAR2(30) := 'MEDITRIAJE_APP';
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_USERS WHERE USERNAME = v_app_user;
    IF v_count > 0 AND v_app_user <> SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') THEN
        EXECUTE IMMEDIATE 'GRANT SELECT, INSERT ON VALORACION_TRIAJE_SINTOMA TO ' || v_app_user;
    END IF;
END;
/
