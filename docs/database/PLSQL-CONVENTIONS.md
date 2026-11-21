# Convenciones PL/SQL y acceso a datos

Este documento fija cómo MediTriaje accede a Oracle desde la corrección del profesor:

1. **No hay SQL en Java.** Todo acceso a datos se hace mediante paquetes PL/SQL (`PKG_<MODULO>`).
2. **Una sola clase llama a la base de datos:** `com.meditriaje.repository.plsql.PackageGateway`. Es la
   "clase que llama a todas las funciones de la base". Ninguna otra clase usa `JdbcTemplate`,
   `NamedParameterJdbcTemplate`, `SimpleJdbcCall` ni `JdbcClient`.
3. **Cada paquete tiene más de un procedimiento o función** (mínimo dos).
4. **Hospital único:** no hay sedes múltiples. La única sede la resuelve la base (`PKG_COMMON`).

Los nombres de tablas, columnas, restricciones y triggers siguen en **español**. Los nombres de paquetes,
procedimientos, funciones y parámetros PL/SQL, y las clases y métodos Java nuevos, van en **inglés**. Los
comentarios (Java, SQL, JS) se escriben en español.

---

## 1. Capas

```
Controller  ->  Service (@Transactional)  ->  Repository (sin SQL)  ->  PackageGateway  ->  PKG_<MODULO> (Oracle)
```

| Capa | Responsabilidad | Prohibido |
|---|---|---|
| Servicio | Reglas de negocio, transacción (`@Transactional`), auditoría | Acceso a JDBC |
| Repositorio | Declara las rutinas del paquete (`ProcedureSpec`), arma parámetros, mapea filas (`RowMapper`) | SQL, `JdbcTemplate` |
| `PackageGateway` | Ejecuta la llamada, cachea la llamada compilada, traduce errores | Lógica de negocio |
| Paquete PL/SQL | SQL, validaciones de datos, errores de aplicación | `COMMIT`, `ROLLBACK`, SQL dinámico con texto del usuario |

Las clases `PackageGateway`, `ProcedureSpec` y `OracleErrorTranslator` viven en
`backend/src/main/java/com/meditriaje/repository/plsql/`.

---

## 2. Archivos de migración

Los paquetes son migraciones **repetibles** de Flyway en `database/migrations/`:

| Archivo | Contenido |
|---|---|
| `R__pkg_1_spec_<modulo>.sql` | Especificación: `CREATE OR REPLACE PACKAGE PKG_<MODULO> AUTHID DEFINER AS ... END;` + `/` |
| `R__pkg_2_body_<modulo>.sql` | Cuerpo: `CREATE OR REPLACE PACKAGE BODY PKG_<MODULO> AS ... END;` + `/` |
| `R__zz_recompile_and_grants.sql` | Ya existe. Recompila inválidos, concede `EXECUTE` sobre **todos** los paquetes a `MEDITRIAJE_APP` y falla si queda un paquete inválido. **No hay que editarlo.** |

- `<modulo>` en minúsculas y en inglés (`common`, `patients`, `pharmacy`, `emergency`...). El paquete es
  `PKG_` + el módulo en mayúsculas (`R__pkg_1_spec_pharmacy.sql` declara `PKG_PHARMACY`).
- Flyway ordena las repetibles por nombre: primero todas las especificaciones (`R__pkg_1_*`), luego todos los
  cuerpos (`R__pkg_2_*`) y al final `R__zz_*`. Así un cuerpo puede llamar a otro paquete sin importar el orden.
- Los cambios de **estructura** (tablas, columnas, índices, triggers) siguen yendo en migraciones versionadas
  `V0NN__descripcion.sql`. **Nunca se editan V001-V025** (sus sumas de control ya están aplicadas en ATP).
- Compatibilidad **Oracle 19c** (ATP): sin tipo `BOOLEAN` en SQL, sin sintaxis exclusiva de 23ai.

---

## 3. Reglas de los paquetes

1. **Al menos dos rutinas** (`PROCEDURE`/`FUNCTION`) por especificación. Lo verifica
   `PlsqlPackageConventionTest` (sin base de datos) y `PackageGatewayIT` (en Oracle real).
2. **Nombres en inglés**, `snake_case`: `get_patient`, `list_prescriptions`, `register_dispensation`.
3. **Parámetros:** entrada con prefijo `p_`, salida con prefijo `o_` (`p_patient_public_id`, `o_patient_id`,
   `o_rows`). Sin parámetros `IN OUT` salvo necesidad justificada.
4. **Sin `COMMIT` ni `ROLLBACK`.** La transacción la controla Java (`@Transactional` en el servicio): el
   `PackageGateway` usa la misma conexión que la transacción de Spring. Lo verifica `PlsqlPackageConventionTest`.
5. **Listados devuelven `SYS_REFCURSOR`** como parámetro `OUT` (`o_rows`, `o_patient`...). Una búsqueda de un
   solo registro también devuelve un cursor (0 o 1 fila); el repositorio usa `gateway.single(...)`.
6. **Valores escalares** se devuelven con parámetros `OUT` o con una `FUNCTION`.
7. **Booleanos:** no se usa `BOOLEAN` en la firma (JDBC no lo enlaza en 19c). Se usa `NUMBER(1)` (0/1) o
   `VARCHAR2(1)` (`'S'`/`'N'`).
8. **Errores de aplicación** siempre con `PKG_COMMON.raise_error(código, mensaje)`. El mensaje es un texto en
   español apto para el usuario final, porque la API lo devuelve tal cual:

| Rango | Constante en `PKG_COMMON` | Significado | Excepción Java | HTTP |
|---|---|---|---|---|
| -20000..-20099 | `c_err_integrity` | Integridad / inmutabilidad | `ConflictoOperacionException` | 409 |
| -20100..-20199 | `c_err_not_found` | No encontrado | `RecursoNoEncontradoException` | 404 |
| -20200..-20299 | `c_err_conflict` | Conflicto / transición de estado inválida | `ConflictoOperacionException` | 409 |
| -20300..-20399 | `c_err_business_rule` | Regla de negocio / validación | `DatosInvalidosException` | 400 |
| -20400..-20499 | `c_err_access_denied` | Acceso denegado | `AccesoNoAutorizadoException` | 403 |
| -20500..-20599 | `c_err_catalog` | Catálogo / importación | `DatosInvalidosException` | 400 |
| ORA-00001 | — | Clave única duplicada | `ConflictoOperacionException` | 409 |
| -20600..-20999 y el resto | `c_err_internal` (-20999) | Error interno o de migración | (sin traducir) | 500 genérico |

   `OracleErrorTranslator` hace esta traducción y `GlobalExceptionHandler` responde con el mismo `ApiError`
   (`codigo`, `mensaje`) de siempre. Los códigos -20990..-20999 están reservados a las aserciones de migración.
   Los triggers existentes (por ejemplo -20030/-20031 de dispensación, -20060 de sede única) caen en el rango de
   integridad y se traducen a 409.

### `PKG_COMMON` (ya disponible)

| Rutina | Uso |
|---|---|
| `PROCEDURE raise_error(p_code IN NUMBER, p_message IN VARCHAR2)` | Lanza el error de aplicación (ver tabla). |
| `FUNCTION get_single_site_id RETURN NUMBER` | ID interno de la única sede (para columnas `SEDE_ID`). |
| `PROCEDURE get_single_site(o_site OUT SYS_REFCURSOR)` | Fila de la única sede (`ID`, `INSTITUCION_ID`, `PUBLIC_ID`, `NOMBRE`, `DIRECCION`, `CIUDAD`, `ESTADO`). |
| `FUNCTION new_public_id RETURN VARCHAR2` | UUID nuevo para `PUBLIC_ID`. |

Las columnas `SEDE_ID` siguen existiendo por ahora: un paquete que inserta en una tabla con `SEDE_ID` usa
`PKG_COMMON.get_single_site_id` y **no recibe la sede como parámetro**. Una fase posterior eliminará esas
columnas cuando todo el SQL esté en paquetes.

### Ejemplo de paquete

```sql
-- R__pkg_1_spec_example.sql
CREATE OR REPLACE PACKAGE PKG_EXAMPLE AUTHID DEFINER AS
    -- Lista las alergias activas de un paciente.
    PROCEDURE list_allergies(p_patient_public_id IN VARCHAR2, o_rows OUT SYS_REFCURSOR);
    -- Registra una alergia y devuelve su identificador público.
    PROCEDURE create_allergy(p_patient_public_id IN VARCHAR2, p_substance IN VARCHAR2,
                             o_public_id OUT VARCHAR2);
END PKG_EXAMPLE;
/
```

```sql
-- R__pkg_2_body_example.sql
CREATE OR REPLACE PACKAGE BODY PKG_EXAMPLE AS
    FUNCTION patient_id(p_public_id IN VARCHAR2) RETURN NUMBER IS
        v_id PACIENTE.ID%TYPE;
    BEGIN
        SELECT ID INTO v_id FROM PACIENTE WHERE PUBLIC_ID = p_public_id;
        RETURN v_id;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            PKG_COMMON.raise_error(PKG_COMMON.c_err_not_found, 'Paciente no encontrado.');
    END patient_id;

    PROCEDURE list_allergies(p_patient_public_id IN VARCHAR2, o_rows OUT SYS_REFCURSOR) IS
        v_patient_id NUMBER := patient_id(p_patient_public_id);
    BEGIN
        OPEN o_rows FOR
            SELECT PUBLIC_ID, SUSTANCIA, ESTADO FROM ALERGIA
             WHERE PACIENTE_ID = v_patient_id ORDER BY ID;
    END list_allergies;

    PROCEDURE create_allergy(p_patient_public_id IN VARCHAR2, p_substance IN VARCHAR2,
                             o_public_id OUT VARCHAR2) IS
    BEGIN
        o_public_id := PKG_COMMON.new_public_id;
        INSERT INTO ALERGIA (PUBLIC_ID, PACIENTE_ID, SUSTANCIA)
        VALUES (o_public_id, patient_id(p_patient_public_id), p_substance);
    END create_allergy;
END PKG_EXAMPLE;
/
```

(Ejemplo ilustrativo: las columnas reales se toman de las migraciones V001-V025.)

---

## 4. Cómo un repositorio llama a la pasarela

API de `PackageGateway` (todas traducen los errores ORA-20xxx):

| Método | Devuelve |
|---|---|
| `Map<String,Object> execute(ProcedureSpec spec, SqlParameterSource input)` | Todos los parámetros `OUT` (cursores ya mapeados a `List`). |
| `Map<String,Object> execute(ProcedureSpec spec)` | Igual, sin parámetros de entrada. |
| `<T> T callFunction(ProcedureSpec spec, SqlParameterSource input)` / `callFunction(spec)` | Valor de retorno de una función. |
| `<T> List<T> list(ProcedureSpec spec, SqlParameterSource input, String cursorParam)` | Filas del cursor indicado. |
| `<T> Optional<T> single(ProcedureSpec spec, SqlParameterSource input, String cursorParam)` | 0 o 1 fila; más de una es error. |

`ProcedureSpec` declara la rutina **una sola vez** como constante estática del repositorio:

- `ProcedureSpec.procedure("PKG_X", "name")` o `ProcedureSpec.function("PKG_X", "name", "result", Types.NUMERIC)`.
- `.in("p_...", Types.X)`, `.out("o_...", Types.X)`, `.cursor("o_...", rowMapper)`, `.build()`.
- **El orden de declaración debe ser exactamente el de la firma PL/SQL**: no se leen metadatos del diccionario
  (el usuario de la aplicación no es dueño de los objetos), los parámetros se enlazan por posición.
- Tipos JDBC habituales: `VARCHAR2` → `Types.VARCHAR`; `NUMBER` → `Types.NUMERIC`; `DATE` → `Types.DATE`;
  `TIMESTAMP WITH TIME ZONE` → `Types.TIMESTAMP` (enviar `java.sql.Timestamp.from(instant)`); `CLOB` → `Types.CLOB`.
- Los `NUMBER` de salida llegan como `BigDecimal`: convertir con `((Number) valor).longValue()`.

Modelo real: `SiteRepository` (usa `PKG_COMMON`). Ejemplo para un repositorio migrado:

```java
@Repository
public class AllergyRepository {

    private static final String PACKAGE = "PKG_EXAMPLE";

    // Mapea cada fila del cursor (nombres de columna de Oracle, en español).
    private static final RowMapper<AlergiaResponse> ROW = (rs, n) -> new AlergiaResponse(
            rs.getString("PUBLIC_ID"), rs.getString("SUSTANCIA"), rs.getString("ESTADO"));

    static final ProcedureSpec LIST_ALLERGIES = ProcedureSpec.procedure(PACKAGE, "list_allergies")
            .in("p_patient_public_id", Types.VARCHAR)
            .cursor("o_rows", ROW)
            .build();

    static final ProcedureSpec CREATE_ALLERGY = ProcedureSpec.procedure(PACKAGE, "create_allergy")
            .in("p_patient_public_id", Types.VARCHAR)
            .in("p_substance", Types.VARCHAR)
            .out("o_public_id", Types.VARCHAR)
            .build();

    private final PackageGateway gateway;

    public AllergyRepository(PackageGateway gateway) {
        this.gateway = gateway;
    }

    public List<AlergiaResponse> listAllergies(String patientPublicId) {
        return gateway.list(LIST_ALLERGIES,
                new MapSqlParameterSource("p_patient_public_id", patientPublicId), "o_rows");
    }

    public String createAllergy(String patientPublicId, String substance) {
        Map<String, Object> out = gateway.execute(CREATE_ALLERGY, new MapSqlParameterSource()
                .addValue("p_patient_public_id", patientPublicId)
                .addValue("p_substance", substance));
        return (String) out.get("o_public_id");
    }
}
```

El servicio sigue igual: anota `@Transactional` y llama al repositorio. Si el paquete lanza
`PKG_COMMON.raise_error(-20100, 'Paciente no encontrado.')`, el cliente recibe 404 con
`{"codigo":"RECURSO_NO_ENCONTRADO", "mensaje":"El recurso solicitado no fue encontrado: Paciente no encontrado."}`.

### Configuración

- Esquema propietario: propiedad `meditriaje.db.owner-schema` (variable opcional `DB_OWNER_SCHEMA`), por defecto
  `MEDITRIAJE_OWNER`. No hay variables de entorno nuevas obligatorias.
- Flyway corre al arrancar como propietario (`FLYWAY_USER`) y aplica versionadas y repetibles; las sesiones de la
  aplicación usan `CURRENT_SCHEMA = MEDITRIAJE_OWNER` y el `GRANT EXECUTE` de `R__zz_recompile_and_grants.sql`.

---

## 5. Guardas automáticas (la fase siguiente debe mantenerlas en verde)

| Prueba | Qué verifica |
|---|---|
| `NoSqlOutsideGatewayTest` | Ninguna clase fuera de `repository/plsql` usa JDBC directo ni contiene texto SQL. Los repositorios heredados están en `LEGACY_ALLOWLIST`, que **solo puede reducirse**: al migrar un repositorio hay que quitarlo de la lista (la prueba falla si sigue en la lista sin SQL). Meta: lista vacía. |
| `PlsqlPackageConventionTest` | Cada `R__pkg_1_spec_<modulo>.sql` declara `PKG_<MODULO>` con al menos dos rutinas, tiene su cuerpo, y ningún cuerpo hace `COMMIT`/`ROLLBACK`. |
| `PackageGatewayIT` | Contra Oracle Free real: función, cursor, `IN`/`OUT`, traducción de errores, `GRANT EXECUTE` sobre todos los paquetes, cero objetos inválidos, todos los paquetes con más de una rutina. |

---

## 6. Prueba de integración de un paquete (Oracle IT)

Cada paquete nuevo lleva su IT con Testcontainers (Docker Desktop encendido). Plantilla
(ver `PackageGatewayIT` y `backend/src/test/java/com/meditriaje/db/OracleTestDatabase.java`):

```java
@Tag("integration")                              // la ejecuta failsafe en `mvn verify`
@Testcontainers(disabledWithoutDocker = true)
class PkgExampleIT {

    private static OracleContainer oracle;
    private static PackageGateway gateway;
    private static JdbcTemplate owner;           // solo para preparar datos en la prueba

    @BeforeAll
    static void migrate() {
        oracle = OracleTestDatabase.newContainer();                       // gvenzl/oracle-free:23-slim-faststart
        oracle.start();
        DataSource ownerDs = OracleTestDatabase.provisionOwner(oracle, "MT_PKG_EXAMPLE"); // esquema propio
        OracleTestDatabase.flyway(ownerDs).migrate();                     // V001.. + repetibles
        owner = new JdbcTemplate(ownerDs);
        DataSource appDs = OracleTestDatabase.dataSource(
                oracle, OracleTestDatabase.APP_USER, OracleTestDatabase.APP_PASSWORD);
        gateway = new PackageGateway(appDs, "MT_PKG_EXAMPLE", new OracleErrorTranslator()); // como en producción
    }

    @AfterAll
    static void stop() { if (oracle != null) oracle.stop(); }

    @Test
    void unknownPatientIsNotFound() {
        assertThatThrownBy(() -> gateway.list(AllergyRepository.LIST_ALLERGIES,
                        new MapSqlParameterSource("p_patient_public_id", "no-existe"), "o_rows"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
```

- La prueba llama al paquete con el **usuario de aplicación** (`MEDITRIAJE_APP`), igual que en producción, por
  lo que también comprueba los permisos.
- Cubrir por rutina: camino feliz, cada error de aplicación que lance y el contenido del cursor.
- Las pruebas unitarias de servicios siguen usando Mockito sobre el repositorio.

Comando (prefijo obligatorio en Windows con JDK 21):

```bash
export JAVA_HOME="$HOME/.jdks/ms-21.0.11"; export PATH="$JAVA_HOME/bin:$PATH"
mvn -f backend/pom.xml verify                                   # unitarias + IT
mvn -f backend/pom.xml verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false \
    -Dit.test=PkgExampleIT -Dfailsafe.failIfNoSpecifiedTests=false   # solo una IT
```

---

## 7. Lista de verificación al migrar un repositorio

1. Crear `R__pkg_1_spec_<modulo>.sql` y `R__pkg_2_body_<modulo>.sql` (≥ 2 rutinas, nombres en inglés,
   `p_`/`o_`, cursores para listas, sin `COMMIT`).
2. Reescribir el repositorio: constantes `ProcedureSpec` + llamadas a `PackageGateway`; sin `JdbcTemplate` ni SQL.
3. Quitar el repositorio de `NoSqlOutsideGatewayTest.LEGACY_ALLOWLIST`.
4. Escribir `Pkg<Modulo>IT` y actualizar las pruebas unitarias del repositorio/servicio.
5. `mvn -f backend/pom.xml verify` en verde (con las IT ejecutándose, no omitidas).
