# MediTriaje 2.0 — Historias de Usuario (Especificación SDD)

> **Versión:** 1.0 · **Fecha:** noviembre 2026
> **Equipo:** Juan David Molina Jiménez, Ailyn Socarras Jiménez, Isabella Taborda
> **Asignaturas:** Programación III · Base de Datos — Universidad Popular del Cesar (UNICESAR)

---

## Índice

1. [Las historias de usuario dentro de SDD](#1-las-historias-de-usuario-dentro-de-sdd)
2. [Convenciones](#2-convenciones)
3. [Actores y roles](#3-actores-y-roles)
4. [Mapa de épicas](#4-mapa-de-épicas)
5. [Historias de usuario](#5-historias-de-usuario)
   - [E1 · Identidad y acceso](#e1--identidad-y-acceso) (HU-01, HU-12, HU-13, HU-14)
   - [E2 · Triaje web ambulatorio](#e2--triaje-web-ambulatorio) (HU-02)
   - [E3 · Disponibilidad y citas](#e3--disponibilidad-y-citas) (HU-03, HU-04, HU-05, HU-06, HU-15, HU-16)
   - [E4 · Atención clínica e historia](#e4--atención-clínica-e-historia) (HU-07, HU-17, HU-22)
   - [E5 · Recetas y farmacia](#e5--recetas-y-farmacia) (HU-08, HU-23, HU-24)
   - [E6 · Portal del paciente](#e6--portal-del-paciente) (HU-09, HU-18, HU-19, HU-20, HU-21)
   - [E7 · Administración de la oferta asistencial](#e7--administración-de-la-oferta-asistencial) (HU-10, HU-36, HU-42)
   - [E8 · Auditoría y reportes](#e8--auditoría-y-reportes) (HU-11, HU-39, HU-40)
   - [E9 · Urgencias](#e9--urgencias) (HU-25, HU-26, HU-27, HU-28)
   - [E10 · Hospitalización](#e10--hospitalización) (HU-29, HU-30, HU-31, HU-32)
   - [E11 · Afiliaciones y representación legal](#e11--afiliaciones-y-representación-legal) (HU-33, HU-34, HU-35)
   - [E12 · Operación hospitalaria](#e12--operación-hospitalaria) (HU-37, HU-38)
   - [E13 · Asistente del sistema](#e13--asistente-del-sistema) (HU-41)
6. [Matriz de trazabilidad](#6-matriz-de-trazabilidad)
7. [Requisitos no funcionales transversales](#7-requisitos-no-funcionales-transversales)
8. [Definition of Ready y Definition of Done](#8-definition-of-ready-y-definition-of-done)
9. [Pendientes y riesgos](#9-pendientes-y-riesgos)

---

## 1. Las historias de usuario dentro de SDD

En *Spec-Driven Development* (Desarrollo Guiado por Especificaciones) la especificación es el artefacto principal: el código, la base de datos y las pruebas se derivan de ella y se validan contra ella. En MediTriaje la cadena es:

```
Documento Maestro (RF / RNF)
        │
        ▼
Historia de usuario (HU)  ──►  Criterios de aceptación (Dado / Cuando / Entonces)
        │                                   │
        ├──► Reglas de negocio (RB)         ├──► Pruebas automatizadas (JUnit, Playwright)
        ├──► Decisiones de arquitectura (ADR)
        ├──► Contrato de API (endpoints)
        └──► Modelo de datos (tablas Oracle, constraints, triggers)
```

Reglas que sigue el equipo:

1. **No se escribe código sin una HU con criterios de aceptación.** Si aparece una funcionalidad nueva, primero se agrega aquí.
2. **Cada criterio de aceptación debe poder convertirse en una prueba.** Por eso se escriben en formato Gherkin (Dado / Cuando / Entonces) y con valores concretos.
3. **La seguridad se especifica, no se supone.** Los criterios incluyen siempre el caso de acceso denegado (otro paciente, otro rol, administrador sin acceso clínico).
4. **La base de datos también es especificación.** Cada HU indica las tablas que toca; las reglas críticas se refuerzan en Oracle (RB-15) con `CHECK`, `UNIQUE`, `FOREIGN KEY` y triggers.
5. **Trazabilidad en ambos sentidos.** Desde una HU se llega a su código y pruebas, y desde una prueba o tabla se puede saber qué HU la justifica (sección 6).

## 2. Convenciones

| Campo | Significado |
|---|---|
| **ID** | `HU-NN`. HU-01 a HU-11 = MVP. HU-12 en adelante = Fase 2 / Plan Maestro Hospitalario. |
| **Historia** | Formato *Como* [rol] *quiero* [acción] *para* [beneficio]. |
| **Prioridad** | MoSCoW: **Must** (imprescindible), **Should** (importante), **Could** (deseable), **Won't** (fuera de alcance por ahora). |
| **Complejidad** | Estimación relativa del equipo: **Baja**, **Media**, **Alta**. Se puede traducir a puntos en la planeación. |
| **Estado** | **Implementada** (endpoint + pruebas en el repositorio), **Parcial**, **Pendiente**. |
| **Criterios** | Escenarios Gherkin numerados `CA-NN.x`. |
| **Trazabilidad** | RF del Documento Maestro, RB, ADR, endpoints, tablas y pruebas. |

Códigos HTTP usados en los criterios: `400` datos inválidos, `401` no autenticado, `403` sin permiso, `404` no existe (o no debe revelarse que existe), `409` conflicto de estado o concurrencia.

## 3. Actores y roles

| Actor | Autoridad | Descripción |
|---|---|---|
| Paciente | `ROLE_PACIENTE` | Se registra, hace triaje web, agenda citas, consulta su información y genera QR de emergencia. |
| Profesional de la salud | `ROLE_PROFESIONAL` | Médico u odontólogo. Atiende, diagnostica, prescribe, hace seguimiento y puede usar *break-glass*. |
| Enfermería | `ROLE_ENFERMERIA` | Admisión y triaje presencial de urgencias, gestión de camas. No prescribe (ADR-026). |
| Farmacéutico | `ROLE_FARMACEUTICO` | Consulta recetas vigentes y registra dispensaciones. Sin acceso a historia clínica (ADR-016). |
| Administrador | `ROLE_ADMINISTRADOR` | Gestiona la oferta asistencial, afiliaciones, reportes y auditoría. **Sin acceso clínico** (RB-20). |
| Tercero con QR | *(sin cuenta)* | Personal de salud externo o familiar que lee un QR temporal o de seguimiento. |
| Sistema | — | Motor de triaje, control de concurrencia, alertas, auditoría y correos. |

## 4. Mapa de épicas

| Épica | Nombre | Historias | Alcance |
|---|---|---|---|
| E1 | Identidad y acceso | HU-01, HU-12, HU-13, HU-14 | MVP + Fase 2 |
| E2 | Triaje web ambulatorio | HU-02 | MVP |
| E3 | Disponibilidad y citas | HU-03, HU-04, HU-05, HU-06, HU-15, HU-16 | MVP + Fase 2 |
| E4 | Atención clínica e historia | HU-07, HU-17, HU-22 | MVP + Fase 2 |
| E5 | Recetas y farmacia | HU-08, HU-23, HU-24 | MVP + Fase 2 |
| E6 | Portal del paciente | HU-09, HU-18, HU-19, HU-20, HU-21 | MVP + Fase 2 |
| E7 | Administración de la oferta asistencial | HU-10, HU-36, HU-42 | MVP + Fase 2 |
| E8 | Auditoría y reportes | HU-11, HU-39, HU-40 | MVP + Fase 2 |
| E9 | Urgencias | HU-25, HU-26, HU-27, HU-28 | Plan Maestro Hospitalario |
| E10 | Hospitalización | HU-29, HU-30, HU-31, HU-32 | Plan Maestro Hospitalario |
| E11 | Afiliaciones y representación legal | HU-33, HU-34, HU-35 | Plan Maestro Hospitalario |
| E12 | Operación hospitalaria | HU-37, HU-38 | Plan Maestro Hospitalario |
| E13 | Asistente del sistema | HU-41 | Fase 2 |

---

## 5. Historias de usuario

### E1 · Identidad y acceso

#### HU-01 · Registro e inicio de sesión del paciente

> **Como** paciente, **quiero** registrarme con mi documento de identidad y aceptar el tratamiento de mis datos, **para** acceder a MediTriaje de forma segura y con mis datos protegidos.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Must | Alta | Implementada | E1 |

**Criterios de aceptación**

- **CA-01.1 Registro exitoso**
  **Dado** un visitante no autenticado con tipo de documento válido (CC, TI, RC, CE, PA), nombres, fecha de nacimiento, correo y contraseña de al menos 10 caracteres
  **Cuando** envía el formulario con la casilla de consentimiento marcada
  **Entonces** se crean en una sola transacción `USUARIO` (rol PACIENTE), `PACIENTE` y `CONSENTIMIENTO` (con fecha y versión del texto), la contraseña se guarda con Argon2id y se audita `REGISTRO_PACIENTE_EXITOSO`.
- **CA-01.2 Consentimiento obligatorio**
  **Dado** un formulario completo **Cuando** se envía sin aceptar el consentimiento **Entonces** responde `400` y no se crea ningún registro.
- **CA-01.3 Coherencia documento–edad** (ADR-020)
  **Dado** un registro con CC y fecha de nacimiento de un menor de 18 años **Cuando** se envía **Entonces** responde `400`. Igual para TI fuera de 7–17 años y RC con 7 años o más.
- **CA-01.4 Duplicados sin enumeración**
  **Dado** un correo o documento ya registrado **Cuando** se intenta registrar de nuevo **Entonces** se rechaza y la transacción se revierte por completo.
- **CA-01.5 Inicio de sesión**
  **Dado** un usuario activo **Cuando** ingresa credenciales correctas **Entonces** recibe un *access token* JWT de 15 minutos y un *refresh token* rotativo de 7 días en cookies `HttpOnly; Secure; SameSite=Strict`, y se audita `LOGIN_EXITOSO`.
- **CA-01.6 Error genérico**
  **Dado** un correo inexistente o una contraseña incorrecta **Cuando** intenta iniciar sesión **Entonces** recibe el mismo mensaje "Credenciales inválidas" en ambos casos.
- **CA-01.7 Bloqueo por intentos**
  **Dado** 4 intentos fallidos consecutivos **Cuando** ocurre el quinto **Entonces** la cuenta queda bloqueada 15 minutos y se audita `LOGIN_BLOQUEO`.
- **CA-01.8 Cierre de sesión**
  **Dado** un usuario autenticado **Cuando** cierra sesión **Entonces** el *refresh token* queda revocado en BD y un intento posterior de `refresh` con él responde `401`.

**Trazabilidad:** RF-01, RF-02, RF-03, RF-04 · RB-02, RB-11, RB-24 · ADR-002, ADR-013, ADR-020 · `POST /api/v1/auth/register|login|refresh|logout` · `USUARIO`, `ROL`, `USUARIO_ROL`, `PACIENTE`, `CONSENTIMIENTO`, `REFRESH_TOKEN`, `AUDITORIA` · `AuthServiceTest`, `AuthControllerTest`, `TokenLifecycleSecurityTest`, `NormaColombianaValidatorTest`, `JwtServiceTest`.

---

#### HU-12 · Recuperar contraseña con código por correo

> **Como** usuario que olvidó su contraseña, **quiero** recibir un código de verificación en mi correo, **para** restablecerla sin depender de un administrador.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Media | Implementada | E1 |

**Criterios de aceptación**

- **CA-12.1 Respuesta anti-enumeración**
  **Dado** cualquier correo, exista o no **Cuando** se solicita recuperación **Entonces** la respuesta es siempre `200` con el mismo mensaje genérico.
- **CA-12.2 Código seguro**
  **Dado** un correo registrado **Cuando** se solicita recuperación **Entonces** se genera un código de 6 dígitos con `SecureRandom`, se guarda **hasheado con SHA-256** en `CODIGO_VERIFICACION`, vence a los 15 minutos y se envía en una plantilla HTML institucional.
- **CA-12.3 Límite de intentos**
  **Dado** un código vigente **Cuando** se ingresa mal 3 veces **Entonces** el código queda invalidado.
- **CA-12.4 Restablecimiento**
  **Dado** un código correcto y vigente y una nueva contraseña de al menos 10 caracteres **Cuando** se confirma **Entonces** se guarda con Argon2id, el código se marca como usado, se revocan **todas** las sesiones activas y se audita el evento.
- **CA-12.5 Fallo de correo**
  **Dado** que el proveedor de correo falla **Cuando** se solicita recuperación **Entonces** la respuesta sigue siendo `200` genérico y se audita `EMAIL_FALLIDO`.

**Trazabilidad:** RF-02 · RB-11 · ADR-014, ADR-020 · `POST /api/v1/auth/forgot-password`, `POST /api/v1/auth/reset-password` · `CODIGO_VERIFICACION`, `REFRESH_TOKEN`, `AUDITORIA` · `AuthServiceTest`, `CodigoVerificacionRepositoryTest`, `EmailServiceTest`, `BrevoApiEmailTransportTest`.

---

#### HU-13 · Autenticación multifactor (MFA)

> **Como** profesional o administrador, **quiero** proteger mi cuenta con un segundo factor (TOTP), **para** que nadie entre a información sensible solo con mi contraseña.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Alta | Implementada | E1 |

**Criterios de aceptación**

- **CA-13.1 Enrolamiento en dos pasos**
  **Dado** un usuario autenticado **Cuando** solicita configurar MFA **Entonces** recibe un secreto Base32 y una URI `otpauth://`, y MFA solo queda activo tras confirmar un primer código válido.
- **CA-13.2 Códigos de respaldo**
  **Dado** que MFA se activa **Entonces** se generan 8 códigos de respaldo de un solo uso, almacenados hasheados en `MFA_BACKUP_CODE`.
- **CA-13.3 Desafío en el login**
  **Dado** un usuario con MFA activo **Cuando** ingresa contraseña correcta **Entonces** recibe `mfaRequerido: true` y un token de desafío de 5 minutos, **sin** cookies de sesión.
- **CA-13.4 Segundo factor**
  **Dado** un token de desafío vigente **Cuando** envía un TOTP válido (RFC 6238, pasos de 30 s) o un código de respaldo no usado **Entonces** recibe las cookies de sesión; con un código inválido o desafío vencido responde `401`.

**Trazabilidad:** RF-02 · ADR-014 · `POST /api/v1/auth/mfa/setup|verify|authenticate` · `USUARIO`, `MFA_BACKUP_CODE` · `TotpServiceTest`, `Base32UtilTest`, `MfaBackupCodeRepositoryTest`, `AuthServiceTest`.

---

#### HU-14 · Cambiar contraseña

> **Como** usuario autenticado (incluido un profesional creado por el administrador), **quiero** cambiar mi contraseña, **para** reemplazar la temporal o una que creo comprometida.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Baja | Implementada | E1 |

**Criterios de aceptación**

- **CA-14.1** **Dado** un usuario autenticado **Cuando** envía su contraseña actual correcta y una nueva de al menos 10 caracteres y distinta de la actual **Entonces** la nueva se guarda con Argon2id, se desactiva la marca de "debe cambiar contraseña" y se audita el cambio.
- **CA-14.2** **Dado** una contraseña actual incorrecta **Cuando** intenta cambiarla **Entonces** responde error y no hay cambios.
- **CA-14.3** **Dado** un usuario no autenticado **Cuando** llama al endpoint **Entonces** responde `401`.

**Trazabilidad:** RF-02 · ADR-002 · `POST /api/v1/auth/change-password` · `USUARIO` (migración V005) · `AuthServiceTest`, `AuthControllerTest`.

---

### E2 · Triaje web ambulatorio

#### HU-02 · Triaje web con corte de emergencia

> **Como** paciente, **quiero** registrar mis síntomas, su duración e intensidad, **para** saber qué tan urgente es mi caso y qué ruta de atención seguir.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Must | Alta | Implementada | E2 |

**Criterios de aceptación**

- **CA-02.1 Entrada válida**
  **Dado** un paciente autenticado **Cuando** elige uno o más síntomas del catálogo, una duración ≥ 0 horas y una intensidad entera entre 0 y 10 **Entonces** el sistema evalúa el triaje; valores fuera de rango responden `400`.
- **CA-02.2 Corte de emergencia**
  **Dado** que al menos un síntoma tiene `ES_ALARMA = 1`, o el nivel resultante es I **Cuando** se evalúa **Entonces** el resultado es nivel I, ruta URGENCIAS, mensaje «Llama al 123 o acude a urgencias de inmediato.», **no se ofrece cita** y se audita `TRIAJE_EMERGENCIA` sin síntomas en el registro.
- **CA-02.3 Varios síntomas**
  **Dado** varios síntomas con distintos niveles **Cuando** se evalúa **Entonces** el nivel final es el **más urgente**.
- **CA-02.4 Nivel conservador**
  **Dado** un síntoma que no coincide con ninguna regla **Cuando** se evalúa **Entonces** aporta nivel III; **nunca** se asigna nivel V por defecto.
- **CA-02.5 Ruta sugerida**
  **Dado** un nivel final **Entonces** la ruta es I→URGENCIAS, II→ATENCION_PRIORITARIA, III→CITA_PRESENCIAL, IV→CITA_TELEMEDICINA, V→CONSULTA_PROGRAMADA.
- **CA-02.6 Determinismo y versión**
  **Dado** las mismas entradas **Cuando** se evalúan dos veces **Entonces** el resultado es idéntico, y cada triaje guarda la versión de reglas usada.
- **CA-02.7 Aviso legal**
  **Dado** cualquier resultado **Entonces** incluye el aviso de que es un prototipo que no sustituye la valoración profesional, sin diagnósticos ni medicamentos.
- **CA-02.8 Aislamiento**
  **Dado** el triaje de otro paciente **Cuando** un paciente lo consulta por su ID **Entonces** responde `403` o `404`.

**Trazabilidad:** RF-09, RF-10, RF-11 · RB-12, RB-22 · ADR-009 · `GET /api/v1/triage/symptoms`, `POST /api/v1/triage`, `GET /api/v1/triage/{id}` · `SINTOMA`, `REGLA_TRIAJE`, `TRIAJE`, `TRIAJE_SINTOMA`, `AUDITORIA` · `MotorTriajeBasadoEnReglasTest`, `CorteEmergenciaTest`, `TriajeMotorFactoryTest`, `TriajeServiceTest`, `TriajeControllerTest`, `TriajeReglasRepositoryTest`, E2E `01-patient-triage-appointment.spec.js`.

---

### E3 · Disponibilidad y citas

#### HU-03 · Consultar disponibilidad

> **Como** paciente, **quiero** buscar horarios libres por especialidad, fecha, sede y modalidad, **para** elegir la cita que me sirve.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Must | Media | Implementada | E3 |

**Criterios de aceptación**

- **CA-03.1** **Dado** slots en estados LIBRE, OCUPADO y BLOQUEADO **Cuando** el paciente consulta disponibilidad **Entonces** solo se devuelven slots `LIBRE` con inicio en el futuro (zona `America/Bogota`).
- **CA-03.2** **Dado** filtros de especialidad, fecha, sede o modalidad **Cuando** se aplican **Entonces** el resultado respeta todos los filtros enviados.
- **CA-03.3** **Dado** un usuario no autenticado **Cuando** consulta **Entonces** responde `401`.

**Trazabilidad:** RF-08, RF-12 · RB-04, RB-12 · ADR-005, ADR-006 · `GET /api/v1/availability` · `DISPONIBILIDAD_SLOT`, `PROFESIONAL`, `ESPECIALIDAD`, `SEDE` · `AvailabilityServiceTest`, `AvailabilityControllerTest`, `DisponibilidadSlotRepositoryTest`.

---

#### HU-04 · Agendar cita sin doble reserva

> **Como** paciente, **quiero** reservar un horario libre (opcionalmente ligado a mi triaje), **para** asegurar mi atención sin que otra persona tome el mismo cupo.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Must | Alta | Implementada | E3 |

**Criterios de aceptación**

- **CA-04.1 Reserva**
  **Dado** un slot LIBRE y futuro **Cuando** el paciente lo reserva **Entonces** se crea una `CITA` en estado PROGRAMADA, el slot pasa a OCUPADO y se audita.
- **CA-04.2 Concurrencia**
  **Dado** dos solicitudes simultáneas sobre el mismo slot **Cuando** se procesan **Entonces** exactamente una tiene éxito y la otra recibe `409` (`CitaNoDisponible`).
- **CA-04.3 Slot no disponible**
  **Dado** un slot OCUPADO, BLOQUEADO o pasado **Cuando** se intenta reservar **Entonces** responde `409` o `400`.
- **CA-04.4 Vínculo opcional al triaje**
  **Dado** un triaje propio **Cuando** se reserva indicando su ID **Entonces** la cita queda vinculada; con un triaje ajeno responde `403`/`404`.

**Trazabilidad:** RF-13 · RB-04, RB-05, RB-17, RB-18 · ADR-006 · `POST /api/v1/appointments` · `CITA`, `DISPONIBILIDAD_SLOT` (índice único funcional) · `AppointmentConcurrencyTest`, `AppointmentServiceTest`, `AppointmentControllerTest`, `CitaRepositoryTest`, `CitaStateMachineTest`.

---

#### HU-05 · Cancelar cita y registrar inasistencia

> **Como** paciente, **quiero** cancelar mi cita con anticipación, **para** liberar el cupo; **y como** profesional o administrador, **quiero** registrar la inasistencia, **para** llevar control de las citas perdidas.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Must | Media | Implementada | E3 |

**Criterios de aceptación**

- **CA-05.1 Cancelación a tiempo**
  **Dado** una cita PROGRAMADA propia que empieza en 2 horas o más **Cuando** el paciente la cancela **Entonces** pasa a CANCELADA, el slot vuelve a LIBRE y se audita.
- **CA-05.2 Fuera de ventana**
  **Dado** una cita que empieza en menos de 2 horas **Cuando** el paciente intenta cancelarla **Entonces** responde `400`.
- **CA-05.3 Inasistencia**
  **Dado** una cita PROGRAMADA cuya hora de inicio ya llegó **Cuando** el profesional asignado o el administrador marcan inasistencia **Entonces** pasa a NO_ASISTIO y el slot sigue OCUPADO.
- **CA-05.4 Profesional ajeno**
  **Dado** una cita de otro profesional **Cuando** un profesional intenta cancelarla o marcar inasistencia **Entonces** responde `403`.
- **CA-05.5 Atención vinculada**
  **Dado** una cita con atención clínica abierta o cerrada **Cuando** se intenta cancelar o marcar inasistencia **Entonces** responde `409`.
- **CA-05.6 Máquina de estados**
  **Dado** cualquier cita **Entonces** solo se permiten `PROGRAMADA → CANCELADA | NO_ASISTIO | ATENDIDA`.

**Trazabilidad:** RF-15 · RB-11, RB-16, RB-17 · ADR-006 · `PATCH /api/v1/appointments/{id}/cancel`, `PATCH /api/v1/appointments/{id}/no-show` · `CITA`, `DISPONIBILIDAD_SLOT`, `AUDITORIA` · `CitaStateMachineTest`, `AppointmentServiceTest`, `AppointmentControllerTest`.

---

#### HU-06 · Agenda del profesional

> **Como** profesional, **quiero** ver mi agenda de citas, **para** organizar mi jornada.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Must | Baja | Implementada | E3 |

**Criterios de aceptación**

- **CA-06.1** **Dado** un profesional autenticado **Cuando** consulta su agenda **Entonces** ve únicamente las citas asignadas a él.
- **CA-06.2** **Dado** un paciente o administrador **Cuando** llama al endpoint **Entonces** responde `403`.

**Trazabilidad:** RF-14 · RB-02 · `GET /api/v1/professionals/me/agenda` · `CITA`, `PROFESIONAL` · `ProfessionalAgendaControllerTest`.

---

#### HU-15 · Reprogramar cita

> **Como** paciente, profesional o administrador, **quiero** mover una cita a otro horario, **para** no tener que cancelarla y volver a buscar cupo.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Alta | Implementada | E3 |

**Criterios de aceptación**

- **CA-15.1** **Dado** una cita PROGRAMADA propia y un nuevo slot LIBRE **Cuando** el paciente la reprograma con 2 horas o más de anticipación **Entonces** se ocupa el nuevo slot, se libera el anterior y se audita, todo en una transacción.
- **CA-15.2** **Dado** menos de 2 horas para la cita **Cuando** el paciente intenta reprogramar **Entonces** responde `400`.
- **CA-15.3** **Dado** un nuevo slot ya reservado, idéntico al actual o en el pasado **Cuando** se reprograma **Entonces** responde `409` o `400`.
- **CA-15.4** **Dado** una cita con atención clínica vinculada **Cuando** se intenta reprogramar **Entonces** responde `409`.
- **CA-15.5** **Dado** un profesional que no es el asignado **Cuando** intenta reprogramar **Entonces** responde `403`.

**Trazabilidad:** RF-15 · RB-05, RB-16, RB-17 · ADR-006 · `PATCH /api/v1/appointments/{id}/reschedule` · `CITA`, `DISPONIBILIDAD_SLOT` (migración V020) · `AppointmentServiceTest`, `AppointmentControllerTest`.

---

#### HU-16 · Notificación de citas por correo

> **Como** paciente, **quiero** recibir un correo cuando reservo o cancelo una cita, **para** tener la confirmación con fecha, hora, profesional y sede.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Could | Media | Implementada | E3 |

**Criterios de aceptación**

- **CA-16.1** **Dado** una reserva exitosa **Entonces** se envía un correo con plantilla `confirmacion-cita.html` y hora local de Bogotá.
- **CA-16.2** **Dado** una cancelación **Entonces** se envía un correo con plantilla `cancelacion-cita.html`.
- **CA-16.3** **Dado** cada envío **Entonces** queda un registro en `RECORDATORIO_CITA` con estado `ENVIADO` o `FALLIDO`.
- **CA-16.4 Resiliencia** **Dado** que el envío falla **Entonces** la reserva o cancelación **no** se revierte.

**Trazabilidad:** RF-29 · ADR-015, ADR-020 · (efecto de `POST /appointments` y `PATCH /cancel`) · `RECORDATORIO_CITA` · `AppointmentNotificationServiceTest`, `RecordatorioCitaRepositoryTest`, `EmailServiceTest`.

---

### E4 · Atención clínica e historia

#### HU-07 · Registrar atención clínica inmutable

> **Como** profesional, **quiero** registrar la atención (motivo, evolución, signos vitales, diagnóstico CIE-10, indicaciones y alergias), **para** dejar constancia clínica confiable que no pueda alterarse después.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Must | Alta | Implementada | E4 |

**Criterios de aceptación**

- **CA-07.1 Relación asistencial**
  **Dado** un profesional sin cita futura con el paciente, sin atención propia en los últimos 12 meses y sin *break-glass* vigente **Cuando** intenta iniciar una atención o ver la historia **Entonces** responde `403`.
- **CA-07.2 Inicio**
  **Dado** una cita PROGRAMADA del profesional **Cuando** inicia la atención **Entonces** la cita sigue en PROGRAMADA y la atención queda ABIERTA.
- **CA-07.3 Cierre**
  **Dado** una atención abierta con diagnóstico CIE-10 activo **Cuando** el profesional la cierra **Entonces** queda CERRADA, la cita pasa a ATENDIDA y se audita.
- **CA-07.4 Inmutabilidad**
  **Dado** una atención CERRADA **Cuando** se intenta un `UPDATE` sobre ella (por la API o directamente en BD) **Entonces** se rechaza; el trigger de Oracle bloquea la operación.
- **CA-07.5 Enmienda**
  **Dado** una atención cerrada **Cuando** el profesional necesita corregir algo **Entonces** crea una enmienda (quién, cuándo, qué, motivo) enlazada a la original sin modificarla.
- **CA-07.6 Alergias clínicas**
  **Dado** un paciente con relación asistencial **Cuando** el profesional registra o inactiva una alergia **Entonces** la inactivación exige motivo y nunca hay borrado físico.
- **CA-07.7 Diagnóstico inválido**
  **Dado** un código CIE-10 inexistente o inactivo **Cuando** se cierra la atención **Entonces** responde `404` o `400`.

**Trazabilidad:** RF-16, RF-17 · RB-06, RB-08, RB-19, RB-21 · ADR-007, ADR-008 · `POST /api/v1/attentions`, `POST /attentions/{id}/close`, `POST /attentions/{id}/amendments`, `GET /attentions/{id}`, `GET /api/v1/clinical/patients/{id}/history`, `GET|POST /api/v1/clinical/patients/{id}/allergies`, `PATCH /clinical/allergies/{id}/deactivate`, `GET /api/v1/catalogs/icd10` · `ATENCION`, `SIGNO_VITAL`, `ATENCION_ENMIENDA`, `DIAGNOSTICO_CIE`, `ALERGIA`, `CITA` · `ClinicalAttentionServiceTest`, `ClinicalAttentionControllerTest`, `AccesoClinicoServiceTest`, `ClinicalPatientHistoryControllerTest`, `ClinicalAllergyControllerTest`, `AtencionRepositoryTest`, `AlergiaRepositoryTest`, E2E `02-professional-attention-recipe.spec.js`.

---

#### HU-17 · Seguimiento post-atención (profesional)

> **Como** profesional, **quiero** asignar tareas de seguimiento al paciente después de atenderlo, **para** controlar su evolución, exámenes pendientes o adherencia al tratamiento.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Media | Implementada | E4 |

**Criterios de aceptación**

- **CA-17.1** **Dado** una atención cerrada propia o con relación asistencial activa **Cuando** el profesional crea un seguimiento de tipo `CONTROL_MEDICO`, `EVOLUCION_SINTOMAS`, `EXAMEN_PENDIENTE` o `ADHERENCIA_TRATAMIENTO` **Entonces** queda PENDIENTE y se notifica al paciente por correo.
- **CA-17.2** **Dado** una fecha sugerida de control anterior a hoy **Cuando** se crea el seguimiento **Entonces** responde `400`.
- **CA-17.3** **Dado** un seguimiento PENDIENTE **Cuando** el profesional lo cancela **Entonces** cambia de estado; si no está PENDIENTE responde `400`.
- **CA-17.4** **Dado** un administrador **Cuando** intenta ver seguimientos **Entonces** responde `403`.

**Trazabilidad:** RF-22 · RB-19, RB-20 · ADR-015 · `POST|GET /api/v1/attentions/{id}/follow-ups`, `GET /api/v1/follow-ups/{id}`, `PATCH /follow-ups/{id}/cancel` · `SEGUIMIENTO_POST_ATENCION` · `FollowUpServiceTest`, `FollowUpControllerTest`, `SeguimientoRepositoryTest`.

---

#### HU-22 · Acceso clínico de emergencia (*break-glass*)

> **Como** profesional ante una emergencia, **quiero** abrir la historia de un paciente con el que no tengo relación asistencial, dejando una justificación, **para** atenderlo sin demoras y con control posterior.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Alta | Implementada | E4 |

**Criterios de aceptación**

- **CA-22.1** **Dado** un profesional **Cuando** activa *break-glass* con un motivo de 20 a 500 caracteres **Entonces** obtiene acceso a la historia de ese paciente por **24 horas** y se audita `ACCESO_BREAK_GLASS`.
- **CA-22.2** **Dado** un motivo vacío o de menos de 20 caracteres **Cuando** se envía **Entonces** responde `400`.
- **CA-22.3** **Dado** un administrador, paciente o farmacéutico **Cuando** intenta activarlo **Entonces** responde `403`.
- **CA-22.4** **Dado** un registro de *break-glass* **Cuando** se intenta modificar o borrar en BD **Entonces** el trigger `TR_BREAK_GLASS_INMUTABILIDAD` lo impide.
- **CA-22.5** **Dado** el acceso de otro profesional **Cuando** se consulta su detalle **Entonces** responde `403`.

**Trazabilidad:** RF-17, RF-26 · RB-08, RB-11, RB-19 · ADR-007, ADR-017 · `POST /api/v1/clinical/break-glass`, `GET /break-glass/active`, `GET /break-glass/{id}` · `ACCESO_BREAK_GLASS`, `AUDITORIA` · `BreakGlassServiceTest`, `ClinicalBreakGlassControllerTest`, `BreakGlassRepositoryTest`, `EmergencyQrAndBreakGlassSecurityTest`.

---

### E5 · Recetas y farmacia

#### HU-08 · Emitir receta médica

> **Como** profesional, **quiero** emitir una receta con uno o más medicamentos, revisando antes las alergias del paciente, **para** que el tratamiento quede formulado de forma segura y completa.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Must | Alta | Implementada | E5 |

**Criterios de aceptación**

- **CA-08.1 Atomicidad**
  **Dado** una receta con varios detalles **Cuando** uno de ellos falla (por ejemplo, medicamento inactivo) **Entonces** no se guarda nada (rollback total).
- **CA-08.2 Snapshot**
  **Dado** una receta guardada **Cuando** luego cambia el nombre o presentación del medicamento en el catálogo **Entonces** el detalle conserva los valores del momento en que se recetó.
- **CA-08.3 Responsabilidad**
  **Dado** un profesional sin relación con la atención ni relación asistencial activa **Cuando** intenta recetar **Entonces** responde `403`.
- **CA-08.4 Alergias**
  **Dado** el panel de prescripción **Entonces** muestra las alergias activas o el texto «Sin alergias registradas (esto no confirma que no tenga)».
- **CA-08.5 Administrador**
  **Dado** un administrador **Cuando** intenta ver una receta **Entonces** responde `403`.

**Trazabilidad:** RF-18, RF-19 · RB-07, RB-08, RB-20, RB-23 · `POST /api/v1/prescriptions`, `GET /api/v1/prescriptions/{id}`, `GET /api/v1/catalogs/medications` · `RECETA`, `RECETA_DETALLE`, `MEDICAMENTO`, `ALERGIA` · `PrescriptionServiceTest`, `PrescriptionControllerTest`, `RecetaRepositoryTest`, `MedicamentoRepositoryTest`, `MedicationCatalogControllerTest`.

---

#### HU-23 · Dispensar medicamentos en farmacia

> **Como** farmacéutico, **quiero** buscar una receta vigente y registrar lo que entrego (incluso por partes), **para** no entregar de más ni medicamentos de recetas vencidas.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Alta | Implementada | E5 |

**Criterios de aceptación**

- **CA-23.1 Vigencia**
  **Dado** una receta con `CREATED_AT + VIGENCIA_DIAS` anterior a hoy **Cuando** se intenta dispensar **Entonces** responde `400`.
- **CA-23.2 Saldo**
  **Dado** un ítem con saldo pendiente **Cuando** se entrega una cantidad mayor al saldo **Entonces** responde `400`; si es menor o igual, se registra y el saldo baja.
- **CA-23.3 Estado calculado**
  **Dado** las entregas acumuladas **Entonces** la receta queda `PENDIENTE`, `DISPENSADA_PARCIAL` o `DISPENSADA_TOTAL`.
- **CA-23.4 Validaciones**
  **Dado** una solicitud sin medicamentos, con medicamentos duplicados o en una sede inactiva **Entonces** responde `400`.
- **CA-23.5 Lote**
  **Dado** cada ítem entregado **Entonces** guarda lote y fecha de vencimiento del lote.
- **CA-23.6 Inmutabilidad y auditoría**
  **Dado** una dispensación registrada **Entonces** no se puede modificar ni borrar (triggers) y se audita `DISPENSACION_RECETA` sin nombres de fármacos.
- **CA-23.7 Segregación**
  **Dado** un farmacéutico **Cuando** intenta acceder a historia clínica o triaje **Entonces** responde `403`.

**Trazabilidad:** RF-20 · RB-09, RB-11, RB-20 · ADR-016 · `GET /api/v1/pharmacy/prescriptions`, `GET /pharmacy/prescriptions/{id}`, `POST /pharmacy/dispensations`, `GET /pharmacy/dispensations/{id}`, `GET /pharmacy/sites` · `DISPENSACION`, `DISPENSACION_DETALLE`, `RECETA`, `RECETA_DETALLE` · `DispensationServiceTest`, `PharmacyDispensationControllerTest`, `DispensacionRepositoryTest`.

---

#### HU-24 · Paciente consulta la dispensación de su receta

> **Como** paciente, **quiero** ver qué medicamentos de mi receta ya me entregaron y cuáles faltan, **para** saber qué debo reclamar.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Could | Baja | Implementada | E5 |

**Criterios de aceptación**

- **CA-24.1** **Dado** una receta propia **Cuando** el paciente consulta su dispensación **Entonces** ve el estado, las entregas y el saldo por ítem.
- **CA-24.2** **Dado** una receta de otro paciente **Cuando** la consulta **Entonces** responde `403`/`404`.

**Trazabilidad:** RF-20 · RB-01 · `GET /api/v1/patients/me/prescriptions/{id}/dispensation` · `DISPENSACION`, `DISPENSACION_DETALLE` · `DispensationServiceTest`, `PacienteControllerTest`.

---

### E6 · Portal del paciente

#### HU-09 · Paciente consulta su información

> **Como** paciente, **quiero** ver mis citas, historia clínica, recetas y alergias, **para** conocer mi información de salud.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Must | Media | Implementada | E6 |

**Criterios de aceptación**

- **CA-09.1** **Dado** un paciente autenticado **Cuando** consulta `/patients/me/*` **Entonces** ve solo su propia información, en modo lectura.
- **CA-09.2** **Dado** un ID de recurso de otro paciente manipulado en la URL **Cuando** se consulta **Entonces** responde `403` o `404` y queda en auditoría.
- **CA-09.3** **Dado** un profesional o administrador **Cuando** llama a `/patients/me/*` **Entonces** responde `403`.

**Trazabilidad:** RF-14, RF-17 · RB-01, RB-13 · `GET /api/v1/patients/me`, `/me/appointments`, `/me/history`, `/me/prescriptions` · `PACIENTE`, `CITA`, `ATENCION`, `RECETA` · `PacienteServiceTest`, `PacienteControllerTest`, `CrossPatientIdAccessSecurityTest`.

---

#### HU-18 · Paciente reporta su evolución

> **Como** paciente, **quiero** ver mis seguimientos pendientes y reportar cómo evoluciono, **para** que mi médico tenga esa información en el próximo control.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Baja | Implementada | E6 |

**Criterios de aceptación**

- **CA-18.1** **Dado** un seguimiento propio **Cuando** el paciente reporta evolución **Entonces** se registra como insumo clínico; **nunca** se convierte en diagnóstico automático.
- **CA-18.2** **Dado** un seguimiento ajeno **Cuando** intenta reportar **Entonces** responde `403`.
- **CA-18.3** **Dado** un filtro de estado inválido **Cuando** lista sus seguimientos **Entonces** responde `400`.

**Trazabilidad:** RF-22 · RB-01, RB-12 · ADR-015 · `GET /api/v1/patients/me/follow-ups`, `POST /me/follow-ups/{id}/report` · `SEGUIMIENTO_POST_ATENCION` · `FollowUpServiceTest`, `FollowUpControllerTest`.

---

#### HU-19 · Alergias autorreportadas por el paciente

> **Como** paciente, **quiero** registrar las alergias que conozco, **para** que los profesionales las tengan en cuenta antes de recetarme.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Baja | Implementada | E6 |

**Criterios de aceptación**

- **CA-19.1** **Dado** un paciente **Cuando** registra una alergia **Entonces** queda marcada como autorreportada (distinta de la clínica, HU-07).
- **CA-19.2** **Dado** una alergia propia **Cuando** la inactiva **Entonces** se exige motivo y no hay borrado físico (`TR_ALERGIA_INMUTABILIDAD`).
- **CA-19.3** **Dado** una alergia ajena **Cuando** intenta inactivarla **Entonces** responde `403`/`404`.

**Trazabilidad:** RF-17 · RB-01, RB-08 · `GET|POST /api/v1/patients/me/allergies`, `PATCH /me/allergies/{id}/deactivate` · `ALERGIA` (migración V016) · `AllergyServiceTest`, `PatientAllergyControllerTest`.

---

#### HU-20 · Generar y revocar QR de emergencia

> **Como** paciente, **quiero** generar un QR temporal con mi resumen de salud, **para** que en una emergencia el personal vea mis alergias y medicamentos sin necesitar mi cuenta.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Media | Implementada | E6 |

**Criterios de aceptación**

- **CA-20.1** **Dado** un paciente **Cuando** genera un QR **Entonces** se crea un token aleatorio que vence en **15 minutos** y permite **máximo 3 lecturas**, con PIN opcional guardado como hash.
- **CA-20.2** **Dado** el QR **Entonces** contiene solo una URL con el token, nunca datos clínicos.
- **CA-20.3** **Dado** un QR propio activo **Cuando** el paciente lo revoca **Entonces** deja de funcionar de inmediato.
- **CA-20.4** **Dado** un QR ajeno **Cuando** intenta revocarlo **Entonces** responde `403`.

**Trazabilidad:** RF-23, RF-24 · RB-10 · ADR-010 · `POST|GET /api/v1/patients/me/emergency-qr`, `PATCH /me/emergency-qr/{id}/revoke` · `ACCESO_TEMPORAL_QR` · `EmergencyQrServiceTest`, `AccesoTemporalQrRepositoryTest`.

---

#### HU-21 · Consultar resumen de salud mediante QR

> **Como** personal de salud que atiende una emergencia, **quiero** escanear el QR del paciente, **para** ver sus alergias, medicamentos activos y antecedentes relevantes al instante.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Media | Implementada | E6 |

**Criterios de aceptación**

- **CA-21.1** **Dado** un token vigente **Cuando** se consulta (sin iniciar sesión) **Entonces** muestra un resumen mínimo con advertencia legal, cuenta una lectura y audita `ACCESO_EMERGENCIA_QR`.
- **CA-21.2** **Dado** un token vencido, revocado o con 3 lecturas **Cuando** se consulta **Entonces** se rechaza con mensaje claro.
- **CA-21.3 PIN** **Dado** un QR con PIN **Cuando** se ingresa un PIN incorrecto **Entonces** cuenta como intento fallido; al **tercer** fallo el acceso queda bloqueado y se audita `BLOQUEADO`.
- **CA-21.4** **Dado** un token inexistente **Entonces** responde `404` sin revelar información.

**Trazabilidad:** RF-23, RF-25 · RB-10, RB-11 · ADR-010 · `GET /api/v1/emergency-summary/{token}/check`, `POST /api/v1/emergency-summary/{token}` · `ACCESO_TEMPORAL_QR` (migración V017), `ALERGIA`, `RECETA` · `EmergencySummaryServiceTest`, `EmergencySummaryControllerTest`, `EmergencyQrAndBreakGlassSecurityTest`.

---

### E7 · Administración de la oferta asistencial

#### HU-10 · Administrar instituciones, sedes, especialidades, profesionales y slots

> **Como** administrador, **quiero** gestionar la oferta asistencial y generar la disponibilidad de los profesionales, **para** que los pacientes tengan cupos reales donde agendar.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Must | Alta | Implementada | E7 |

**Criterios de aceptación**

- **CA-10.1 CRUD con desactivación**
  **Dado** instituciones, sedes, especialidades o profesionales **Cuando** el administrador los crea, edita, desactiva o activa **Entonces** los cambios se auditan y nunca hay borrado físico de registros con historia.
- **CA-10.2 Generación de slots**
  **Dado** un profesional, una sede y un rango horario **Cuando** se generan slots **Entonces** no se crean slots que se solapen con otros del mismo profesional.
- **CA-10.3 Bloqueo de slots**
  **Dado** un slot LIBRE **Cuando** se bloquea **Entonces** pasa a BLOQUEADO; desbloquear solo aplica a slots BLOQUEADO. Cualquier otro estado responde `400`.
- **CA-10.4 Especialidad del profesional**
  **Dado** un slot de una especialidad que el profesional no tiene **Cuando** se genera **Entonces** responde `400`.
- **CA-10.5 Sin acceso clínico**
  **Dado** un administrador **Cuando** intenta leer historia, atenciones, recetas, triajes o seguimientos **Entonces** responde `403`.

**Trazabilidad:** RF-05, RF-06, RF-07, RF-08 · RB-15, RB-20 · ADR-007 · `/api/v1/admin/institutions|sites|specialties|professionals` (POST, GET, PUT, PATCH activate/deactivate), `POST /admin/slots/generate`, `PATCH /admin/slots/{id}/block|unblock`, `DELETE /admin/slots/{id}` · `INSTITUCION`, `SEDE`, `ESPECIALIDAD`, `PROFESIONAL`, `DISPONIBILIDAD_SLOT` · `AdminCatalogServiceTest`, `AdminProfessionalServiceTest`, `SlotGeneratorServiceTest`, `AdminInstitutionControllerTest`, `AdminSiteControllerTest`, `AdminSpecialtyControllerTest`, `AdminProfessionalControllerTest`, `AdminSlotControllerTest`, `AdminClinicalAccessMetaSecurityTest`, E2E `03-admin-security-isolation.spec.js`.

---

#### HU-36 · Registrar ausencias médicas

> **Como** profesional o administrador, **quiero** registrar los periodos en que un profesional no estará disponible, **para** que la agenda refleje su ausencia.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Could | Media | Implementada | E7 |

**Criterios de aceptación**

- **CA-36.1** **Dado** un rango válido **Cuando** se registra la ausencia **Entonces** se guarda y se audita `AUSENCIA_MEDICA_REGISTRADA`.
- **CA-36.2** **Dado** una fecha de fin anterior a la de inicio **Entonces** responde `409`.
- **CA-36.3** **Dado** una ausencia que se cruza con otra activa del mismo profesional **Entonces** responde `409`.

**Trazabilidad:** RF-08 · RB-11 · ADR-025 · `POST /api/v1/affiliations/absences`, `GET /affiliations/professionals/{id}/absences` · `AUSENCIA_MEDICA` · `AffiliationServiceTest`, `AffiliationControllerTest`.

---

#### HU-42 · Consultar y exportar citas (administración)

> **Como** administrador, **quiero** listar y exportar las citas con filtros, **para** hacer seguimiento operativo sin ver información clínica.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Could | Baja | Implementada | E7 |

**Criterios de aceptación**

- **CA-42.1** **Dado** un administrador **Cuando** consulta citas **Entonces** obtiene un listado paginado sin motivos, diagnósticos ni notas clínicas.
- **CA-42.2** **Dado** los mismos filtros **Cuando** exporta **Entonces** descarga un archivo CSV compatible con Excel con las mismas filas.
- **CA-42.3** **Dado** un rol distinto de administrador **Entonces** responde `403`.

**Trazabilidad:** RF-14, RF-30 · RB-20 · `GET /api/v1/admin/appointments`, `GET /admin/appointments/export` · `CITA` · `AdminAppointmentServiceTest`, `AdminAppointmentControllerTest`.

---

### E8 · Auditoría y reportes

#### HU-11 · Auditoría de eventos sensibles

> **Como** responsable de seguridad, **quiero** que el sistema registre cada evento sensible, **para** saber quién hizo qué, cuándo y desde dónde.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Must | Media | Implementada | E8 |

**Criterios de aceptación**

- **CA-11.1** **Dado** un login (éxito o fallo), acceso a historia, creación o cierre de atención, receta, cancelación, inasistencia o cambio administrativo **Entonces** se inserta un registro con usuario, acción, tipo de recurso, ID, resultado, IP y fecha.
- **CA-11.2** **Dado** un registro de auditoría **Entonces** no contiene síntomas, diagnósticos, notas, nombres de medicamentos ni secretos.
- **CA-11.3** **Dado** la tabla `AUDITORIA` **Cuando** se intenta `UPDATE` o `DELETE` **Entonces** el motor lo impide (insert-only).
- **CA-11.4** **Dado** un fallo al auditar un evento secundario **Entonces** se registra en log sin exponer datos sensibles.

**Trazabilidad:** RF-26 · RB-11, RB-25 · ADR-011, ADR-028 · (transversal) · `AUDITORIA` (migraciones V002, V022) · `AuditoriaServiceTest`, `AuditoriaRepositoryTest`, `SecurityLogSanitizationTest`.

---

#### HU-39 · Reportes operativos agregados

> **Como** administrador, **quiero** indicadores de citas, triajes, farmacia y *break-glass* por rango de fechas, **para** tomar decisiones sin acceder a datos personales.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Could | Media | Implementada | E8 |

**Criterios de aceptación**

- **CA-39.1** **Dado** un rango `fechaDesde`–`fechaHasta` (Bogotá) **Cuando** se consulta un reporte **Entonces** devuelve solo conteos y tasas (`COUNT`, `SUM`, `GROUP BY`): citas por estado, especialidad y sede; triajes por nivel I–V y cortes de emergencia; recetas por estado de dispensación.
- **CA-39.2** **Dado** cualquier reporte **Entonces** no incluye IDs de pacientes ni datos clínicos individuales.
- **CA-39.3** **Dado** un rol distinto de administrador **Entonces** responde `403`.

**Trazabilidad:** RF-30 · RB-20 · ADR-018, ADR-019 · `GET /api/v1/admin/reports/operational|appointments|triage` · vistas agregadas sobre `CITA`, `TRIAJE`, `RECETA`, `DISPENSACION`, `ACCESO_BREAK_GLASS` · `ReporteServiceTest`, `ReporteRepositoryTest`, `AdminReportControllerTest`.

---

#### HU-40 · Visor de auditoría

> **Como** administrador, **quiero** consultar y filtrar la bitácora de auditoría, **para** revisar accesos sospechosos.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Could | Baja | Implementada | E8 |

**Criterios de aceptación**

- **CA-40.1** **Dado** filtros de fecha, acción y resultado **Cuando** se consulta **Entonces** devuelve resultados paginados con correo del usuario, acción, recurso, resultado, IP y fecha.
- **CA-40.2** **Dado** un número de página negativo **Entonces** responde `400`.
- **CA-40.3** **Dado** un rol distinto de administrador **Entonces** responde `403`.

**Trazabilidad:** RF-26 · RB-25 · ADR-019 · `GET /api/v1/admin/audit` · `AUDITORIA`, `USUARIO` · `AdminAuditControllerTest`, `AuditoriaRepositoryTest`.

---

### E9 · Urgencias

#### HU-25 · Admisión a urgencias (incluye pacientes no identificados)

> **Como** enfermera o profesional de urgencias, **quiero** registrar el ingreso de un paciente aunque no tenga cita, cuenta ni documento, **para** que nadie se quede sin atención por un trámite.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Alta | Implementada | E9 |

**Criterios de aceptación**

- **CA-25.1** **Dado** una sede, un medio de llegada y un motivo (máx. 500 caracteres) **Cuando** se admite a un paciente registrado **Entonces** se crea un `EPISODIO_ATENCION` de tipo URGENCIA sin exigir cita.
- **CA-25.2 NN** **Dado** un paciente sin identificar **Cuando** se admite con identidad provisional **Entonces** se genera un código opaco aleatorio, **sin** inventar documento ni nombre.
- **CA-25.3** **Dado** un paciente sin afiliación a EPS o con afiliación desconocida **Entonces** el ingreso **no** se bloquea.
- **CA-25.4** **Dado** un episodio recién admitido **Entonces** su triaje queda `PENDIENTE_VALORACION`, nunca un nivel automático.
- **CA-25.5** **Dado** un paciente o farmacéutico **Cuando** intenta admitir **Entonces** responde `403`.

**Trazabilidad:** ADR-022, ADR-023, ADR-025, ADR-026, ADR-027 · `POST /api/v1/emergency/admissions`, `GET /emergency/admissions/{id}` · `EPISODIO_ATENCION`, `INGRESO_URGENCIA`, `IDENTIDAD_PROVISIONAL` (migración V018) · `EmergencyServiceTest`, `EmergencyControllerTest`, E2E `04-nursing-urgency-triage-queue.spec.js`.

---

#### HU-26 · Reconciliar identidad provisional

> **Como** personal de urgencias, **quiero** vincular un paciente NN con su expediente real cuando se confirme su identidad, **para** unificar su historia sin perder lo registrado.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Media | Implementada | E9 |

**Criterios de aceptación**

- **CA-26.1** **Dado** un episodio con identidad provisional activa y un paciente civil verificado **Cuando** se reconcilia **Entonces** el episodio queda vinculado al paciente en una transacción auditada y el historial previo se conserva intacto.
- **CA-26.2** **Dado** un episodio sin identidad provisional, o una ya reconciliada **Entonces** responde `400`/`409`.

**Trazabilidad:** ADR-023 · `POST /api/v1/emergency/episodes/{id}/reconcile` · `IDENTIDAD_PROVISIONAL`, `EPISODIO_ATENCION`, `PACIENTE` · `EmergencyServiceTest`.

---

#### HU-27 · Valoración de triaje presencial y reevaluación

> **Como** enfermera o profesional, **quiero** clasificar al paciente de urgencias en niveles I–V con signos vitales y reevaluarlo cuando cambie, **para** priorizar la atención según la Resolución 5596 de 2015.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Alta | Implementada | E9 |

**Criterios de aceptación**

- **CA-27.1** **Dado** un episodio abierto **Cuando** se registra una valoración con nivel, motivo (máx. 500) y signos vitales dentro de rango biológico (por ejemplo FC 20–300, FR 4–80) **Entonces** se guarda firmada por quien la hizo.
- **CA-27.2** **Dado** signos vitales fuera de rango **Entonces** responde `400`.
- **CA-27.3 Append-only** **Dado** una reevaluación **Entonces** se crea un **nuevo** registro en `VALORACION_TRIAJE`; los anteriores no cambian.
- **CA-27.4** **Dado** un episodio cerrado o egresado **Cuando** se intenta valorar **Entonces** responde `409`.
- **CA-27.5** **Dado** un administrador **Cuando** consulta las notas de triaje **Entonces** responde `403`.

**Trazabilidad:** RF-09, RF-10 · RB-08 · ADR-027 · `POST|GET /api/v1/emergency/episodes/{id}/triage-assessments` · `VALORACION_TRIAJE`, `EPISODIO_ATENCION` · `EmergencyServiceTest`, `EmergencyControllerTest`.

---

#### HU-28 · Cola de urgencias, equipo asistencial y cierre de episodio

> **Como** personal de urgencias, **quiero** ver la cola de pacientes por prioridad, asignar el equipo que los atiende y cerrar el episodio, **para** coordinar el servicio.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Media | Implementada | E9 |

**Criterios de aceptación**

- **CA-28.1** **Dado** episodios abiertos en una sede **Cuando** se consulta la cola **Entonces** se listan ordenados por prioridad de triaje y tiempo de espera.
- **CA-28.2** **Dado** un episodio **Cuando** se asigna un profesional al equipo **Entonces** queda en `ASIGNACION_ASISTENCIAL` y se audita.
- **CA-28.3** **Dado** un episodio ya cerrado **Cuando** se intenta cerrar otra vez **Entonces** responde `409`.

**Trazabilidad:** ADR-022, ADR-026 · `GET /api/v1/emergency/queue[/{siteId}]`, `POST /emergency/episodes/{id}/care-team`, `POST /emergency/episodes/{id}/close` · `EPISODIO_ATENCION`, `ASIGNACION_ASISTENCIAL` · `EmergencyServiceTest`, `EmergencyControllerTest`.

---

### E10 · Hospitalización

#### HU-29 · Asignar y trasladar camas sin sobreasignación

> **Como** enfermera o profesional, **quiero** asignar una cama a un paciente y trasladarlo cuando sea necesario, **para** que nunca haya dos pacientes en la misma cama.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Alta | Implementada | E10 |

**Criterios de aceptación**

- **CA-29.1** **Dado** una cama disponible **Cuando** se asigna a un episodio **Entonces** se abre una ocupación y se registra un movimiento.
- **CA-29.2 Concurrencia** **Dado** dos asignaciones simultáneas sobre la misma cama **Entonces** una gana y la otra recibe `409`; Oracle garantiza una sola ocupación abierta por cama.
- **CA-29.3** **Dado** un traslado a la misma cama de origen, o de un paciente sin cama activa **Entonces** responde `409`.
- **CA-29.4** **Dado** un traslado válido **Entonces** cierra la ocupación anterior, abre la nueva y agrega un movimiento (append-only), todo en una transacción.

**Trazabilidad:** ADR-024 · `POST /api/v1/hospital/episodes/{id}/beds/assign|transfer`, `GET /hospital/episodes/{id}/movements` · `CAMA_HOSPITALARIA`, `OCUPACION_CAMA`, `MOVIMIENTO_PACIENTE`, `HABITACION_SALA`, `AREA_HOSPITALARIA` (migración V019) · `HospitalServiceTest`, `HospitalControllerTest`, E2E `05-hospital-beds-census-movements.spec.js`.

---

#### HU-30 · Registrar procedimientos hospitalarios

> **Como** profesional, **quiero** programar procedimientos (por ejemplo, en quirófano) y actualizar su estado, **para** llevar el control de lo realizado durante la hospitalización.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Could | Media | Implementada | E10 |

**Criterios de aceptación**

- **CA-30.1** **Dado** un episodio, una sala y un profesional existentes **Cuando** se registra el procedimiento **Entonces** queda guardado con su estado inicial.
- **CA-30.2** **Dado** una sala o profesional inexistente **Entonces** responde `404`.
- **CA-30.3** **Dado** un rol distinto de profesional **Cuando** registra o cambia el estado **Entonces** responde `403`.

**Trazabilidad:** ADR-024 · `POST /api/v1/hospital/episodes/{id}/procedures`, `PATCH /hospital/procedures/{id}/status`, `GET /hospital/episodes/{id}/procedures` · `PROCEDIMIENTO_HOSPITALARIO` · `HospitalServiceTest`, `HospitalControllerTest`.

---

#### HU-31 · Egreso hospitalario

> **Como** médico, **quiero** dar de alta a un paciente, **para** cerrar su episodio y liberar la cama.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Media | Implementada | E10 |

**Criterios de aceptación**

- **CA-31.1** **Dado** un episodio activo **Cuando** el médico registra el egreso **Entonces** se crea `EGRESO_HOSPITALARIO`, se cierra la ocupación de cama y se audita.
- **CA-31.2** **Dado** un episodio ya egresado **Entonces** responde `409`.
- **CA-31.3** **Dado** enfermería u otro rol no médico **Entonces** responde `403`.

**Trazabilidad:** ADR-024 · `POST /api/v1/hospital/episodes/{id}/discharge` · `EGRESO_HOSPITALARIO`, `OCUPACION_CAMA`, `EPISODIO_ATENCION` · `HospitalServiceTest`.

---

#### HU-32 · Censo y estado de camas

> **Como** personal asistencial o administrador, **quiero** ver el censo de camas por sede y cambiar su estado (por ejemplo, en limpieza), **para** saber cuántas hay disponibles.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Should | Baja | Implementada | E10 |

**Criterios de aceptación**

- **CA-32.1** **Dado** una sede **Cuando** se consulta el censo **Entonces** muestra camas totales, ocupadas y disponibles.
- **CA-32.2** **Dado** una cama OCUPADA con un paciente activo **Cuando** se intenta cambiar su estado **Entonces** responde `409`; los cambios válidos se auditan como `ESTADO_CAMA_ACTUALIZADO`.
- **CA-32.3** **Dado** un paciente o farmacéutico **Entonces** responde `403`.

**Trazabilidad:** ADR-024 · `GET /api/v1/hospital/census/{sedeId}`, `GET /hospital/beds/{sedeId}`, `PATCH /hospital/beds/{id}/status` · `CAMA_HOSPITALARIA`, `OCUPACION_CAMA` · `HospitalServiceTest`, `HospitalControllerTest`.

---

### E11 · Afiliaciones y representación legal

#### HU-33 · Importar afiliaciones EPS desde Excel en dos pasos

> **Como** administrador, **quiero** cargar el padrón de afiliados de una EPS desde Excel, revisando antes los errores, **para** actualizar la cobertura de muchos pacientes sin riesgo.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Could | Alta | Implementada | E11 |

**Criterios de aceptación**

- **CA-33.1 Plantilla** **Dado** un administrador **Cuando** descarga la plantilla **Entonces** recibe un `.xlsx` con las columnas esperadas.
- **CA-33.2 Preview** **Dado** un archivo válido **Cuando** se sube **Entonces** se valida en memoria y se devuelve un reporte por fila (válida o con error), sin escribir afiliaciones todavía.
- **CA-33.3 Límites** **Dado** un archivo vacío, sin hojas, de más de **10 MB** o más de **5 000 filas** **Entonces** responde `409`.
- **CA-33.4 Validación de valores** **Dado** un tipo de documento fuera de {CC, TI, RC, CE, PA, PE, PPT} o un régimen fuera de {CONTRIBUTIVO, SUBSIDIADO, ESPECIAL, NO_ASEGURADO} **Entonces** la fila queda con error.
- **CA-33.5 Commit** **Dado** un lote en estado PREVIEW **Cuando** se confirma en modo `ATOMIC_ALL` con alguna fila con error **Entonces** responde `409` y no aplica nada; un lote ya procesado también responde `409`.
- **CA-33.6** **Dado** la importación **Entonces** **no** crea cuentas web ni contraseñas.

**Trazabilidad:** ADR-025 · `GET /api/v1/affiliations/eps`, `GET /affiliations/template`, `POST /affiliations/upload-preview`, `POST /affiliations/batches/{id}/commit` · `ENTIDAD_EPS`, `LOTE_IMPORTACION_EPS`, `DETALLE_IMPORTACION_EPS`, `AFILIACION_PACIENTE` (migración V020) · `AffiliationServiceTest`, `AffiliationControllerTest`, E2E `06-affiliations-eps-import-scheduling.spec.js`.

---

#### HU-34 · Consultar afiliación de un paciente

> **Como** personal asistencial o administrador, **quiero** consultar la EPS y el régimen de un paciente por su documento, **para** orientar los trámites, sin que eso bloquee su atención.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Could | Baja | Implementada | E11 |

**Criterios de aceptación**

- **CA-34.1** **Dado** un tipo y número de documento **Cuando** se consulta **Entonces** devuelve la afiliación vigente o indica que no hay registro.
- **CA-34.2** **Dado** un paciente o farmacéutico **Entonces** responde `403`.

**Trazabilidad:** ADR-025 · `GET /api/v1/affiliations/patients/{tipoDocumento}/{numeroDocumento}` · `AFILIACION_PACIENTE`, `ENTIDAD_EPS` · `AffiliationServiceTest`.

---

#### HU-35 · Representación legal de menores

> **Como** tutor (o administrador), **quiero** registrar mi vínculo de representación legal con un menor, **para** gestionar sus citas e información.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Could | Media | Implementada | E11 |

**Criterios de aceptación**

- **CA-35.1** **Dado** un menor y un representante existentes y distintos **Cuando** se registra el vínculo **Entonces** queda guardado y se audita `REPRESENTACION_LEGAL_REGISTRADA`.
- **CA-35.2** **Dado** que menor y representante son la misma persona **Entonces** responde `409`.
- **CA-35.3** **Dado** un tutor **Cuando** lista sus representados **Entonces** ve solo los menores vinculados a él.

**Trazabilidad:** RF-04 · RB-01, RB-11 · `POST /api/v1/affiliations/guardians`, `GET /affiliations/guardians/{id}/minors` · `REPRESENTACION_LEGAL`, `PACIENTE` · `AffiliationServiceTest`.

---

### E12 · Operación hospitalaria

#### HU-37 · Dashboard operativo y alertas automáticas

> **Como** coordinador asistencial o administrador, **quiero** ver en tiempo real la ocupación, la cola por nivel de triaje y alertas automáticas, **para** reaccionar ante saturación o demoras.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Could | Media | Implementada | E12 |

**Criterios de aceptación**

- **CA-37.1** **Dado** una sede **Cuando** se consulta el dashboard **Entonces** muestra tasa de ocupación de camas, episodios por nivel de triaje y tiempos promedio de espera.
- **CA-37.2 Saturación** **Dado** una ocupación de camas ≥ **85 %** **Entonces** se crea una alerta `SATURACION_CAMAS` de severidad CRÍTICA.
- **CA-37.3 Demora** **Dado** pacientes en nivel II con espera promedio > **30 minutos** **Entonces** se crea una alerta `DEMORA_TRIAJE_II` de severidad ALTA.
- **CA-37.4 Sin duplicados** **Dado** una alerta activa del mismo tipo en la sede **Entonces** no se crea otra (índice único `UQ_ALERTA_SEDE_TIPO_ACTIVA`).
- **CA-37.5** **Dado** una alerta activa **Cuando** se reconoce **Entonces** cambia de estado y se audita `ALERTA_OPERATIVA_RECONOCIDA`.

**Trazabilidad:** RF-28 · ADR-024, ADR-027 · `GET /api/v1/operational/dashboard`, `GET /operational/alerts`, `POST /operational/alerts/{id}/acknowledge` · `ALERTA_OPERATIVA` (migración V021), `OCUPACION_CAMA`, `VALORACION_TRIAJE` · `OperationalAnalyticsServiceTest`, `OperationalControllerTest`.

---

#### HU-38 · QR de seguimiento intrahospitalario para familiares

> **Como** personal asistencial, **quiero** generar un QR de seguimiento para la familia del paciente, **para** que sepan dónde está y su estado general sin llamar a la recepción.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Could | Baja | Implementada | E12 |

**Criterios de aceptación**

- **CA-38.1** **Dado** un episodio **Cuando** se genera el QR **Entonces** se crea un token opaco con vigencia de **7 días**; si ya existe uno vigente, se reutiliza.
- **CA-38.2** **Dado** un token ACTIVO y vigente **Cuando** se consulta **Entonces** muestra información operativa (ubicación y estado) y **ningún** dato clínico.
- **CA-38.3** **Dado** un token inactivo o vencido **Entonces** responde `404` con mensaje genérico.

**Trazabilidad:** ADR-028 · `POST /api/v1/operational/episodes/{id}/tracking-qr`, `GET /operational/tracking-qr/{token}` · `SEGUIMIENTO_INTRAHOSPITALARIO_QR` · `OperationalAnalyticsServiceTest`, `OperationalControllerTest`.

---

### E13 · Asistente del sistema

#### HU-41 · Asistente de orientación

> **Como** usuario, **quiero** preguntarle al asistente cómo usar MediTriaje (triaje, citas, farmacia, QR), **para** resolver dudas sin llamar a nadie.

| Prioridad | Complejidad | Estado | Épica |
|---|---|---|---|
| Could | Media | Implementada | E13 |

**Criterios de aceptación**

- **CA-41.1 Alarma primero** **Dado** un mensaje con términos de alarma (dolor torácico, ahogo severo, pérdida de conocimiento, sangrado masivo) **Entonces** la respuesta prioriza acudir a urgencias o llamar al 123.
- **CA-41.2 Sin diagnóstico** **Dado** cualquier pregunta **Entonces** el asistente nunca diagnostica ni recomienda medicamentos.
- **CA-41.3** **Dado** una pregunta sobre un tema conocido **Entonces** responde de forma determinista con sugerencias de acción (por ejemplo `#/patient/triage`).
- **CA-41.4** **Dado** cualquier respuesta **Entonces** incluye el aviso «Soy un asistente de orientación para MediTriaje 2.0. No sustituyo la valoración médica profesional.»

**Trazabilidad:** RF-27 · RB-12 · ADR-018 · `POST /api/v1/assistant/chat` · (sin tablas propias) · `AssistantServiceTest`, `AssistantControllerTest`.

---

## 6. Matriz de trazabilidad

### 7.1 Historia → requisito, reglas, decisiones y pruebas

| HU | Título corto | RF | RB | ADR | Prioridad | Estado |
|---|---|---|---|---|---|---|
| HU-01 | Registro e inicio de sesión | RF-01–04 | 02, 11, 24 | 002, 013, 020 | Must | Implementada |
| HU-02 | Triaje web | RF-09–11 | 12, 22 | 009 | Must | Implementada |
| HU-03 | Disponibilidad | RF-08, 12 | 04, 12 | 005, 006 | Must | Implementada |
| HU-04 | Agendar cita | RF-13 | 04, 05, 17, 18 | 006 | Must | Implementada |
| HU-05 | Cancelar / inasistencia | RF-15 | 11, 16, 17 | 006 | Must | Implementada |
| HU-06 | Agenda profesional | RF-14 | 02 | — | Must | Implementada |
| HU-07 | Atención clínica | RF-16, 17 | 06, 08, 19, 21 | 007, 008 | Must | Implementada |
| HU-08 | Receta | RF-18, 19 | 07, 08, 20, 23 | — | Must | Implementada |
| HU-09 | Paciente consulta | RF-14, 17 | 01, 13 | — | Must | Implementada |
| HU-10 | Administración | RF-05–08 | 15, 20 | 007 | Must | Implementada |
| HU-11 | Auditoría | RF-26 | 11, 25 | 011, 028 | Must | Implementada |
| HU-12 | Recuperar contraseña | RF-02 | 11 | 014, 020 | Should | Implementada |
| HU-13 | MFA | RF-02 | — | 014 | Should | Implementada |
| HU-14 | Cambiar contraseña | RF-02 | — | 002 | Should | Implementada |
| HU-15 | Reprogramar cita | RF-15 | 05, 16, 17 | 006 | Should | Implementada |
| HU-16 | Correos de citas | RF-29 | — | 015, 020 | Could | Implementada |
| HU-17 | Seguimiento (profesional) | RF-22 | 19, 20 | 015 | Should | Implementada |
| HU-18 | Reporte de evolución | RF-22 | 01, 12 | 015 | Should | Implementada |
| HU-19 | Alergias autorreportadas | RF-17 | 01, 08 | — | Should | Implementada |
| HU-20 | Generar QR emergencia | RF-23, 24 | 10 | 010 | Should | Implementada |
| HU-21 | Leer QR emergencia | RF-23, 25 | 10, 11 | 010 | Should | Implementada |
| HU-22 | Break-glass | RF-17, 26 | 08, 11, 19 | 007, 017 | Should | Implementada |
| HU-23 | Dispensación | RF-20 | 09, 11, 20 | 016 | Should | Implementada |
| HU-24 | Paciente ve dispensación | RF-20 | 01 | 016 | Could | Implementada |
| HU-25 | Admisión urgencias | — | — | 022, 023, 025–027 | Should | Implementada |
| HU-26 | Reconciliar NN | — | — | 023 | Should | Implementada |
| HU-27 | Triaje presencial | RF-09, 10 | 08 | 027 | Should | Implementada |
| HU-28 | Cola y cierre de episodio | — | — | 022, 026 | Should | Implementada |
| HU-29 | Camas | — | — | 024 | Should | Implementada |
| HU-30 | Procedimientos | — | — | 024 | Could | Implementada |
| HU-31 | Egreso | — | — | 024 | Should | Implementada |
| HU-32 | Censo de camas | — | — | 024 | Should | Implementada |
| HU-33 | Importar EPS | — | — | 025 | Could | Implementada |
| HU-34 | Consultar afiliación | — | — | 025 | Could | Implementada |
| HU-35 | Representación legal | RF-04 | 01, 11 | — | Could | Implementada |
| HU-36 | Ausencias médicas | RF-08 | 11 | 025 | Could | Implementada |
| HU-37 | Dashboard y alertas | RF-28 | — | 024, 027 | Could | Implementada |
| HU-38 | QR de seguimiento | — | — | 028 | Could | Implementada |
| HU-39 | Reportes | RF-30 | 20 | 018, 019 | Could | Implementada |
| HU-40 | Visor de auditoría | RF-26 | 25 | 019 | Could | Implementada |
| HU-41 | Asistente | RF-27 | 12 | 018 | Could | Implementada |
| HU-42 | Citas (admin) | RF-14, 30 | 20 | — | Could | Implementada |

### 7.2 Historia → base de datos (para la asignatura de Base de Datos)

| Tabla Oracle | Historias que la usan | Migración |
|---|---|---|
| `USUARIO`, `ROL`, `USUARIO_ROL`, `REFRESH_TOKEN` | HU-01, HU-12, HU-13, HU-14 | V001–V002, V005 |
| `PACIENTE`, `CONSENTIMIENTO` | HU-01, HU-09, HU-35 | V003 |
| `INSTITUCION`, `SEDE`, `ESPECIALIDAD`, `PROFESIONAL` | HU-10 | V004, V015 |
| `DISPONIBILIDAD_SLOT`, `CITA` | HU-03, HU-04, HU-05, HU-06, HU-15, HU-42 | V006, V020 |
| `SINTOMA`, `REGLA_TRIAJE`, `TRIAJE`, `TRIAJE_SINTOMA` | HU-02, HU-39 | V007 |
| `ATENCION`, `SIGNO_VITAL`, `ATENCION_ENMIENDA`, `DIAGNOSTICO_CIE` | HU-07, HU-09 | V008 |
| `MEDICAMENTO`, `RECETA`, `RECETA_DETALLE` | HU-08, HU-09, HU-23, HU-24 | V009 |
| `CODIGO_VERIFICACION`, `MFA_BACKUP_CODE` | HU-12, HU-13 | V010 |
| `SEGUIMIENTO_POST_ATENCION`, `RECORDATORIO_CITA` | HU-16, HU-17, HU-18 | V011 |
| `ACCESO_TEMPORAL_QR` | HU-20, HU-21 | V012, V017 |
| `DISPENSACION`, `DISPENSACION_DETALLE` | HU-23, HU-24, HU-39 | V013 |
| `ACCESO_BREAK_GLASS` | HU-22, HU-39 | V014 |
| `ALERGIA` | HU-07, HU-08, HU-19, HU-21 | V016 |
| `EPISODIO_ATENCION`, `INGRESO_URGENCIA`, `IDENTIDAD_PROVISIONAL`, `VALORACION_TRIAJE`, `ASIGNACION_ASISTENCIAL` | HU-25, HU-26, HU-27, HU-28 | V018 |
| `AREA_HOSPITALARIA`, `HABITACION_SALA`, `CAMA_HOSPITALARIA`, `OCUPACION_CAMA`, `MOVIMIENTO_PACIENTE`, `PROCEDIMIENTO_HOSPITALARIO`, `EGRESO_HOSPITALARIO` | HU-29, HU-30, HU-31, HU-32 | V019 |
| `ENTIDAD_EPS`, `AFILIACION_PACIENTE`, `LOTE_IMPORTACION_EPS`, `DETALLE_IMPORTACION_EPS`, `REPRESENTACION_LEGAL`, `AUSENCIA_MEDICA` | HU-33, HU-34, HU-35, HU-36 | V020 |
| `ALERTA_OPERATIVA`, `SEGUIMIENTO_INTRAHOSPITALARIO_QR` | HU-37, HU-38 | V021 |
| `AUDITORIA` | HU-11, HU-40 y todas las que auditan | V002, V022 |

### 7.3 Historia → pruebas de extremo a extremo (Playwright)

| Prueba E2E | Historias cubiertas |
|---|---|
| `01-patient-triage-appointment.spec.js` | HU-01, HU-02, HU-03, HU-04 |
| `02-professional-attention-recipe.spec.js` | HU-06, HU-07, HU-08 |
| `03-admin-security-isolation.spec.js` | HU-10, HU-09 (aislamiento), RB-20 |
| `04-nursing-urgency-triage-queue.spec.js` | HU-25, HU-27, HU-28 |
| `05-hospital-beds-census-movements.spec.js` | HU-29, HU-32 |
| `06-affiliations-eps-import-scheduling.spec.js` | HU-33, HU-36 |

## 7. Requisitos no funcionales transversales

Aplican a **todas** las historias; una HU no se considera terminada si incumple alguno.

| Código | Requisito | Verificación |
|---|---|---|
| RNF-SEG-01 | La autorización vive en el backend; ocultar un botón no es seguridad (RB-03, RB-13). | `AdminClinicalAccessMetaSecurityTest`, `CrossPatientIdAccessSecurityTest` |
| RNF-SEG-02 | Identificadores públicos opacos (`PUBLIC_ID`), nunca IDs secuenciales en la API (ADR-003). | Revisión de DTOs |
| RNF-SEG-03 | Logs y auditoría sin datos clínicos, secretos ni correos completos. | `SecurityLogSanitizationTest` |
| RNF-SEG-04 | Protección CSRF con cookies `SameSite=Strict` y cabecera. | `CsrfHeaderFilterTest` |
| RNF-SEG-05 | En producción, la app no arranca sin secretos configurados. | `ProdSecretsFailFastTest` |
| RNF-DAT-01 | Fechas en UTC en BD y presentación en `America/Bogota` (ADR-005). | Pruebas de servicio |
| RNF-DAT-02 | Restricciones críticas duplicadas en Oracle (`CHECK`, `UNIQUE`, FK, triggers) (RB-15). | `OracleIntegrationTest` |
| RNF-DAT-03 | Registros clínicos sin borrado físico (RB-08). | Triggers de inmutabilidad |
| RNF-OPE-01 | Cambios de esquema solo por migraciones versionadas (ADR-004). | `database/migrations/V001…V022` |
| RNF-OPE-02 | CI ejecuta pruebas, análisis de dependencias y detección de secretos en cada PR. | `.github/workflows/ci.yml`, `e2e.yml` |

## 8. Definition of Ready y Definition of Done

**Definition of Ready (una HU puede entrar al sprint cuando…)**

- [ ] Tiene formato *Como / Quiero / Para* y está asignada a una épica.
- [ ] Tiene criterios de aceptación en Gherkin, incluido al menos un caso de acceso denegado.
- [ ] Están identificadas las reglas de negocio (RB) y decisiones (ADR) que aplica.
- [ ] Están identificadas las tablas afectadas y si requiere migración nueva.
- [ ] Tiene prioridad y complejidad acordadas por el equipo.

**Definition of Done (una HU está terminada cuando…)**

- [ ] Cada criterio de aceptación tiene al menos una prueba automatizada que pasa.
- [ ] Endpoints protegidos con `@PreAuthorize` y validación de pertenencia en el servicio.
- [ ] Migración Flyway creada (si aplica) con constraints y triggers en Oracle.
- [ ] Eventos sensibles auditados sin contenido clínico.
- [ ] Documentación actualizada: esta matriz, `API.md` y, si cambia el modelo, `MER.md` y `MODELO_RELACIONAL.md`.
- [ ] CI en verde y PR revisado por otro integrante antes de unir a `develop`.

## 9. Pendientes y riesgos

| # | Tema | Detalle | Acción sugerida |
|---|---|---|---|
| 1 | ADR en estado PROPUESTO | Varios ADR (011, 012, 014–028) siguen como "PROPUESTO" aunque ya están implementados. | Revisarlos y marcarlos como ACEPTADO en `DECISIONES.md`. |
| 2 | RB-09 y RB-10 marcadas como Fase 2 | En `REGLAS_NEGOCIO.md` siguen como "fuera de alcance", pero HU-20, HU-21 y HU-23 ya las implementan. | Actualizar `REGLAS_NEGOCIO.md` y agregar reglas RB-26 en adelante para urgencias, camas y afiliaciones. |
| 3 | Sin RF para el módulo hospitalario | HU-25 a HU-34 y HU-38 solo se justifican por ADR; el Documento Maestro llega hasta RF-30. | Agregar RF-31 en adelante (urgencias, camas, afiliaciones) al Documento Maestro. |
| 4 | `MVP.md` sección 3 | Queda desactualizada frente a este documento. | Reemplazarla por un enlace a `HISTORIAS_DE_USUARIO.md`. |
| 5 | Casos de uso | `CASOS_DE_USO.md` solo cubre CU-01 a CU-11. | Agregar CU-12 en adelante para las HU nuevas más complejas (HU-22, HU-23, HU-25, HU-29, HU-33). |
| 6 | Reglas de triaje | Siguen siendo un prototipo sin validación clínica (ADR-009). | Mantener el aviso legal y documentar la validación pendiente. |
| 7 | Complejidad | La columna de complejidad es una estimación inicial. | Convertirla a puntos de historia en la próxima planeación. |
