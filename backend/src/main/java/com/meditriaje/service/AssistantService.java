package com.meditriaje.service;

import com.meditriaje.dto.assistant.AssistantQuestionRequest;
import com.meditriaje.dto.assistant.AssistantAnswerResponse;
import com.meditriaje.dto.assistant.ActionSuggestionDto;
import com.meditriaje.exception.InvalidDataException;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Servicio y motor del Asistente Virtual del Sistema (F2.6, RF-27, ADR-018, §5.19).
 * Guía interactiva de orientación operativa y navegación con detección infalible de emergencias.
 * Regla de oro: CERO diagnósticos, CERO prescripción de medicamentos.
 */
@Service
public class AssistantService {

    public static final String LEGAL_NOTICE =
            "Esta orientación es proporcionada por el asistente de navegación de MediTriaje 2.0. "
            + "No sustituye la valoración, diagnóstico ni tratamiento de un profesional de la salud.";

    private static final List<String> EMERGENCY_KEYWORDS = List.of(
            "dolor de pecho", "dolor toracico", "presion en el pecho", "opresion en el pecho",
            "no puedo respirar", "falta de aire", "ahogo", "dificultad respiratoria",
            "inconsciente", "perdida de conciencia", "desmayo", "sin conocimiento",
            "convulsion", "convulsiones",
            "sangrado abundante", "sangrado incontrolable", "hemorragia",
            "paralisis", "boca torcida", "perdida de fuerza en brazo", "acv", "derrame cerebral", "infarto",
            "intoxicacion severa", "envenenamiento"
    );

    public AssistantAnswerResponse processQuery(AssistantQuestionRequest request) {
        if (request == null || request.message() == null || request.message().trim().isBlank()) {
            throw new InvalidDataException("El mensaje de consulta no puede estar vacío.");
        }

        String rawMensaje = request.message().trim();
        String normalizado = normalizeText(rawMensaje);
        String rol = resolveRole(request);

        // 1. Detección infalible de emergencia médica vital (aplica a todos los roles)
        if (isEmergencyAlert(normalizado)) {
            return buildEmergencyAnswer(rol);
        }

        // 2. Enrutamiento contextual según el rol del usuario
        if ("GUEST".equals(rol) || "PUBLIC".equals(rol) || "INVITADO".equals(rol)) {
            return processGuestQuery(normalizado);
        } else if (rol.contains("ADMINISTRADOR")) {
            return processAdminQuery(normalizado);
        } else if (rol.contains("PROFESIONAL")) {
            return processProfessionalQuery(normalizado);
        } else if (rol.contains("FARMACEUTICO")) {
            return processPharmacyQuery(normalizado);
        } else {
            return processPatientQuery(normalizado);
        }
    }

    private String resolveRole(AssistantQuestionRequest request) {
        if (request != null && request.context() != null && !request.context().trim().isBlank()) {
            return request.context().trim().toUpperCase(Locale.ROOT);
        }
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getAuthorities() != null) {
            for (org.springframework.security.core.GrantedAuthority ga : auth.getAuthorities()) {
                String a = ga.getAuthority();
                if (a != null && a.startsWith("ROLE_")) {
                    return a.toUpperCase(Locale.ROOT);
                }
            }
        }
        return "ROLE_PACIENTE";
    }

    private AssistantAnswerResponse buildEmergencyAnswer(String rol) {
        List<ActionSuggestionDto> sugerencias;
        if ("GUEST".equals(rol) || "PUBLIC".equals(rol) || "INVITADO".equals(rol)) {
            sugerencias = List.of(
                    new ActionSuggestionDto("Llamar al 123", "tel:123", "phone"),
                    new ActionSuggestionDto("Ver Niveles de Emergencia", "#/", "alert-circle")
            );
        } else if (rol.contains("ADMINISTRADOR") || rol.contains("PROFESIONAL") || rol.contains("FARMACEUTICO")) {
            sugerencias = List.of(
                    new ActionSuggestionDto("Llamar al 123", "tel:123", "phone")
            );
        } else {
            sugerencias = List.of(
                    new ActionSuggestionDto("Llamar al 123", "tel:123", "phone"),
                    new ActionSuggestionDto("Iniciar Triaje de Emergencia", "#/patient/triage", "activity")
            );
        }

        return new AssistantAnswerResponse(
                "⚠️ ALERTA DE EMERGENCIA MÉDICA: Los síntomas o situaciones que describes indican un posible riesgo vital o emergencia inminente. "
                + "Por favor, comunícate inmediatamente a la línea nacional de emergencias 123 o acude sin demora al servicio de urgencias hospitalario más cercano. "
                + "No aguardes por una cita programada ni esperes respuesta en línea.",
                true,
                "EMERGENCIA",
                sugerencias,
                LEGAL_NOTICE
        );
    }

    // --- FLUJO PARA INVITADOS / USUARIOS SIN SESIÓN (GUEST) ---
    private AssistantAnswerResponse processGuestQuery(String normalizado) {
        if (containsAny(normalizado, "cita", "agendar", "reservar", "cancelar", "disponibilidad", "slot", "turno", "horario")) {
            return new AssistantAnswerResponse(
                    "En MediTriaje 2.0 puedes agendar citas médicas presenciales o por telemedicina en tiempo real y sin colisiones de horario.\n\n"
                    + "🔒 **Para agendar tu cita**: Debes iniciar sesión con tu cuenta de paciente. Si aún no tienes una, puedes registrarte en pocos minutos con tu documento de identidad colombiano.",
                    false,
                    "CITAS_GUEST",
                    List.of(
                            new ActionSuggestionDto("Iniciar Sesión", "#/login", "user"),
                            new ActionSuggestionDto("Registrarme como Paciente", "#/register", "user-plus"),
                            new ActionSuggestionDto("Conocer la Plataforma", "#/", "hospital")
                    ),
                    LEGAL_NOTICE
            );
        }

        if (containsAny(normalizado, "triaje", "triage", "sintoma", "prioridad", "nivel", "clasificacion")) {
            return new AssistantAnswerResponse(
                    "El Triaje Clínico de MediTriaje 2.0 te orienta según la severidad y duración de tus síntomas en 5 niveles oficiales:\n"
                    + "• Nivel I (Rojo): Urgencia vital inmediata (remisión directa a urgencias/123).\n"
                    + "• Nivel II (Naranja): Atención prioritaria en urgencias.\n"
                    + "• Nivel III (Amarillo): Cita médica presencial general.\n"
                    + "• Nivel IV (Verde): Consulta médica por telemedicina.\n"
                    + "• Nivel V (Azul): Consulta médica general programada.\n\n"
                    + "🔒 **Para realizar tu triaje oficial**: Inicia sesión con tu cuenta de paciente. También puedes probar el demostrador interactivo en nuestra página de inicio.",
                    false,
                    "TRIAJE_GUEST",
                    List.of(
                            new ActionSuggestionDto("Iniciar Sesión", "#/login", "user"),
                            new ActionSuggestionDto("Registrarme como Paciente", "#/register", "user-plus"),
                            new ActionSuggestionDto("Probar Simulador de Triaje", "#/", "activity")
                    ),
                    LEGAL_NOTICE
            );
        }

        if (containsAny(normalizado, "registro", "registrar", "crear cuenta", "abrir cuenta", "nuevo usuario", "inscribir")) {
            return new AssistantAnswerResponse(
                    "El registro en MediTriaje 2.0 es gratuito para pacientes. Requiere tu documento de identidad colombiano (CC, TI, RC o CE), fecha de nacimiento para cálculo cronológico de edad y datos de contacto.",
                    false,
                    "REGISTRO_GUEST",
                    List.of(
                            new ActionSuggestionDto("Registrarme como Paciente", "#/register", "user-plus"),
                            new ActionSuggestionDto("Iniciar Sesión", "#/login", "user")
                    ),
                    LEGAL_NOTICE
            );
        }

        if (containsAny(normalizado, "receta", "medicamento", "farmacia", "droga", "remedio")) {
            return new AssistantAnswerResponse(
                    "Las recetas médicas en MediTriaje 2.0 se emiten digitalmente con código único e inmutable tras la atención médica. Para consultar tus recetas debes ingresar a tu cuenta de paciente.",
                    false,
                    "FARMACIA_GUEST",
                    List.of(
                            new ActionSuggestionDto("Iniciar Sesión", "#/login", "user"),
                            new ActionSuggestionDto("Registrarme", "#/register", "user-plus")
                    ),
                    LEGAL_NOTICE
            );
        }

        if (containsAny(normalizado, "contrasena", "password", "clave", "recuperar", "olvide")) {
            return new AssistantAnswerResponse(
                    "Si olvidaste tu contraseña, puedes recuperarla mediante un código numérico temporal de 6 dígitos enviado a tu correo registrado (válido por 15 minutos).",
                    false,
                    "SEGURIDAD_GUEST",
                    List.of(
                            new ActionSuggestionDto("Recuperar Contraseña", "#/forgot-password", "shield"),
                            new ActionSuggestionDto("Iniciar Sesión", "#/login", "user")
                    ),
                    LEGAL_NOTICE
            );
        }

        return new AssistantAnswerResponse(
                "¡Hola! Te damos la bienvenida a **MediTriaje 2.0**, plataforma cloud de salud asistencial en Colombia.\n\n"
                + "Puedo orientarte sobre el funcionamiento de nuestro triaje clínico inteligente, agendamiento de citas, historia clínica inmutable y recetas digitales.\n\n"
                + "🔒 Para agendar una cita o realizar tu triaje oficial, por favor inicia sesión o crea tu cuenta gratuita de paciente.",
                false,
                "GENERAL_GUEST",
                List.of(
                        new ActionSuggestionDto("Iniciar Sesión", "#/login", "user"),
                        new ActionSuggestionDto("Registrarme como Paciente", "#/register", "user-plus"),
                        new ActionSuggestionDto("Conocer Más", "#/", "hospital")
                ),
                LEGAL_NOTICE
        );
    }

    // --- FLUJO PARA ADMINISTRADOR (ROLE_ADMINISTRADOR) ---
    private AssistantAnswerResponse processAdminQuery(String normalizado) {
        if (containsAny(normalizado, "reporte", "metrica", "estadistica", "operativo", "indicador", "dashboard", "grafic")) {
            return new AssistantAnswerResponse(
                    "En el módulo de Reportes Operativos puedes monitorear indicadores clave en tiempo real:\n"
                    + "• Triajes: Distribución por niveles (I al V) y tasas de emergencia.\n"
                    + "• Citas: Turnos agendados, completados y cancelaciones oportunas.\n"
                    + "• Atenciones: Volumen asistencial y diagnósticos CIE-10.\n"
                    + "• Farmacia: Prescripciones emitidas y entregas de medicamentos.",
                    false,
                    "REPORTES_ADMIN",
                    List.of(
                            new ActionSuggestionDto("Ver Reportes Operativos", "#/admin/reports", "bar-chart-2"),
                            new ActionSuggestionDto("Panel Principal", "#/admin/dashboard", "layout")
                    ),
                    LEGAL_NOTICE
            );
        }

        if (containsAny(normalizado, "profesional", "medico", "doctor", "alta", "rethus", "talento humano", "especialista")) {
            return new AssistantAnswerResponse(
                    "Gestión de Talento Humano en Salud bajo la Ley 1164 de 2007:\n"
                    + "• Documentos válidos: Cédula de Ciudadanía (CC) o Extranjería (CE).\n"
                    + "• ReTHUS: Número de registro médico profesional obligatorio y único.\n"
                    + "• Celular: Número de 10 dígitos conforme a numeración colombiana.\n"
                    + "• Especialidad: Asignación según catálogo MinSalud (Resolución 3100 de 2019).\n"
                    + "• Contraseña: El sistema genera una contraseña temporal segura para el médico.",
                    false,
                    "PROFESIONALES_ADMIN",
                    List.of(
                            new ActionSuggestionDto("Gestión de Profesionales", "#/admin/professionals", "users"),
                            new ActionSuggestionDto("Catálogo de Especialidades", "#/admin/specialties", "activity")
                    ),
                    LEGAL_NOTICE
            );
        }

        if (containsAny(normalizado, "sede", "institucion", "ips", "clinica", "hospital", "nit")) {
            return new AssistantAnswerResponse(
                    "Infraestructura Hospitalaria:\n"
                    + "• MediTriaje opera para un único hospital: la sede y los datos de la institución se configuran en la base de datos.\n"
                    + "• Desde el panel puedes gestionar camas, áreas, especialidades y profesionales.",
                    false,
                    "INFRAESTRUCTURA_ADMIN",
                    List.of(
                            new ActionSuggestionDto("Especialidades", "#/admin/specialties", "hospital"),
                            new ActionSuggestionDto("Ver Profesionales", "#/admin/professionals", "users")
                    ),
                    LEGAL_NOTICE
            );
        }

        if (containsAny(normalizado, "slot", "disponibilidad", "turno", "generar", "horario")) {
            return new AssistantAnswerResponse(
                    "Generador de Slots de Disponibilidad:\n"
                    + "• Genera turnos para profesionales del hospital con rango de fechas y horario.\n"
                    + "• Duración configurable o por defecto de la especialidad (20, 30 o 45 min).\n"
                    + "• Validación automática anti-solapes en base de datos bajo zona horaria America/Bogota.",
                    false,
                    "SLOTS_ADMIN",
                    List.of(
                            new ActionSuggestionDto("Generar Slots de Citas", "#/admin/slots", "calendar"),
                            new ActionSuggestionDto("Ver Profesionales", "#/admin/professionals", "users")
                    ),
                    LEGAL_NOTICE
            );
        }

        if (containsAny(normalizado, "auditoria", "seguridad", "evento", "trazabilidad", "log", "pistas")) {
            return new AssistantAnswerResponse(
                    "Registro de Auditoría de Seguridad (ADR-007, ADR-011):\n"
                    + "• Registro inmutable de eventos: logins, altas, actualizaciones de estado, emisión de recetas y emergencias.\n"
                    + "• Principio de privacidad: No expone contenido clínico sensible ni diagnósticos de pacientes.",
                    false,
                    "AUDITORIA_ADMIN",
                    List.of(
                            new ActionSuggestionDto("Consultar Auditoría", "#/admin/audit", "shield"),
                            new ActionSuggestionDto("Panel Principal", "#/admin/dashboard", "layout")
                    ),
                    LEGAL_NOTICE
            );
        }

        if (containsAny(normalizado, "cita", "agendar", "triaje", "sintoma")) {
            return new AssistantAnswerResponse(
                    "Como Administrador del sistema, no realizas triajes clínicos personales ni agendas citas de pacientes. Tu rol es habilitar la oferta: gestionar especialidades, profesionales y generar los slots de disponibilidad horaria.",
                    false,
                    "ADMIN_ORIENTACION",
                    List.of(
                            new ActionSuggestionDto("Generar Slots de Disponibilidad", "#/admin/slots", "calendar"),
                            new ActionSuggestionDto("Reportes Operativos", "#/admin/reports", "bar-chart-2"),
                            new ActionSuggestionDto("Gestión de Profesionales", "#/admin/professionals", "users")
                    ),
                    LEGAL_NOTICE
            );
        }

        return new AssistantAnswerResponse(
                "👋 ¡Hola, Administrador! Estás en la consola de gestión de **MediTriaje 2.0**.\n\n"
                + "Puedes consultarme sobre la generación de turnos, alta de profesionales (ReTHUS), supervisión hospitalaria, reportes operativos o auditoría de seguridad.",
                false,
                "GENERAL_ADMIN",
                List.of(
                        new ActionSuggestionDto("Reportes Operativos", "#/admin/reports", "bar-chart-2"),
                        new ActionSuggestionDto("Gestión de Profesionales", "#/admin/professionals", "users"),
                        new ActionSuggestionDto("Generador de Slots", "#/admin/slots", "calendar"),
                        new ActionSuggestionDto("Auditoría de Seguridad", "#/admin/audit", "shield")
                ),
                LEGAL_NOTICE
        );
    }

    // --- FLUJO PARA PROFESIONAL ASISTENCIAL (ROLE_PROFESIONAL) ---
    private AssistantAnswerResponse processProfessionalQuery(String normalizado) {
        if (containsAny(normalizado, "agenda", "cita", "turno", "horario", "paciente")) {
            return new AssistantAnswerResponse(
                    "En tu Agenda Médica puedes revisar las citas programadas para hoy, filtrar por rango de fechas o estado, y dar inicio a la atención clínica de cada paciente.",
                    false,
                    "AGENDA_PROFESIONAL",
                    List.of(
                            new ActionSuggestionDto("Mi Agenda Médica", "#/professional/agenda", "calendar")
                    ),
                    LEGAL_NOTICE
            );
        }

        if (containsAny(normalizado, "atencion", "historia", "cie-10", "diagnostico", "inmutable", "enmienda")) {
            return new AssistantAnswerResponse(
                    "Registro de Atención Clínica (Resolución 1995 de 1999):\n"
                    + "• Registro clínico estructurado con signos vitales, anamnesis y diagnóstico codificado CIE-10.\n"
                    + "• Inmutabilidad: Una vez guardada la atención, el registro no puede ser alterado ni eliminado.\n"
                    + "• Enmiendas: Cualquier aclaración debe anexarse como enmienda fechada y firmada.",
                    false,
                    "ATENCION_PROFESIONAL",
                    List.of(
                            new ActionSuggestionDto("Ir a Mi Agenda", "#/professional/agenda", "calendar")
                    ),
                    LEGAL_NOTICE
            );
        }

        if (containsAny(normalizado, "receta", "medicamento", "prescribir", "farmaco", "dosis", "posologia")) {
            return new AssistantAnswerResponse(
                    "Prescripción Médica Electrónica:\n"
                    + "• Prescribe fármacos del catálogo oficial indicando dosis, frecuencia, duración y cantidad.\n"
                    + "• Congelamiento histórico: Se almacena un snapshot inmutable del medicamento y se asigna código de reclamación al paciente.",
                    false,
                    "RECETA_PROFESIONAL",
                    List.of(
                            new ActionSuggestionDto("Mi Agenda Médica", "#/professional/agenda", "pill")
                    ),
                    LEGAL_NOTICE
            );
        }

        if (containsAny(normalizado, "break-glass", "urgencia", "emergencia asistencial", "acceso temporal")) {
            return new AssistantAnswerResponse(
                    "Protocolo Break-Glass de Acceso de Emergencia (ADR-007, F2.5):\n"
                    + "• Permite consultar el historial de un paciente en situaciones de urgencia sin relación asistencial previa.\n"
                    + "• Requiere justificación clínica obligatoria y genera auditoría inmutable de alta prioridad.",
                    false,
                    "BREAK_GLASS_PROFESIONAL",
                    List.of(
                            new ActionSuggestionDto("Mi Agenda Médica", "#/professional/agenda", "shield")
                    ),
                    LEGAL_NOTICE
            );
        }

        return new AssistantAnswerResponse(
                "👋 ¡Hola, Doctor(a)! Estás en el portal asistencial de **MediTriaje 2.0**.\n\n"
                + "Puedo orientarte en la consulta de tu agenda de citas, registro de atenciones clínicas CIE-10, emisión de recetas electrónicas o el protocolo Break-Glass.",
                false,
                "GENERAL_PROFESIONAL",
                List.of(
                        new ActionSuggestionDto("Mi Agenda Médica", "#/professional/agenda", "calendar")
                ),
                LEGAL_NOTICE
        );
    }

    // --- FLUJO PARA FARMACEUTICO (ROLE_FARMACEUTICO) ---
    private AssistantAnswerResponse processPharmacyQuery(String normalizado) {
        return new AssistantAnswerResponse(
                "Ventanilla de Dispensación Farmacéutica (ADR-016):\n"
                + "• Búsqueda: Ingresa el código alfanumérico de la receta (ej. REC-XXXXXXXX) o el documento del paciente.\n"
                + "• Validación: Verifica vigencia de la receta y lotes disponibles.\n"
                + "• Entregas: Registra entregas parciales o completas con cálculo automático de saldos.",
                false,
                "FARMACIA_ROL",
                List.of(
                        new ActionSuggestionDto("Ventanilla de Farmacia", "#/pharmacy/dispensation", "pill")
                ),
                LEGAL_NOTICE
        );
    }

    // --- FLUJO PARA PACIENTE (ROLE_PACIENTE / DEFAULT) ---
    private AssistantAnswerResponse processPatientQuery(String normalizado) {
        // Consulta sobre Triaje Clínico
        if (containsAny(normalizado, "triaje", "triage", "sintoma", "prioridad", "nivel i", "nivel ii", "nivel iii", "nivel iv", "nivel v", "clasificacion")) {
            return new AssistantAnswerResponse(
                    "El Triaje Clínico de MediTriaje 2.0 te orienta según la severidad y duración de tus síntomas:\n"
                    + "• Nivel I (Rojo): Urgencia vital inmediata (remisión directa a urgencias/123).\n"
                    + "• Nivel II (Naranja): Atención prioritaria en urgencias.\n"
                    + "• Nivel III (Amarillo): Cita médica presencial general.\n"
                    + "• Nivel IV (Verde): Consulta médica por telemedicina.\n"
                    + "• Nivel V (Azul): Consulta médica general programada.\n\n"
                    + "¿Deseas iniciar una evaluación guiada de síntomas ahora?",
                    false,
                    "TRIAJE",
                    List.of(
                            new ActionSuggestionDto("Realizar Triaje Clínico", "#/patient/triage", "activity"),
                            new ActionSuggestionDto("Consultar Citas", "#/patient/appointments", "calendar")
                    ),
                    LEGAL_NOTICE
            );
        }

        // Consulta sobre Citas y Agendamiento
        if (containsAny(normalizado, "cita", "agendar", "reservar", "cancelar", "disponibilidad", "slot", "turno", "horario")) {
            return new AssistantAnswerResponse(
                    "En MediTriaje 2.0 puedes buscar disponibilidad real por especialidad y sede hospitalaria en tiempo real.\n"
                    + "• Agendamiento: Selecciona tu especialidad y el horario más conveniente.\n"
                    + "• Cancelación oportuna: Si no puedes asistir, puedes cancelar tu cita hasta con 2 horas de anticipación para liberar el turno a otro paciente que lo necesite.\n"
                    + "• Puntualidad: Se recomienda conectarse o llegar 15 minutos antes de la hora acordada.",
                    false,
                    "CITAS",
                    List.of(
                            new ActionSuggestionDto("Buscar Disponibilidad", "#/patient/book", "calendar"),
                            new ActionSuggestionDto("Mis Citas Programadas", "#/patient/appointments", "clock")
                    ),
                    LEGAL_NOTICE
            );
        }

        // Consulta sobre Farmacia y Recetas Médicas
        if (containsAny(normalizado, "receta", "medicamento", "farmacia", "dispensacion", "reclamar", "lote", "invima", "saldo", "droga", "remedio")) {
            return new AssistantAnswerResponse(
                    "Tus recetas médicas son emitidas digitalmente con trazabilidad inmutable:\n"
                    + "• Código de reclamación: Cada receta cuenta con un código alfanumérico (ej. REC-XXXXXXXX).\n"
                    + "• En ventanilla: Presenta tu documento de identidad y tu código de reclamación en la farmacia de la sede.\n"
                    + "• Entregas y saldos: Puedes reclamar entregas parciales y hacer seguimiento del saldo pendiente de tus medicamentos desde tu portal.",
                    false,
                    "FARMACIA",
                    List.of(
                            new ActionSuggestionDto("Ver Mis Recetas", "#/patient/prescriptions", "pill"),
                            new ActionSuggestionDto("Mi Panel Principal", "#/patient/dashboard", "user")
                    ),
                    LEGAL_NOTICE
            );
        }

        // Consulta sobre Resumen de Salud y QR de Emergencia
        if (containsAny(normalizado, "qr", "codigo qr", "resumen de salud", "emergencia qr", "prehospitalario", "paramedico")) {
            return new AssistantAnswerResponse(
                    "El código QR de emergencia permite que personal de rescate o urgencias consulte un resumen seguro de tu salud ante una eventualidad:\n"
                    + "• Temporal y cifrado: Cada QR expira a los 15 minutos o tras 3 lecturas autorizadas.\n"
                    + "• Protección con PIN: Puedes exigir un PIN numérico de 4 dígitos para autorizar la lectura prehospitalaria.\n"
                    + "• Contenido: Incluye grupo sanguíneo, alergias activas, medicamentos vigentes y contactos de emergencia.",
                    false,
                    "RESUMEN_QR",
                    List.of(
                            new ActionSuggestionDto("Generar QR de Emergencia", "#/patient/emergency-qr", "shield"),
                            new ActionSuggestionDto("Ver Mi Historia", "#/patient/history", "file-text")
                    ),
                    LEGAL_NOTICE
            );
        }

        // Consulta sobre Historia Clínica e Inmutabilidad
        if (containsAny(normalizado, "historia", "historial", "antecedente", "inmutable", "enmienda", "correccion", "cie-10", "diagnostico")) {
            return new AssistantAnswerResponse(
                    "Tu historia clínica en MediTriaje 2.0 cumple estrictos estándares legales de seguridad (Resolución 1995 de 1999):\n"
                    + "• Inmutabilidad: Una vez cerrada la atención por el médico tratante, el registro no puede ser alterado ni borrado.\n"
                    + "• Aclaraciones: Cualquier corrección debe realizarse mediante enmiendas clínicas cronológicas fechadas y firmadas por el profesional.\n"
                    + "• Consulta segura: Solo tú y los profesionales con relación asistencial activa o autorización de emergencia pueden consultar tus registros.",
                    false,
                    "HISTORIA_CLINICA",
                    List.of(
                            new ActionSuggestionDto("Consultar Historia Clínica", "#/patient/history", "file-text"),
                            new ActionSuggestionDto("Mis Citas", "#/patient/appointments", "calendar")
                    ),
                    LEGAL_NOTICE
            );
        }

        // Consulta sobre Seguridad, Contraseña o MFA
        if (containsAny(normalizado, "contrasena", "password", "clave", "mfa", "totp", "recuperar", "bloqueo", "datos", "seguridad")) {
            return new AssistantAnswerResponse(
                    "Seguridad y gestión de tu cuenta en MediTriaje 2.0:\n"
                    + "• Recuperación: Si olvidaste tu contraseña, solicita un código numérico temporal de 6 dígitos enviado a tu correo registrado (válido por 15 minutos).\n"
                    + "• Doble factor (MFA): Profesionales y administradores cuentan con autenticación reforzada mediante aplicaciones TOTP (Google Authenticator / Authy) y códigos de respaldo.\n"
                    + "• Habeas Data: Tratamos tus datos sensibles con cifrado estricto y conforme a la Ley 1581 de 2012.",
                    false,
                    "SEGURIDAD",
                    List.of(
                            new ActionSuggestionDto("Recuperar Contraseña", "#/forgot-password", "shield"),
                            new ActionSuggestionDto("Iniciar Sesión", "#/login", "user")
                    ),
                    LEGAL_NOTICE
            );
        }

        // Orientación General por defecto para Paciente
        return new AssistantAnswerResponse(
                "¡Hola! Soy tu asistente de orientación en MediTriaje 2.0. Estoy aquí para ayudarte a navegar por la plataforma:\n"
                + "¿Qué deseas hacer hoy? Puedes preguntarme sobre cómo orientar tus síntomas con el triaje, agendar o cancelar citas, reclamar medicamentos con tu código de receta, o activar tu código QR de emergencia.",
                false,
                "GENERAL",
                List.of(
                        new ActionSuggestionDto("Triaje Clínico", "#/patient/triage", "activity"),
                        new ActionSuggestionDto("Agendar Cita", "#/patient/book", "calendar"),
                        new ActionSuggestionDto("Recetas Médicas", "#/patient/prescriptions", "pill")
                ),
                LEGAL_NOTICE
        );
    }

    private boolean isEmergencyAlert(String texto) {
        for (String palabra : EMERGENCY_KEYWORDS) {
            if (texto.contains(palabra)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String texto, String... terminos) {
        String textoLimpio = texto.replace("meditriaje", " ").replace("meditriage", " ");
        for (String termino : terminos) {
            if (textoLimpio.contains(normalizeText(termino))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeText(String input) {
        if (input == null) return "";
        String descompuesto = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return descompuesto.replaceAll("\\p{M}", "");
    }
}
