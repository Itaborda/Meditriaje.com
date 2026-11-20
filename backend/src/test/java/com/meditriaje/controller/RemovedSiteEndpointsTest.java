package com.meditriaje.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hospital único: la API ya no expone administración ni selección de sedes o instituciones. El servidor resuelve la
 * única sede, así que ninguna ruta ni parámetro de controlador puede referirse a una sede.
 *
 * <p>Se recorren por reflexión todos los controladores REST; la prueba de rutas que siguen mapeadas evita que el
 * barrido pase vacío por error.</p>
 */
class RemovedSiteEndpointsTest {

    private record Route(String path, Method method) {}

    private static List<Route> allRoutes() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Route> routes = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.meditriaje.controller")) {
            Class<?> controller = Class.forName(candidate.getBeanClassName());
            RequestMapping onClass = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String[] bases = onClass != null && onClass.path().length > 0 ? onClass.path() : new String[] {""};
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping onMethod = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (onMethod == null) {
                    continue;
                }
                String[] suffixes = onMethod.path().length > 0 ? onMethod.path() : new String[] {""};
                for (String base : bases) {
                    for (String suffix : suffixes) {
                        routes.add(new Route(base + suffix, method));
                    }
                }
            }
        }
        return routes;
    }

    @Test
    @DisplayName("ninguna ruta administra o lista sedes o instituciones (responden 404)")
    void noSiteOrInstitutionRoutes() throws ClassNotFoundException {
        assertThat(allRoutes())
                .extracting(Route::path)
                .noneMatch(path -> path.contains("/sites") || path.contains("/institutions"));
    }

    @Test
    @DisplayName("ninguna ruta lleva la sede como variable de ruta o parámetro de consulta")
    void noSiteParameters() throws ClassNotFoundException {
        List<String> siteParameters = new ArrayList<>();
        for (Route route : allRoutes()) {
            assertThat(route.path().toLowerCase()).doesNotContain("{sede").doesNotContain("{site");
            for (Parameter parameter : route.method().getParameters()) {
                boolean bound = parameter.isAnnotationPresent(RequestParam.class)
                        || parameter.isAnnotationPresent(PathVariable.class);
                String name = parameter.getName().toLowerCase();
                if (bound && (name.contains("sede") || name.contains("site"))) {
                    siteParameters.add(route.path() + " -> " + parameter.getName());
                }
            }
        }
        assertThat(siteParameters).isEmpty();
    }

    @Test
    @DisplayName("las rutas que antes recibían la sede siguen mapeadas sin ella")
    void formerSiteRoutesAreStillMapped() throws ClassNotFoundException {
        List<String> paths = allRoutes().stream().map(Route::path).toList();
        assertThat(paths)
                .contains("/api/v1/hospital/census", "/api/v1/hospital/beds", "/api/v1/emergency/queue")
                .anyMatch(path -> path.startsWith("/api/v1/admin/specialties"));
    }
}
