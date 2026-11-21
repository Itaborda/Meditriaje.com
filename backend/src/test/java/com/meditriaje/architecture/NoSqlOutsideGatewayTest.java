package com.meditriaje.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regla del profesor: ninguna sentencia SQL en Java; toda llamada a la base pasa por
 * {@code com.meditriaje.repository.plsql.PackageGateway}.
 *
 * <p>Falla si una clase fuera de {@code repository/plsql} usa JdbcTemplate, NamedParameterJdbcTemplate,
 * SimpleJdbcCall o JdbcClient, o contiene texto SQL (literal o bloque de texto que empiece por SELECT, INSERT,
 * UPDATE, DELETE, MERGE o WITH). Los repositorios heredados que aún tienen SQL están en una lista congelada que
 * solo puede reducirse: al migrar un repositorio a paquetes PL/SQL hay que quitarlo de {@link #LEGACY_ALLOWLIST}
 * (la prueba falla si sigue en la lista sin infringir la regla). La meta es una lista vacía.
 */
class NoSqlOutsideGatewayTest {

    private static final Path SOURCES = Path.of("src", "main", "java");
    private static final String GATEWAY_PACKAGE = "com/meditriaje/repository/plsql/";

    private static final Pattern JDBC_API =
            Pattern.compile("\\b(JdbcTemplate|NamedParameterJdbcTemplate|SimpleJdbcCall|JdbcClient)\\b");
    private static final Pattern SQL_LITERAL =
            Pattern.compile("\"\\s*(SELECT|INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|MERGE\\s+INTO)\\s");
    private static final Pattern SQL_TEXT_BLOCK =
            Pattern.compile("\"\"\"\\s*\\R\\s*(SELECT|INSERT|UPDATE|DELETE|MERGE|WITH)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("(?m)^\\s*//.*$");

    /** Lista congelada de clases heredadas con SQL. SOLO SE PUEDE REDUCIR. */
    static final Set<String> LEGACY_ALLOWLIST = Set.of(
            "com/meditriaje/demo/DemoDataSeeder.java",
            "com/meditriaje/repository/AccesoTemporalQrRepository.java",
            "com/meditriaje/repository/AfiliacionPacienteRepository.java",
            "com/meditriaje/repository/AlergiaRepository.java",
            "com/meditriaje/repository/AlertaOperativaRepository.java",
            "com/meditriaje/repository/AsignacionAsistencialRepository.java",
            "com/meditriaje/repository/AtencionRepository.java",
            "com/meditriaje/repository/AuditoriaRepository.java",
            "com/meditriaje/repository/BreakGlassRepository.java",
            "com/meditriaje/repository/CodigoVerificacionRepository.java",
            "com/meditriaje/repository/ConsentimientoRepository.java",
            "com/meditriaje/repository/DiagnosticoCie10Repository.java",
            "com/meditriaje/repository/DispensacionRepository.java",
            "com/meditriaje/repository/EntidadEpsRepository.java",
            "com/meditriaje/repository/EspecialidadRepository.java",
            "com/meditriaje/repository/IdentidadProvisionalRepository.java",
            "com/meditriaje/repository/LoteImportacionEpsRepository.java",
            "com/meditriaje/repository/MedicamentoRepository.java",
            "com/meditriaje/repository/MfaBackupCodeRepository.java",
            "com/meditriaje/repository/PacienteRepository.java",
            "com/meditriaje/repository/ProfesionalRepository.java",
            "com/meditriaje/repository/RecetaRepository.java",
            "com/meditriaje/repository/RefreshTokenRepository.java",
            "com/meditriaje/repository/ReporteRepository.java",
            "com/meditriaje/repository/RepresentacionLegalRepository.java",
            "com/meditriaje/repository/SeguimientoQrRepository.java",
            "com/meditriaje/repository/SeguimientoRepository.java",
            "com/meditriaje/repository/UsuarioRepository.java"
    );

    @Test
    @DisplayName("solo la pasarela PL/SQL (y la lista heredada congelada) accede a la base con JDBC o SQL")
    void noSqlOutsideTheGateway() {
        Set<String> violations = findViolations();

        Set<String> newViolations = new TreeSet<>(violations);
        newViolations.removeAll(LEGACY_ALLOWLIST);
        assertThat(newViolations)
                .as("Clases nuevas con SQL o JDBC directo: muevan el SQL a un paquete PKG_<MODULO> y llamen a PackageGateway")
                .isEmpty();
    }

    @Test
    @DisplayName("la lista heredada solo se reduce: un repositorio ya migrado debe salir de ella")
    void allowlistOnlyShrinks() {
        Set<String> violations = findViolations();

        Set<String> alreadyMigrated = new TreeSet<>(LEGACY_ALLOWLIST);
        alreadyMigrated.removeAll(violations);
        assertThat(alreadyMigrated)
                .as("Estas clases ya no tienen SQL: quítenlas de LEGACY_ALLOWLIST")
                .isEmpty();
    }

    @Test
    @DisplayName("la detección reconoce JDBC directo, literales SQL y bloques de texto, e ignora comentarios")
    void detectorRecognisesPlantedViolations() {
        assertThat(violates("class A { JdbcTemplate t; }")).isTrue();
        assertThat(violates("class A { SimpleJdbcCall c; }")).isTrue();
        assertThat(violates("class A { String s = \"SELECT ID FROM SEDE\"; }")).isTrue();
        assertThat(violates("class A { String s = \"\"\"\n    UPDATE SEDE SET X = 1\n    \"\"\"; }")).isTrue();
        assertThat(violates("class A { /* usa JdbcTemplate */ String s = \"Seleccione una opcion\"; }")).isFalse();
        assertThat(violates("class A {\n  // SELECT ID FROM SEDE\n}")).isFalse();
    }

    static Set<String> findViolations() {
        Set<String> result = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String relative = SOURCES.relativize(path).toString().replace('\\', '/');
                if (!relative.startsWith(GATEWAY_PACKAGE) && violates(read(path))) {
                    result.add(relative);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    static boolean violates(String source) {
        String code = LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(source).replaceAll(" ")).replaceAll(" ");
        return JDBC_API.matcher(code).find()
                || SQL_LITERAL.matcher(code).find()
                || SQL_TEXT_BLOCK.matcher(code).find();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
