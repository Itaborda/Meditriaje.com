-- =============================================================================
-- MediTriaje - R__pkg_1_spec_hospital: especificación de PKG_HOSPITAL
-- Repetible. Gestión hospitalaria: áreas, habitaciones/salas, camas, ocupación de camas, movimientos
-- intrahospitalarios, egresos y procedimientos hospitalarios (tablas de V019).
-- Derechos del definidor; nunca ejecuta COMMIT ni ROLLBACK (la transacción es de Java).
-- Las consultas de un solo registro devuelven un cursor con 0 o 1 fila.
-- =============================================================================
CREATE OR REPLACE PACKAGE PKG_HOSPITAL AUTHID DEFINER AS

    -- ---------------------------------------------------------------- Áreas
    -- Registra un área en la única sede del hospital y devuelve su ID interno.
    PROCEDURE create_area(p_public_id IN VARCHAR2, p_code IN VARCHAR2, p_name IN VARCHAR2,
                          p_type IN VARCHAR2, p_floor IN VARCHAR2, p_status IN VARCHAR2,
                          o_id OUT NUMBER);
    -- Área por ID interno (todas las columnas de AREA_HOSPITALARIA).
    PROCEDURE get_area_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    -- Área por identificador público.
    PROCEDURE get_area_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);
    -- Áreas de una sede ordenadas por código.
    PROCEDURE list_areas_by_site(p_site_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- ---------------------------------------------------------------- Habitaciones y salas
    -- Registra una habitación o sala y devuelve su ID interno.
    PROCEDURE create_room(p_public_id IN VARCHAR2, p_area_id IN NUMBER, p_code IN VARCHAR2,
                          p_type IN VARCHAR2, p_status IN VARCHAR2, o_id OUT NUMBER);
    -- Habitación por ID interno.
    PROCEDURE get_room_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    -- Habitación por identificador público.
    PROCEDURE get_room_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);
    -- Habitaciones de un área ordenadas por código.
    PROCEDURE list_rooms_by_area(p_area_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- ---------------------------------------------------------------- Camas
    -- Registra una cama y devuelve su ID interno.
    PROCEDURE create_bed(p_public_id IN VARCHAR2, p_room_id IN NUMBER, p_code IN VARCHAR2,
                         p_status IN VARCHAR2, o_id OUT NUMBER);
    -- Cama por ID interno.
    PROCEDURE get_bed_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    -- Cama por identificador público.
    PROCEDURE get_bed_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);
    -- Cambia el estado de una cama y su marca de actualización.
    PROCEDURE update_bed_status(p_bed_id IN NUMBER, p_status IN VARCHAR2);
    -- Reserva atómica: pasa la cama de DISPONIBLE a OCUPADA. o_reserved = 1 si se reservó, 0 si no estaba
    -- disponible.
    PROCEDURE reserve_bed_if_available(p_bed_id IN NUMBER, o_reserved OUT NUMBER);
    -- Censo de camas de una sede con su ocupación activa y el paciente (o identidad provisional NN).
    PROCEDURE list_bed_details_by_site(p_site_id IN NUMBER, o_rows OUT SYS_REFCURSOR);
    -- Detalle de una cama por identificador público.
    PROCEDURE get_bed_detail_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);

    -- ---------------------------------------------------------------- Ocupación de camas
    -- Registra una ocupación. Si un índice único la rechaza (cama o episodio con ocupación activa),
    -- no lanza error: o_id queda en NULL para que la capa Java aplique su compensación.
    PROCEDURE create_occupancy(p_public_id IN VARCHAR2, p_episode_id IN NUMBER, p_bed_id IN NUMBER,
                               p_start_at IN TIMESTAMP WITH TIME ZONE, p_status IN VARCHAR2,
                               p_assigned_by_user_id IN NUMBER, p_reason IN VARCHAR2, o_id OUT NUMBER);
    -- Ocupación por ID interno.
    PROCEDURE get_occupancy_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    -- Ocupación ACTIVA de una cama.
    PROCEDURE get_active_occupancy_by_bed(p_bed_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    -- Ocupación ACTIVA de un episodio.
    PROCEDURE get_active_occupancy_by_episode(p_episode_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    -- Finaliza una ocupación con la fecha de fin indicada.
    PROCEDURE finish_occupancy(p_occupancy_id IN NUMBER, p_end_at IN TIMESTAMP WITH TIME ZONE);

    -- ---------------------------------------------------------------- Movimientos
    -- Registra un movimiento intrahospitalario (append-only) y devuelve su ID interno.
    PROCEDURE create_movement(p_public_id IN VARCHAR2, p_episode_id IN NUMBER, p_origin_area_id IN NUMBER,
                              p_target_area_id IN NUMBER, p_origin_bed_id IN NUMBER,
                              p_target_bed_id IN NUMBER, p_moved_at IN TIMESTAMP WITH TIME ZONE,
                              p_reason IN VARCHAR2, p_registered_by_user_id IN NUMBER, o_id OUT NUMBER);
    -- Movimiento por ID interno.
    PROCEDURE get_movement_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    -- Movimientos de un episodio en orden cronológico, con nombres de áreas, códigos de camas y usuario.
    PROCEDURE list_movements_by_episode(p_episode_id IN NUMBER, o_rows OUT SYS_REFCURSOR);

    -- ---------------------------------------------------------------- Egresos
    -- Registra el egreso hospitalario de un episodio y devuelve su ID interno.
    PROCEDURE create_discharge(p_public_id IN VARCHAR2, p_episode_id IN NUMBER, p_doctor_id IN NUMBER,
                               p_discharged_at IN TIMESTAMP WITH TIME ZONE, p_destination_type IN VARCHAR2,
                               p_diagnosis IN VARCHAR2, p_epicrisis IN VARCHAR2, p_care_plan IN VARCHAR2,
                               o_id OUT NUMBER);
    -- Egreso por ID interno.
    PROCEDURE get_discharge_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    -- Egreso de un episodio.
    PROCEDURE get_discharge_by_episode(p_episode_id IN NUMBER, o_row OUT SYS_REFCURSOR);

    -- ---------------------------------------------------------------- Procedimientos
    -- Registra un procedimiento hospitalario y devuelve su ID interno.
    PROCEDURE create_procedure(p_public_id IN VARCHAR2, p_episode_id IN NUMBER, p_procedure_type IN VARCHAR2,
                               p_description IN VARCHAR2, p_professional_id IN NUMBER, p_room_id IN NUMBER,
                               p_status IN VARCHAR2, p_start_at IN TIMESTAMP WITH TIME ZONE,
                               p_observations IN VARCHAR2, o_id OUT NUMBER);
    -- Procedimiento por ID interno.
    PROCEDURE get_procedure_by_id(p_id IN NUMBER, o_row OUT SYS_REFCURSOR);
    -- Procedimiento por identificador público.
    PROCEDURE get_procedure_by_public_id(p_public_id IN VARCHAR2, o_row OUT SYS_REFCURSOR);
    -- Cambia estado y fecha de fin; las observaciones solo se reemplazan si llegan informadas.
    PROCEDURE update_procedure_status(p_id IN NUMBER, p_status IN VARCHAR2,
                                      p_end_at IN TIMESTAMP WITH TIME ZONE, p_observations IN VARCHAR2);
    -- Procedimientos de un episodio (más recientes primero) con profesional y sala.
    PROCEDURE list_procedures_by_episode(p_episode_id IN NUMBER, o_rows OUT SYS_REFCURSOR);
END PKG_HOSPITAL;
/
