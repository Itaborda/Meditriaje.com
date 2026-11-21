-- =============================================================================
-- MediTriaje - R__pkg_1_spec_identity: especificación de PKG_IDENTITY
-- Repetible. Identidad y credenciales: usuarios y roles, refresh tokens, códigos de respaldo MFA y
-- códigos de verificación. Derechos del definidor; sin COMMIT ni ROLLBACK (la transacción es de Java).
-- Los indicadores lógicos se reciben y devuelven como NUMBER (0/1).
-- =============================================================================
CREATE OR REPLACE PACKAGE PKG_IDENTITY AUTHID DEFINER AS

    -- ---------------------------------------------------------------- USUARIO
    -- Número de usuarios con el correo indicado (comparación exacta).
    FUNCTION count_users_by_email(p_email IN VARCHAR2) RETURN NUMBER;

    -- Crea un usuario ACTIVO sin intentos fallidos y devuelve su ID interno.
    PROCEDURE create_user(p_public_id IN VARCHAR2, p_email IN VARCHAR2, p_password_hash IN VARCHAR2,
                          p_must_change_password IN NUMBER, o_user_id OUT NUMBER);

    -- Usuario por correo (0 o 1 fila): ID, PUBLIC_ID, EMAIL, PASSWORD_HASH, ESTADO, INTENTOS_FALLIDOS,
    -- BLOQUEADO_HASTA, CREATED_AT, DEBE_CAMBIAR_PASSWORD, MFA_HABILITADO, MFA_SECRET, MFA_CONFIGURADO_AT.
    PROCEDURE get_user_by_email(p_email IN VARCHAR2, o_user OUT SYS_REFCURSOR);

    -- Usuario por ID interno (mismas columnas que get_user_by_email).
    PROCEDURE get_user_by_id(p_user_id IN NUMBER, o_user OUT SYS_REFCURSOR);

    -- Usuario por identificador público (mismas columnas que get_user_by_email).
    PROCEDURE get_user_by_public_id(p_public_id IN VARCHAR2, o_user OUT SYS_REFCURSOR);

    -- Nombres de los roles del usuario (columna NOMBRE), ordenados alfabéticamente.
    PROCEDURE list_user_roles(p_user_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- Actualiza intentos fallidos, bloqueo temporal y estado del usuario.
    PROCEDURE update_failed_attempts(p_user_id IN NUMBER, p_attempts IN NUMBER,
                                     p_locked_until IN TIMESTAMP WITH TIME ZONE, p_status IN VARCHAR2);

    -- Restablece los intentos fallidos, quita el bloqueo y deja el usuario ACTIVO.
    PROCEDURE reset_failed_attempts(p_user_id IN NUMBER);

    -- ID del rol con el nombre indicado; NULL si no existe.
    FUNCTION get_role_id_by_name(p_name IN VARCHAR2) RETURN NUMBER;

    -- Asigna un rol al usuario.
    PROCEDURE assign_role(p_user_id IN NUMBER, p_role_id IN NUMBER);

    -- Cambia la contraseña, fija la obligación de cambiarla y limpia intentos y bloqueo.
    PROCEDURE update_password(p_user_id IN NUMBER, p_password_hash IN VARCHAR2, p_must_change_password IN NUMBER);

    -- Cambia el estado del usuario.
    PROCEDURE update_status(p_user_id IN NUMBER, p_status IN VARCHAR2);

    -- Guarda el secreto MFA pendiente de confirmación.
    PROCEDURE save_mfa_secret(p_user_id IN NUMBER, p_mfa_secret IN VARCHAR2);

    -- Activa MFA con el secreto indicado.
    PROCEDURE enable_mfa(p_user_id IN NUMBER, p_mfa_secret IN VARCHAR2);

    -- Desactiva MFA y borra el secreto.
    PROCEDURE disable_mfa(p_user_id IN NUMBER);

    -- ---------------------------------------------------------- REFRESH_TOKEN
    -- Crea un refresh token vigente y devuelve su ID interno.
    PROCEDURE create_refresh_token(p_user_id IN NUMBER, p_token_hash IN VARCHAR2,
                                   p_expires_at IN TIMESTAMP WITH TIME ZONE, o_token_id OUT NUMBER);

    -- Refresh token por hash (0 o 1 fila): ID, USUARIO_ID, TOKEN_HASH, EXPIRACION, REVOCADO, CREATED_AT.
    PROCEDURE get_refresh_token_by_hash(p_token_hash IN VARCHAR2, o_token OUT SYS_REFCURSOR);

    -- Revoca un refresh token por ID.
    PROCEDURE revoke_refresh_token(p_token_id IN NUMBER);

    -- Revoca un refresh token por hash.
    PROCEDURE revoke_refresh_token_by_hash(p_token_hash IN VARCHAR2);

    -- Revoca todos los refresh tokens vigentes del usuario.
    PROCEDURE revoke_user_refresh_tokens(p_user_id IN NUMBER);

    -- -------------------------------------------------------- MFA_BACKUP_CODE
    -- Registra un código de respaldo sin usar.
    PROCEDURE add_mfa_backup_code(p_user_id IN NUMBER, p_code_hash IN VARCHAR2);

    -- Elimina todos los códigos de respaldo del usuario.
    PROCEDURE delete_mfa_backup_codes(p_user_id IN NUMBER);

    -- Marca como usado un código disponible; o_updated = filas actualizadas (0 si no estaba disponible).
    PROCEDURE consume_mfa_backup_code(p_user_id IN NUMBER, p_code_hash IN VARCHAR2, o_updated OUT NUMBER);

    -- Número de códigos de respaldo sin usar del usuario.
    FUNCTION count_available_backup_codes(p_user_id IN NUMBER) RETURN NUMBER;

    -- Códigos de respaldo del usuario por ID ascendente: ID, USUARIO_ID, CODE_HASH, USADO, USADO_AT, CREATED_AT.
    PROCEDURE list_mfa_backup_codes(p_user_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- ---------------------------------------------------- CODIGO_VERIFICACION
    -- Crea un código de verificación y devuelve su ID interno.
    PROCEDURE create_verification_code(p_public_id IN VARCHAR2, p_user_id IN NUMBER, p_type IN VARCHAR2,
                                       p_code_hash IN VARCHAR2, p_expires_at IN TIMESTAMP WITH TIME ZONE,
                                       p_failed_attempts IN NUMBER, p_max_attempts IN NUMBER, p_used IN NUMBER,
                                       o_code_id OUT NUMBER);

    -- Código por ID (0 o 1 fila): ID, PUBLIC_ID, USUARIO_ID, TIPO, CODIGO_HASH, FECHA_EXPIRACION,
    -- INTENTOS_FALLIDOS, MAX_INTENTOS, USADO, CREATED_AT.
    PROCEDURE get_verification_code_by_id(p_code_id IN NUMBER, o_code OUT SYS_REFCURSOR);

    -- Último código sin usar del usuario y tipo (0 o 1 fila, mismas columnas).
    PROCEDURE get_latest_pending_code(p_user_id IN NUMBER, p_type IN VARCHAR2, o_code OUT SYS_REFCURSOR);

    -- Suma un intento fallido al código.
    PROCEDURE increment_code_attempts(p_code_id IN NUMBER);

    -- Marca el código como usado.
    PROCEDURE mark_code_used(p_code_id IN NUMBER);

    -- Marca como usados todos los códigos pendientes del usuario y tipo.
    PROCEDURE invalidate_pending_codes(p_user_id IN NUMBER, p_type IN VARCHAR2);
END PKG_IDENTITY;
/
