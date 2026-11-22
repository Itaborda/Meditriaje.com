package com.meditriaje.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Convenciones de los paquetes PL/SQL (docs/database/PLSQL-CONVENTIONS.md), verificadas sobre los archivos de
 * migración sin base de datos:
 *
 * <ul>
 *   <li>cada {@code R__pkg_1_spec_<modulo>.sql} declara el paquete {@code PKG_<MODULO>} con MÁS DE UN
 *       procedimiento o función (requisito del profesor);
 *   <li>cada especificación tiene su cuerpo {@code R__pkg_2_body_<modulo>.sql};
 *   <li>ningún cuerpo ejecuta COMMIT ni ROLLBACK (la transacción es de Java).
 * </ul>
 */
class PlsqlPackageConventionTest {

    private static final Path MIGRATIONS = Path.of("..", "database", "migrations");
    private static final Pattern SPEC_FILE = Pattern.compile("R__pkg_1_spec_([a-z0-9_]+)\\.sql");
    private static final Pattern SUBPROGRAM = Pattern.compile("(?im)^\\s*(PROCEDURE|FUNCTION)\\s+[A-Za-z][A-Za-z0-9_]*");
    private static final Pattern PACKAGE_NAME =
            Pattern.compile("(?i)CREATE\\s+OR\\s+REPLACE\\s+PACKAGE\\s+([A-Za-z0-9_]+)");
    private static final Pattern TRANSACTION_CONTROL = Pattern.compile("(?i)\\b(COMMIT|ROLLBACK)\\s*;");
    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\r\\n]*");

    @Test
    @DisplayName("existe al menos un paquete (PKG_COMMON)")
    void atLeastOnePackageExists() {
        assertThat(specFiles()).extracting(path -> path.getFileName().toString()).contains("R__pkg_1_spec_common.sql");
    }

    @Test
    @DisplayName("cada especificación declara PKG_<MODULO> con más de un procedimiento o función")
    void everySpecDeclaresMoreThanOneSubprogram() {
        for (Path spec : specFiles()) {
            String module = module(spec);
            String code = withoutComments(read(spec));

            Matcher name = PACKAGE_NAME.matcher(code);
            assertThat(name.find()).as("%s no declara un paquete", spec.getFileName()).isTrue();
            assertThat(name.group(1).toUpperCase())
                    .as("%s debe declarar PKG_%s", spec.getFileName(), module.toUpperCase())
                    .isEqualTo("PKG_" + module.toUpperCase());

            long subprograms = SUBPROGRAM.matcher(code).results().count();
            assertThat(subprograms)
                    .as("%s debe declarar más de un procedimiento o función", spec.getFileName())
                    .isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("cada especificación tiene su cuerpo y ningún cuerpo hace COMMIT o ROLLBACK")
    void everySpecHasABodyWithoutTransactionControl() {
        for (Path spec : specFiles()) {
            Path body = MIGRATIONS.resolve("R__pkg_2_body_" + module(spec) + ".sql");
            assertThat(body).as("falta el cuerpo de %s", spec.getFileName()).exists();
            assertThat(TRANSACTION_CONTROL.matcher(withoutComments(read(body))).find())
                    .as("%s no debe ejecutar COMMIT ni ROLLBACK", body.getFileName())
                    .isFalse();
        }
    }

    private static List<Path> specFiles() {
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            return files.filter(path -> SPEC_FILE.matcher(path.getFileName().toString()).matches())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String module(Path spec) {
        Matcher matcher = SPEC_FILE.matcher(spec.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(spec.toString());
        }
        return matcher.group(1);
    }

    private static String withoutComments(String sql) {
        return LINE_COMMENT.matcher(sql).replaceAll("");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
