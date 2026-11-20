-- =============================================================================
-- MediTriaje - R__pkg_2_body_identity: cuerpo de PKG_IDENTITY
-- Repetible. Sin COMMIT ni ROLLBACK (la transacción pertenece a la capa de aplicación).
-- =============================================================================
CREATE OR REPLACE PACKAGE BODY PKG_IDENTITY AS

    -- ---------------------------------------------------------------- USUARIO

    FUNCTION count_users_by_email(p_email IN VARCHAR2) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USUARIO WHERE EMAIL = p_email;
        RETURN v_count;
    END count_users_by_email;

    PROCEDURE create_user(p_public_id IN VARCHAR2, p_email IN VARCHAR2, p_password_hash IN VARCHAR2,
                          p_must_change_password IN NUMBER, o_user_id OUT NUMBER) IS
    BEGIN
        INSERT INTO USUARIO (PUBLIC_ID, EMAIL, PASSWORD_HASH, ESTADO, INTENTOS_FALLIDOS, DEBE_CAMBIAR_PASSWORD)
        VALUES (p_public_id, p_email, p_password_hash, 'ACTIVO', 0, p_must_change_password)
        RETURNING ID INTO o_user_id;
    END create_user;

    PROCEDURE get_user_by_email(p_email IN VARCHAR2, o_user OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_user FOR
            SELECT ID, PUBLIC_ID, EMAIL, PASSWORD_HASH, ESTADO, INTENTOS_FALLIDOS, BLOQUEADO_HASTA, CREATED_AT,
                   DEBE_CAMBIAR_PASSWORD, MFA_HABILITADO, MFA_SECRET, MFA_CONFIGURADO_AT
              FROM USUARIO
             WHERE EMAIL = p_email;
    END get_user_by_email;

    PROCEDURE get_user_by_id(p_user_id IN NUMBER, o_user OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_user FOR
            SELECT ID, PUBLIC_ID, EMAIL, PASSWORD_HASH, ESTADO, INTENTOS_FALLIDOS, BLOQUEADO_HASTA, CREATED_AT,
                   DEBE_CAMBIAR_PASSWORD, MFA_HABILITADO, MFA_SECRET, MFA_CONFIGURADO_AT
              FROM USUARIO
             WHERE ID = p_user_id;
    END get_user_by_id;

    PROCEDURE get_user_by_public_id(p_public_id IN VARCHAR2, o_user OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_user FOR
            SELECT ID, PUBLIC_ID, EMAIL, PASSWORD_HASH, ESTADO, INTENTOS_FALLIDOS, BLOQUEADO_HASTA, CREATED_AT,
                   DEBE_CAMBIAR_PASSWORD, MFA_HABILITADO, MFA_SECRET, MFA_CONFIGURADO_AT
              FROM USUARIO
             WHERE PUBLIC_ID = p_public_id;
    END get_user_by_public_id;

    PROCEDURE list_user_roles(p_user_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT r.NOMBRE
              FROM ROL r
              JOIN USUARIO_ROL ur ON ur.ROL_ID = r.ID
             WHERE ur.USUARIO_ID = p_user_id
             ORDER BY r.NOMBRE;
    END list_user_roles;

    PROCEDURE update_failed_attempts(p_user_id IN NUMBER, p_attempts IN NUMBER,
                                     p_locked_until IN TIMESTAMP WITH TIME ZONE, p_status IN VARCHAR2) IS
    BEGIN
        UPDATE USUARIO
           SET INTENTOS_FALLIDOS = p_attempts, BLOQUEADO_HASTA = p_locked_until, ESTADO = p_status,
               UPDATED_AT = CURRENT_TIMESTAMP
         WHERE ID = p_user_id;
    END update_failed_attempts;

    PROCEDURE reset_failed_attempts(p_user_id IN NUMBER) IS
    BEGIN
        UPDATE USUARIO
           SET INTENTOS_FALLIDOS = 0, BLOQUEADO_HASTA = NULL, ESTADO = 'ACTIVO', UPDATED_AT = CURRENT_TIMESTAMP
         WHERE ID = p_user_id;
    END reset_failed_attempts;

    FUNCTION get_role_id_by_name(p_name IN VARCHAR2) RETURN NUMBER IS
        v_id ROL.ID%TYPE;
    BEGIN
        SELECT ID INTO v_id FROM ROL WHERE NOMBRE = p_name;
        RETURN v_id;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RETURN NULL;
    END get_role_id_by_name;

    PROCEDURE assign_role(p_user_id IN NUMBER, p_role_id IN NUMBER) IS
    BEGIN
        INSERT INTO USUARIO_ROL (USUARIO_ID, ROL_ID) VALUES (p_user_id, p_role_id);
    END assign_role;

    PROCEDURE update_password(p_user_id IN NUMBER, p_password_hash IN VARCHAR2, p_must_change_password IN NUMBER) IS
    BEGIN
        UPDATE USUARIO
           SET PASSWORD_HASH = p_password_hash, DEBE_CAMBIAR_PASSWORD = p_must_change_password, INTENTOS_FALLIDOS = 0,
               BLOQUEADO_HASTA = NULL, UPDATED_AT = CURRENT_TIMESTAMP
         WHERE ID = p_user_id;
    END update_password;

    PROCEDURE update_status(p_user_id IN NUMBER, p_status IN VARCHAR2) IS
    BEGIN
        UPDATE USUARIO SET ESTADO = p_status, UPDATED_AT = CURRENT_TIMESTAMP WHERE ID = p_user_id;
    END update_status;

    PROCEDURE save_mfa_secret(p_user_id IN NUMBER, p_mfa_secret IN VARCHAR2) IS
    BEGIN
        UPDATE USUARIO SET MFA_SECRET = p_mfa_secret, UPDATED_AT = CURRENT_TIMESTAMP WHERE ID = p_user_id;
    END save_mfa_secret;

    PROCEDURE enable_mfa(p_user_id IN NUMBER, p_mfa_secret IN VARCHAR2) IS
    BEGIN
        UPDATE USUARIO
           SET MFA_HABILITADO = 1, MFA_SECRET = p_mfa_secret, MFA_CONFIGURADO_AT = CURRENT_TIMESTAMP,
               UPDATED_AT = CURRENT_TIMESTAMP
         WHERE ID = p_user_id;
    END enable_mfa;

    PROCEDURE disable_mfa(p_user_id IN NUMBER) IS
    BEGIN
        UPDATE USUARIO
           SET MFA_HABILITADO = 0, MFA_SECRET = NULL, MFA_CONFIGURADO_AT = NULL, UPDATED_AT = CURRENT_TIMESTAMP
         WHERE ID = p_user_id;
    END disable_mfa;

    -- ---------------------------------------------------------- REFRESH_TOKEN

    PROCEDURE create_refresh_token(p_user_id IN NUMBER, p_token_hash IN VARCHAR2,
                                   p_expires_at IN TIMESTAMP WITH TIME ZONE, o_token_id OUT NUMBER) IS
    BEGIN
        INSERT INTO REFRESH_TOKEN (USUARIO_ID, TOKEN_HASH, EXPIRACION, REVOCADO)
        VALUES (p_user_id, p_token_hash, p_expires_at, 0)
        RETURNING ID INTO o_token_id;
    END create_refresh_token;

    PROCEDURE get_refresh_token_by_hash(p_token_hash IN VARCHAR2, o_token OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_token FOR
            SELECT ID, USUARIO_ID, TOKEN_HASH, EXPIRACION, REVOCADO, CREATED_AT
              FROM REFRESH_TOKEN
             WHERE TOKEN_HASH = p_token_hash;
    END get_refresh_token_by_hash;

    PROCEDURE revoke_refresh_token(p_token_id IN NUMBER) IS
    BEGIN
        UPDATE REFRESH_TOKEN SET REVOCADO = 1 WHERE ID = p_token_id;
    END revoke_refresh_token;

    PROCEDURE revoke_refresh_token_by_hash(p_token_hash IN VARCHAR2) IS
    BEGIN
        UPDATE REFRESH_TOKEN SET REVOCADO = 1 WHERE TOKEN_HASH = p_token_hash;
    END revoke_refresh_token_by_hash;

    PROCEDURE revoke_user_refresh_tokens(p_user_id IN NUMBER) IS
    BEGIN
        UPDATE REFRESH_TOKEN SET REVOCADO = 1 WHERE USUARIO_ID = p_user_id AND REVOCADO = 0;
    END revoke_user_refresh_tokens;

    -- -------------------------------------------------------- MFA_BACKUP_CODE

    PROCEDURE add_mfa_backup_code(p_user_id IN NUMBER, p_code_hash IN VARCHAR2) IS
    BEGIN
        INSERT INTO MFA_BACKUP_CODE (USUARIO_ID, CODE_HASH, USADO) VALUES (p_user_id, p_code_hash, 0);
    END add_mfa_backup_code;

    PROCEDURE delete_mfa_backup_codes(p_user_id IN NUMBER) IS
    BEGIN
        DELETE FROM MFA_BACKUP_CODE WHERE USUARIO_ID = p_user_id;
    END delete_mfa_backup_codes;

    PROCEDURE consume_mfa_backup_code(p_user_id IN NUMBER, p_code_hash IN VARCHAR2, o_updated OUT NUMBER) IS
    BEGIN
        UPDATE MFA_BACKUP_CODE
           SET USADO = 1, USADO_AT = CURRENT_TIMESTAMP
         WHERE USUARIO_ID = p_user_id AND CODE_HASH = p_code_hash AND USADO = 0;
        o_updated := SQL%ROWCOUNT;
    END consume_mfa_backup_code;

    FUNCTION count_available_backup_codes(p_user_id IN NUMBER) RETURN NUMBER IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM MFA_BACKUP_CODE WHERE USUARIO_ID = p_user_id AND USADO = 0;
        RETURN v_count;
    END count_available_backup_codes;

    PROCEDURE list_mfa_backup_codes(p_user_id IN NUMBER, o_rows OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_rows FOR
            SELECT ID, USUARIO_ID, CODE_HASH, USADO, USADO_AT, CREATED_AT
              FROM MFA_BACKUP_CODE
             WHERE USUARIO_ID = p_user_id
             ORDER BY ID ASC;
    END list_mfa_backup_codes;

    -- ---------------------------------------------------- CODIGO_VERIFICACION

    PROCEDURE create_verification_code(p_public_id IN VARCHAR2, p_user_id IN NUMBER, p_type IN VARCHAR2,
                                       p_code_hash IN VARCHAR2, p_expires_at IN TIMESTAMP WITH TIME ZONE,
                                       p_failed_attempts IN NUMBER, p_max_attempts IN NUMBER, p_used IN NUMBER,
                                       o_code_id OUT NUMBER) IS
    BEGIN
        INSERT INTO CODIGO_VERIFICACION (
            PUBLIC_ID, USUARIO_ID, TIPO, CODIGO_HASH, FECHA_EXPIRACION, INTENTOS_FALLIDOS, MAX_INTENTOS, USADO
        ) VALUES (
            p_public_id, p_user_id, p_type, p_code_hash, p_expires_at, p_failed_attempts, p_max_attempts, p_used
        )
        RETURNING ID INTO o_code_id;
    END create_verification_code;

    PROCEDURE get_verification_code_by_id(p_code_id IN NUMBER, o_code OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_code FOR
            SELECT ID, PUBLIC_ID, USUARIO_ID, TIPO, CODIGO_HASH, FECHA_EXPIRACION, INTENTOS_FALLIDOS, MAX_INTENTOS,
                   USADO, CREATED_AT
              FROM CODIGO_VERIFICACION
             WHERE ID = p_code_id;
    END get_verification_code_by_id;

    PROCEDURE get_latest_pending_code(p_user_id IN NUMBER, p_type IN VARCHAR2, o_code OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN o_code FOR
            SELECT ID, PUBLIC_ID, USUARIO_ID, TIPO, CODIGO_HASH, FECHA_EXPIRACION, INTENTOS_FALLIDOS, MAX_INTENTOS,
                   USADO, CREATED_AT
              FROM CODIGO_VERIFICACION
             WHERE USUARIO_ID = p_user_id AND TIPO = p_type AND USADO = 0
             ORDER BY CREATED_AT DESC, ID DESC
             FETCH FIRST 1 ROWS ONLY;
    END get_latest_pending_code;

    PROCEDURE increment_code_attempts(p_code_id IN NUMBER) IS
    BEGIN
        UPDATE CODIGO_VERIFICACION SET INTENTOS_FALLIDOS = INTENTOS_FALLIDOS + 1 WHERE ID = p_code_id;
    END increment_code_attempts;

    PROCEDURE mark_code_used(p_code_id IN NUMBER) IS
    BEGIN
        UPDATE CODIGO_VERIFICACION SET USADO = 1 WHERE ID = p_code_id;
    END mark_code_used;

    PROCEDURE invalidate_pending_codes(p_user_id IN NUMBER, p_type IN VARCHAR2) IS
    BEGIN
        UPDATE CODIGO_VERIFICACION SET USADO = 1 WHERE USUARIO_ID = p_user_id AND TIPO = p_type AND USADO = 0;
    END invalidate_pending_codes;
END PKG_IDENTITY;
/
