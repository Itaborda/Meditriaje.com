-- =============================================================================
-- MediTriaje 2.0 — Migración V027: Ampliación defensiva de PIN_HASH en ACCESO_TEMPORAL_QR
-- =============================================================================
-- Los hashes Argon2id actuales ocupan 97 caracteres, muy cerca del límite previo
-- de VARCHAR2(100 CHAR). Se amplía a VARCHAR2(255 CHAR) en línea con USUARIO.PASSWORD_HASH.
-- =============================================================================

ALTER TABLE ACCESO_TEMPORAL_QR MODIFY (PIN_HASH VARCHAR2(255 CHAR));
