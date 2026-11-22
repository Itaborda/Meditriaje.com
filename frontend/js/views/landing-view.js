/**
 * MediTriaje 2.0 — Vista de Landing Page (landing-view.js)
 * Diseño profesional inspirado en Radix UI y shadcn/ui.
 * Totalmente accesible, responsivo y basado en tokens CSS de MediTriaje 2.0.
 */

import { auth } from '../auth.js';
import { motion } from '../motion.js';
import { ui } from '../ui.js';

export async function landingView(container) {
  const isAuth = auth.isAuthenticated;
  let userDashboardUrl = '#/patient/dashboard';
  let userDashboardLabel = 'Ir a mi panel de paciente';

  if (auth.isAdmin) {
    userDashboardUrl = '#/admin/dashboard';
    userDashboardLabel = 'Ir al panel de administración';
  } else if (auth.isProfesional) {
    userDashboardUrl = '#/professional/agenda';
    userDashboardLabel = 'Ir a mi agenda médica';
  } else if (auth.isFarmaceutico) {
    userDashboardUrl = '#/pharmacy/dispensation';
    userDashboardLabel = 'Ir a dispensación farmacéutica';
  } else if (auth.isEnfermeria) {
    userDashboardUrl = '#/nursing/dashboard';
    userDashboardLabel = 'Ir al panel de enfermería';
  }

  container.innerHTML = `
    <div class="landing-ambient-wrapper" data-motion-custom="true">
      <!-- 1. Hero Stage (Referencia Visual MediTriaje 2.0) -->
      <section id="landing-top" class="landing-hero-stage">
        <!-- Arte orgánico de fondo + línea ECG vectorial animada -->
        <div class="hero-bg-art" aria-hidden="true">
          <div class="hero-bg-glow hero-bg-glow--left"></div>
          <div class="hero-bg-glow hero-bg-glow--right"></div>
          <svg class="hero-curve-svg" viewBox="0 0 720 640" preserveAspectRatio="none">
            <defs>
              <linearGradient id="heroWaveGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#0F766E" stop-opacity="0.92"/>
                <stop offset="55%" stop-color="#0D9488" stop-opacity="0.86"/>
                <stop offset="100%" stop-color="#2DD4BF" stop-opacity="0.55"/>
              </linearGradient>
            </defs>
            <path d="M275 0 C175 110, 210 235, 345 315 C460 385, 425 545, 515 640 L720 640 L720 0 Z" fill="url(#heroWaveGrad)"/>
          </svg>
          <svg class="hero-ecg-wave" viewBox="0 0 360 120" fill="none">
            <polyline points="0,72 135,72 152,72 166,24 182,102 198,46 212,72 248,72 260,58 272,72 360,72" stroke="rgba(255,255,255,0.88)" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>

        <div class="landing-container landing-hero-container">
          <div class="landing-hero-grid">
            <!-- Columna izquierda: propuesta de valor y acciones -->
            <div class="landing-hero-copy">
              <button type="button" id="btnScrollSim1" class="landing-pill" aria-label="Ir al simulador de triaje en vivo">
                <span class="landing-pill-icon">${ui.icon('shield', 'icon icon--xs')}</span>
                <span>Plataforma Cloud Asistencial en Vivo</span>
              </button>

              <h1 class="landing-headline">
                Atención médica oportuna,<br>
                <span class="gradient-text">triaje clínico inteligente</span><br>
                y recetas digitales
              </h1>

              <p class="landing-lead">
                Sistema integral de gestión en salud bajo normativa colombiana. Orienta tus síntomas en tiempo real, agenda citas médicas presenciales o de telemedicina sin colisiones, y consulta tu historial clínico inmutable.
              </p>

              <div class="landing-cta-row">
                ${isAuth ? `
                  <a href="${userDashboardUrl}" class="btn btn-primary btn--lg landing-cta-primary">
                    ${ui.icon('user', 'icon icon--sm')}
                    <span>${userDashboardLabel}</span>
                    ${ui.icon('arrow-right', 'icon icon--sm')}
                  </a>
                  <button type="button" id="btnScrollSim2" class="btn btn-secondary btn--lg landing-cta-secondary">
                    ${ui.icon('search', 'icon icon--sm')}
                    <span>Probar simulador en vivo</span>
                  </button>
                ` : `
                  <a href="#/register" class="btn btn-primary btn--lg landing-cta-primary">
                    ${ui.icon('activity', 'icon icon--sm')}
                    <span>Comenzar mi triaje ahora</span>
                    ${ui.icon('arrow-right', 'icon icon--sm')}
                  </a>
                  <a href="#/availability" class="btn btn-secondary btn--lg landing-cta-secondary">
                    ${ui.icon('calendar', 'icon icon--sm')}
                    <span>Agendar una cita</span>
                  </a>
                `}
              </div>

              <div class="landing-trust-row">
                <span class="landing-trust-item">
                  ${ui.icon('check-circle', 'icon icon--xs text-primary')}
                  <span>Rápido y seguro</span>
                </span>
                <span class="landing-trust-item">
                  ${ui.icon('check-circle', 'icon icon--xs text-primary')}
                  <span>100% en la nube</span>
                </span>
                <span class="landing-trust-item">
                  ${ui.icon('check-circle', 'icon icon--xs text-primary')}
                  <span>Normativa colombiana (ADR-007)</span>
                </span>
              </div>
            </div>

            <!-- Columna derecha: composición Laptop + Móvil + Médica + Pill -->
            <div class="landing-hero-visual" aria-hidden="true">
              <!-- Mockup Desktop Panel Clínico -->
              <div class="hero-laptop">
                <div class="hero-laptop-screen">
                  <aside class="hero-laptop-sidebar">
                    <div class="hero-laptop-brand">
                      <span class="hero-laptop-dot"></span>
                      <span>MediTriaje</span>
                    </div>
                    <ul class="hero-laptop-nav">
                      <li class="is-active">${ui.icon('home', 'icon icon--xs')}<span>Inicio</span></li>
                      <li>${ui.icon('activity', 'icon icon--xs')}<span>Triaje</span></li>
                      <li>${ui.icon('calendar', 'icon icon--xs')}<span>Citas</span></li>
                      <li>${ui.icon('users', 'icon icon--xs')}<span>Pacientes</span></li>
                      <li>${ui.icon('file-text', 'icon icon--xs')}<span>Recetas</span></li>
                      <li>${ui.icon('layers', 'icon icon--xs')}<span>Historial</span></li>
                    </ul>
                  </aside>

                  <div class="hero-laptop-main">
                    <div class="hero-laptop-greeting">
                      <div class="hero-laptop-title">¡Hola, Dr. Ramírez!</div>
                      <div class="hero-laptop-sub">Resumen de hoy · Clínica Zafiro</div>
                    </div>

                    <div class="hero-laptop-kpis">
                      <div class="hero-laptop-kpi">
                        <span class="hero-laptop-kpi-icon hero-laptop-kpi-icon--teal">${ui.icon('calendar', 'icon icon--xs')}</span>
                        <span class="hero-laptop-kpi-label">Citas hoy</span>
                        <strong class="hero-laptop-kpi-val tabular-nums">24</strong>
                      </div>
                      <div class="hero-laptop-kpi">
                        <span class="hero-laptop-kpi-icon hero-laptop-kpi-icon--teal">${ui.icon('users', 'icon icon--xs')}</span>
                        <span class="hero-laptop-kpi-label">Pacientes activos</span>
                        <strong class="hero-laptop-kpi-val tabular-nums">1,326</strong>
                      </div>
                      <div class="hero-laptop-kpi hero-laptop-kpi--warn">
                        <span class="hero-laptop-kpi-icon hero-laptop-kpi-icon--amber">${ui.icon('alert-triangle', 'icon icon--xs')}</span>
                        <span class="hero-laptop-kpi-label">Alertas de triaje</span>
                        <strong class="hero-laptop-kpi-val hero-laptop-kpi-val--danger tabular-nums">3</strong>
                      </div>
                    </div>

                    <div class="hero-laptop-chart-card">
                      <div class="hero-laptop-chart-title">Triaje del día</div>
                      <div class="hero-laptop-chart-row">
                        <div class="hero-donut-wrap">
                          <svg class="hero-donut-svg" viewBox="0 0 48 48">
                            <circle cx="24" cy="24" r="19" fill="none" stroke="var(--border)" stroke-width="5"/>
                            <circle cx="24" cy="24" r="19" fill="none" stroke="#10B981" stroke-width="5" stroke-dasharray="77 120" stroke-dashoffset="0" stroke-linecap="round"/>
                            <circle cx="24" cy="24" r="19" fill="none" stroke="#F59E0B" stroke-width="5" stroke-dasharray="28 120" stroke-dashoffset="-80" stroke-linecap="round"/>
                            <circle cx="24" cy="24" r="19" fill="none" stroke="#EF4444" stroke-width="5" stroke-dasharray="9 120" stroke-dashoffset="-110" stroke-linecap="round"/>
                          </svg>
                          <div class="hero-donut-center">
                            <strong class="tabular-nums">24</strong>
                            <span>atenciones</span>
                          </div>
                        </div>
                        <ul class="hero-donut-legend">
                          <li><span class="dot dot--red"></span><span>Urgente (2)</span></li>
                          <li><span class="dot dot--amber"></span><span>Prioritario (6)</span></li>
                          <li><span class="dot dot--green"></span><span>Normal (16)</span></li>
                        </ul>
                      </div>
                    </div>
                  </div>
                </div>
                <div class="hero-laptop-base"></div>
              </div>

              <!-- Mockup Teléfono Móvil (Triaje del paciente) -->
              <div class="hero-phone">
                <div class="hero-phone-notch"></div>
                <div class="hero-phone-header">
                  ${ui.icon('activity', 'icon icon--xs text-primary')}
                  <span>MediTriaje</span>
                </div>
                <div class="hero-phone-body">
                  <div class="hero-phone-badge">
                    ${ui.icon('clipboard', 'icon icon--md text-primary')}
                  </div>
                  <div class="hero-phone-title">¿Cómo te sientes hoy?</div>
                  <div class="hero-phone-sub">Te ayudamos a evaluar tus síntomas y orientarte.</div>
                  <div class="hero-phone-cta">Iniciar triaje →</div>
                </div>
              </div>

              <!-- Retrato profesional de médica colombiana recortado -->
              <div class="hero-doctor-wrap">
                <img src="assets/images/hero-doctor.png" alt="Médica especialista de MediTriaje" class="hero-doctor-img" loading="eager" width="439" height="760">
              </div>

              <!-- Pill flotante de confianza -->
              <div class="hero-floating-pill">
                <span class="hero-floating-pill-icon">
                  ${ui.icon('shield-check', 'icon icon--sm')}
                </span>
                <span class="hero-floating-pill-text">
                  Tu salud, gestionada<br>de forma <strong class="text-primary">inteligente</strong>
                </span>
              </div>
            </div>
          </div>

          <!-- Franja de 4 pilares clave del hero -->
          <div class="hero-feature-strip">
            <article class="hero-feature-card">
              <div class="hero-feature-icon">${ui.icon('stethoscope', 'icon icon--md')}</div>
              <div>
                <h2 class="hero-feature-title">Triaje inteligente</h2>
                <p class="hero-feature-desc">Orientación clínica basada en protocolos de riesgo.</p>
              </div>
            </article>

            <article class="hero-feature-card">
              <div class="hero-feature-icon">${ui.icon('calendar', 'icon icon--md')}</div>
              <div>
                <h2 class="hero-feature-title">Gestión de citas</h2>
                <p class="hero-feature-desc">Agenda presencial o virtual sin colisiones.</p>
              </div>
            </article>

            <article class="hero-feature-card">
              <div class="hero-feature-icon">${ui.icon('file-text', 'icon icon--md')}</div>
              <div>
                <h2 class="hero-feature-title">Recetas digitales</h2>
                <p class="hero-feature-desc">Emisión y consulta de fórmulas médicas en línea.</p>
              </div>
            </article>

            <article class="hero-feature-card">
              <div class="hero-feature-icon">${ui.icon('layers', 'icon icon--md')}</div>
              <div>
                <h2 class="hero-feature-title">Historial clínico</h2>
                <p class="hero-feature-desc">Consulta segura e inmutable de tu información.</p>
              </div>
            </article>
          </div>
        </div>
      </section>

      <!-- Secciones inferiores centradas -->
      <div class="landing-body-sections">
        <div class="landing-container">
          <!-- 2. Funciones: Métricas + Bento Grid -->
          <section id="landing-funciones" class="landing-section scroll-mt-8">
            <div class="metrics-container">
              <div class="metric-card">
                <div class="metric-value tabular-nums" data-metric-target="42">42</div>
                <div class="metric-label">Reglas Clínicas</div>
                <div class="metric-sub">Motor determinista según intensidad y duración</div>
              </div>

              <div class="metric-card">
                <div class="metric-value tabular-nums" data-metric-target="5">5</div>
                <div class="metric-label">Niveles de Triaje</div>
                <div class="metric-sub">Escala I a V con semaforización reglamentaria</div>
              </div>

              <div class="metric-card">
                <div class="metric-value tabular-nums" data-metric-target="100" data-metric-suffix="%">100%</div>
                <div class="metric-label">Inmutabilidad</div>
                <div class="metric-sub">Historias clínicas protegidas con enmiendas cronológicas</div>
              </div>

              <div class="metric-card">
                <div class="metric-value tabular-nums" data-metric-target="0">0</div>
                <div class="metric-label">Colisiones</div>
                <div class="metric-sub">Reserva atómica de slots y prevención de doble turno</div>
              </div>
            </div>

            <div class="text-center mb-10">
              <span class="badge badge--scheduled mb-2">Arquitectura y Capacidades</span>
              <h2 class="text-3xl font-bold mb-3">Diseñado para la seguridad y calidad asistencial</h2>
              <p class="text-muted" style="max-width: 60ch; margin: 0 auto;">
                Cada componente sigue principios de Clean Architecture, segregación de responsabilidades y trazabilidad médica estricta.
              </p>
            </div>

            <div class="bento-grid">
              <!-- Tarjeta 1 -->
              <div class="bento-card">
                <div>
                  <div class="bento-icon-wrapper mb-4">
                    ${ui.icon('activity', 'icon icon--md')}
                  </div>
                  <h3 class="text-lg font-bold mb-2">Triaje Clínico con Corte de Emergencia</h3>
                  <p class="text-sm text-muted">
                    Motor puro y determinista. Ante síntomas de alarma vital (dolor torácico opresivo, dificultad severa, pérdida de conciencia), activa de inmediato el corte de emergencia orientando al 123 o urgencias.
                  </p>
                </div>
                <div>
                  <span class="badge badge--in-progress">
                    Motor Puro Determinista
                  </span>
                </div>
              </div>

              <!-- Tarjeta 2 -->
              <div class="bento-card">
                <div>
                  <div class="bento-icon-wrapper mb-4">
                    ${ui.icon('calendar', 'icon icon--md')}
                  </div>
                  <h3 class="text-lg font-bold mb-2">Agendamiento Asistencial sin Fricciones</h3>
                  <p class="text-sm text-muted">
                    Consulta en tiempo real por especialidad y modalidad (presencial o telemedicina). Bloqueo atómico de turnos con política estricta de cancelación con al menos 2 horas de anticipación.
                  </p>
                </div>
                <div>
                  <span class="badge badge--confirmed">
                    Slots en Tiempo Real
                  </span>
                </div>
              </div>

              <!-- Tarjeta 3 -->
              <div class="bento-card">
                <div>
                  <div class="bento-icon-wrapper mb-4">
                    ${ui.icon('file-text', 'icon icon--md')}
                  </div>
                  <h3 class="text-lg font-bold mb-2">Historia Clínica Inmutable</h3>
                  <p class="text-sm text-muted">
                    Cumplimiento normativo estricto (ADR-007): una atención médica cerrada nunca se modifica ni se borra. Cualquier ajuste requiere una enmienda cronológica firmada y justificada.
                  </p>
                </div>
                <div>
                  <span class="badge badge--scheduled">
                    Enmiendas Cronológicas
                  </span>
                </div>
              </div>

              <!-- Tarjeta 4 -->
              <div class="bento-card">
                <div>
                  <div class="bento-icon-wrapper mb-4">
                    ${ui.icon('pill', 'icon icon--md')}
                  </div>
                  <h3 class="text-lg font-bold mb-2">Recetas con Snapshot Farmacológico</h3>
                  <p class="text-sm text-muted">
                    Prescripción digital vinculada a la atención con congelamiento histórico del medicamento (nombre, principio activo, presentación), evitando discrepancias futuras en farmacia.
                  </p>
                </div>
                <div>
                  <span class="badge badge--confirmed">
                    Dispensación Trazable
                  </span>
                </div>
              </div>

              <!-- Tarjeta 5 -->
              <div class="bento-card">
                <div>
                  <div class="bento-icon-wrapper mb-4">
                    ${ui.icon('shield', 'icon icon--md')}
                  </div>
                  <h3 class="text-lg font-bold mb-2">Ficha de Emergencia con Código QR</h3>
                  <p class="text-sm text-muted">
                    Carné de emergencia accesible mediante token temporal para socorristas o paramédicos en situaciones críticas, informando grupo sanguíneo, alergias y contactos de urgencia.
                  </p>
                </div>
                <div>
                  <span class="badge badge--scheduled">
                    Acceso Rápido Vital
                  </span>
                </div>
              </div>

              <!-- Tarjeta 6 -->
              <div class="bento-card">
                <div>
                  <div class="bento-icon-wrapper mb-4">
                    ${ui.icon('lock', 'icon icon--md')}
                  </div>
                  <h3 class="text-lg font-bold mb-2">Seguridad &amp; Protocolo Break-Glass</h3>
                  <p class="text-sm text-muted">
                    Aislamiento de roles: el personal administrativo nunca accede a datos clínicos. En emergencias no asignadas, los médicos pueden activar el protocolo Break-Glass bajo auditoría forense inmutable.
                  </p>
                </div>
                <div>
                  <span class="badge badge--neutral">
                    Auditoría Forense
                  </span>
                </div>
              </div>
            </div>
          </section>

          <!-- 3. Seguridad: Spotlight Banner (Prescripción Digital Sellada & Ficha QR) -->
          <section id="landing-seguridad" class="landing-section scroll-mt-8">
            <div class="card bento-card--spotlight" style="padding: var(--space-8); border-radius: var(--radius-xl);">
              <div class="grid grid-cols-1 grid-cols-2-lg gap-8 items-center">
                <div>
                  <span class="badge badge--confirmed mb-3">Farmacia &amp; Trazabilidad Criptográfica</span>
                  <h3 class="text-2xl font-bold mb-3">Recetas Digitales con Snapshot Farmacológico</h3>
                  <p class="text-muted text-sm mb-4" style="line-height: 1.6;">
                    En MediTriaje, una receta médica emitida congela de manera inmutable el principio activo, concentración y posología del medicamento (ADR-007 / ADR-008). Además, cada paciente cuenta con su <strong>Ficha de Emergencia con Código QR</strong> accesible en situaciones críticas para socorristas y paramédicos.
                  </p>
                  <div class="flex flex-col gap-2 text-xs">
                    <div class="flex items-center gap-2 font-medium">
                      ${ui.icon('check', 'icon icon--xs text-success')}
                      <span>Sello digital inmutable y firma del profesional tratante</span>
                    </div>
                    <div class="flex items-center gap-2 font-medium">
                      ${ui.icon('check', 'icon icon--xs text-success')}
                      <span>Dispensación segura en farmacia sin discrepancias históricas</span>
                    </div>
                    <div class="flex items-center gap-2 font-medium">
                      ${ui.icon('check', 'icon icon--xs text-success')}
                      <span>Ficha médica de emergencia con token temporal y grupo sanguíneo</span>
                    </div>
                  </div>
                </div>
                <div>
                  <div class="hero-showcase-visual">
                    <div class="hero-visual-badge hero-visual-badge--top">
                      <span style="color: var(--success); display: inline-flex;">${ui.icon('shield', 'icon icon--xs')}</span>
                      <span>Prescripción Criptográfica</span>
                    </div>
                    <img src="assets/images/prescription-record-3d.jpg" alt="Prescripción médica digital y carné de emergencia QR 3D" loading="eager" width="600" height="380">
                    <div class="hero-visual-badge hero-visual-badge--bottom">
                      <span style="color: var(--primary); display: inline-flex;">${ui.icon('lock', 'icon icon--xs')}</span>
                      <span>ADR-007 Inmutable</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </section>

    <!-- 8. Simulador Interactivo de Triaje en Vivo -->
    <section id="demo-simulador" class="mb-16 scroll-mt-8">
      <div class="text-center mb-8">
        <span class="badge badge--scheduled mb-2">Demostrador en Tiempo Real</span>
        <h2 class="text-3xl font-bold mb-3">Simula el motor de triaje sin registro previo</h2>
        <p class="text-muted" style="max-width: 60ch; margin: 0 auto;">
          Selecciona un motivo de consulta y ajusta la intensidad de tus síntomas para comprobar cómo clasifica el algoritmo.
        </p>
      </div>

      <div class="simulator-box">
        <div class="grid grid-cols-1 grid-cols-2-md gap-6 mb-6">
          <div>
            <label for="simSintoma" class="form-label text-sm font-semibold mb-2 block">
              Selecciona síntoma principal:
            </label>
            <select id="simSintoma" class="form-select w-full">
              <option value="alarma-toracico">Alarma vital: Dolor torácico opresivo</option>
              <option value="alarma-respiratoria">Alarma vital: Dificultad respiratoria severa</option>
              <option value="fiebre" selected>Fiebre alta persistente (&gt; 38.5°C)</option>
              <option value="cefalea">Dolor de cabeza intenso o cefalea</option>
              <option value="abdominal">Dolor abdominal cólico</option>
              <option value="garganta">Molestia o dolor de garganta</option>
              <option value="rutina">Chequeo médico preventivo o rutina</option>
            </select>
          </div>

          <div>
            <label for="simDuracion" class="form-label text-sm font-semibold mb-2 block">
              Duración aproximada del síntoma:
            </label>
            <select id="simDuracion" class="form-select w-full">
              <option value="1">Menos de 2 horas</option>
              <option value="6">Entre 2 y 6 horas</option>
              <option value="24" selected>24 horas (1 día)</option>
              <option value="48">48 horas (2 días)</option>
              <option value="72">Más de 72 horas (3+ días)</option>
            </select>
          </div>
        </div>

        <div class="mb-6">
          <div class="flex flex-wrap justify-between items-center gap-2 mb-2">
            <label for="simIntensidad" class="form-label text-sm font-semibold">
              Nivel de Intensidad del Síntoma:
            </label>
            <span id="simIntensidadValor" class="badge badge--scheduled font-bold" style="font-size: var(--text-sm);">
              7 / 10
            </span>
          </div>
          <input type="range" id="simIntensidad" class="w-full" min="0" max="10" value="7" style="accent-color: var(--primary); cursor: pointer;">
          <div class="flex justify-between text-xs text-muted mt-1">
            <span>0 (Leve / Nulo)</span>
            <span>5 (Moderado)</span>
            <span>10 (Severo / Extremo)</span>
          </div>
        </div>

        <!-- Tarjeta de Resultado Dinámico del Simulador -->
        <div id="simResultadoBox" class="card" style="border: 2px solid var(--primary-soft-border); background-color: var(--primary-soft); padding: var(--space-5);">
          <div class="flex flex-wrap items-center justify-between gap-3 mb-3">
            <span class="text-xs uppercase font-bold tracking-wider" style="color: var(--primary);">Clasificación del Motor:</span>
            <span id="simNivelBadge" class="badge" style="background: var(--triage-3-bg); color: var(--triage-3-fg); border: 1px solid var(--triage-3-bd); font-size: var(--text-sm); font-weight: var(--weight-bold);">
              Nivel III · Urgente (Amarillo)
            </span>
          </div>

          <div id="simRutaText" class="text-lg font-bold mb-2" style="color: var(--text);">
            Ruta Sugerida: Consulta Médica Presencial
          </div>

          <p id="simMensajeText" class="text-sm text-muted mb-4">
            Tus síntomas requieren evaluación médica presencial en las próximas 4 a 6 horas para descartar complicaciones.
          </p>

          <div class="flex flex-wrap items-center justify-between gap-3 pt-3" style="border-top: 1px solid var(--primary-soft-border);">
            <span class="text-xs text-muted flex items-center gap-1">
              ${ui.icon('info', 'icon icon--xs')} Demostración puramente orientativa.
            </span>
            <a href="#/register" id="simCtaBtn" class="btn btn-primary btn--sm">
              <span id="simCtaText">Agendar cita con esta prioridad</span>
              <span id="simCtaIcon" style="display: inline-flex;">${ui.icon('arrow-right', 'icon icon--xs')}</span>
            </a>
          </div>
        </div>
      </div>
    </section>

          <!-- 4. Preguntas Frecuentes (Normativa) -->
          <section id="landing-normativa" class="landing-section scroll-mt-8">
            <div class="text-center mb-8">
              <span class="badge badge--scheduled mb-2">Preguntas Frecuentes</span>
              <h2 class="text-3xl font-bold mb-3">Todo lo que necesitas saber</h2>
              <p class="text-muted" style="max-width: 60ch; margin: 0 auto;">
                Respuestas claras sobre el funcionamiento, privacidad y agendamiento en la plataforma.
              </p>
            </div>

            <div class="faq-accordion">
              <details class="faq-item" open>
                <summary class="faq-summary">
                  <span>¿Cómo determina el sistema mi nivel de prioridad en el triaje?</span>
                  ${ui.icon('chevron-down', 'icon icon--sm')}
                </summary>
                <div class="faq-content">
                  El motor de triaje utiliza un algoritmo determinista puro basado en la duración del síntoma, su escala de intensidad (0 a 10) y la presencia de factores de riesgo. Cruza tus datos con una matriz de 42 reglas prototípicas alineadas a los 5 niveles estándar del sistema de salud colombiano (I: Resucitación/Emergencia a V: No urgente).
                </div>
              </details>

              <details class="faq-item">
                <summary class="faq-summary">
                  <span>¿Qué ocurre si selecciono un síntoma de alarma como dolor en el pecho?</span>
                  ${ui.icon('chevron-down', 'icon icon--sm')}
                </summary>
                <div class="faq-content">
                  El sistema activa de inmediato la compuerta de <strong>Corte de Emergencia</strong>. Se clasifica de forma automática como <strong>Nivel I (Emergencia Vital)</strong>, se bloquea la reserva de citas programadas y se muestra en pantalla completa las instrucciones para contactar a la Línea 123 o acudir inmediatamente al centro asistencial más cercano.
                </div>
              </details>

              <details class="faq-item">
                <summary class="faq-summary">
                  <span>¿Puedo cancelar o reprogramar una cita médica ya agendada?</span>
                  ${ui.icon('chevron-down', 'icon icon--sm')}
                </summary>
                <div class="faq-content">
                  Sí. Siguiendo la política de optimización asistencial de la plataforma, los pacientes pueden cancelar o modificar sus citas sin penalidad hasta con <strong>2 horas de anticipación</strong> a la hora de inicio del turno. Esto permite que el horario quede disponible inmediatamente para otro paciente que lo necesite.
                </div>
              </details>

              <details class="faq-item">
                <summary class="faq-summary">
                  <span>¿Por qué las historias clínicas y atenciones no se pueden borrar?</span>
                  ${ui.icon('chevron-down', 'icon icon--sm')}
                </summary>
                <div class="faq-content">
                  Por mandato ético y normativo (ADR-007). Los registros médicos firmados son inmutables para garantizar la autenticidad jurídica. En caso de requerir una corrección diagnóstica o farmacológica, el médico tratante emite una <strong>enmienda cronológica justificada</strong> que queda indexada sin alterar el registro original.
                </div>
              </details>

              <details class="faq-item">
                <summary class="faq-summary">
                  <span>¿Cómo funciona la Ficha Médica de Emergencia por código QR?</span>
                  ${ui.icon('chevron-down', 'icon icon--sm')}
                </summary>
                <div class="faq-content">
                  Desde tu portal de paciente puedes generar un código QR con token temporal cifrado. Al ser escaneado por personal de primeros auxilios o brigadistas, muestra únicamente datos vitales: tipo sanguíneo, alergias a medicamentos, condiciones preexistentes y contacto de emergencia, sin exponer historiales sensibles.
                </div>
              </details>
            </div>
          </section>

          <!-- 5. Footer Completo (Contacto) -->
          <footer id="landing-contacto" class="landing-footer scroll-mt-8">
            <div class="footer-grid">
              <div class="footer-col">
                <div class="flex items-center gap-2 mb-3">
                  <svg class="icon icon-hospital text-primary" viewBox="0 0 24 24" style="width: 24px; height: 24px;" aria-hidden="true">
                    <path d="M12 6v4"/><path d="M14 14h-4"/><path d="M14 18h-4"/><path d="M14 8h-4"/><path d="M18 12h-4"/><path d="M6 12h4"/><rect width="16" height="20" x="4" y="2" rx="2"/><path d="M10 22v-4h4v4"/>
                  </svg>
                  <span class="font-bold text-lg" style="color: var(--text);">MediTriaje</span>
                </div>
                <p class="text-sm text-muted mb-4" style="max-width: 32ch;">
                  Plataforma asistencial web para la orientación de triaje, agendamiento de citas, historia clínica inmutable y recetas digitales en Colombia.
                </p>
                <div class="text-xs text-muted">
                  Desplegado en Render (Backend Spring Boot 3) + Vercel (Frontend ES Modules) + Oracle Cloud ATP.
                </div>
              </div>

              <div class="footer-col">
                <h4>Plataforma</h4>
                <ul>
                  <li><button type="button" id="btnScrollSim3" style="background: none; border: none; padding: 0; color: var(--text-muted); cursor: pointer; font: inherit;">Simulador de Triaje</button></li>
                  <li><a href="#/availability">Consulta de Disponibilidad</a></li>
                  <li><a href="#/register">Registro de Pacientes</a></li>
                  <li><a href="#/login">Ingreso a la Plataforma</a></li>
                </ul>
              </div>

              <div class="footer-col">
                <h4>Normativa &amp; Salud</h4>
                <ul>
                  <li><a href="#/emergency-summary/token-demo">Ficha de Emergencia QR</a></li>
                  <li><button type="button" data-legal-modal="triage" style="background: none; border: none; padding: 0; color: var(--text-muted); cursor: pointer; font: inherit; text-align: left;">Estándar de Triaje (I-V)</button></li>
                  <li><button type="button" data-legal-modal="inmutabilidad" style="background: none; border: none; padding: 0; color: var(--text-muted); cursor: pointer; font: inherit; text-align: left;">Inmutabilidad Clínica</button></li>
                  <li><button type="button" data-legal-modal="breakglass" style="background: none; border: none; padding: 0; color: var(--text-muted); cursor: pointer; font: inherit; text-align: left;">Protocolo Break-Glass</button></li>
                </ul>
              </div>

              <div class="footer-col">
                <h4>Proyecto Académico</h4>
                <ul>
                  <li><a href="https://github.com/Itaborda/Meditriaje.com" target="_blank" rel="noopener noreferrer">Repositorio GitHub</a></li>
                  <li><button type="button" data-legal-modal="upc" style="background: none; border: none; padding: 0; color: var(--text-muted); cursor: pointer; font: inherit; text-align: left;">UPC Valledupar</button></li>
                  <li><button type="button" data-legal-modal="stack" style="background: none; border: none; padding: 0; color: var(--text-muted); cursor: pointer; font: inherit; text-align: left;">Stack Tecnológico</button></li>
                  <li><a href="#/login">Portal Administrativo</a></li>
                </ul>
              </div>
            </div>

            <div class="flex flex-wrap justify-between items-center gap-4 pt-6" style="border-top: 1px solid var(--border);">
              <div class="text-xs text-muted">
                © 2026 MediTriaje · Plataforma de Demostración Asistencial y Académica.
              </div>
              <div class="flex flex-wrap items-center gap-2 text-xs text-muted">
                <span>Diseño UI/UX con Tokens CSS</span>
                <span aria-hidden="true">·</span>
                <span>WCAG 2.1 AA Accesible</span>
              </div>
            </div>
          </footer>
        </div>
      </div>
    </div>
  `;

  // 12. Modales informativos y normativos del pie de página
  initLegalModals(container);

  // 13. Lógica Interactiva del Simulador de Triaje
  initSimulator(container, { userDashboardUrl, userDashboardLabel });

  // 14. Coreografía discreta de entrada y revelación en scroll
  initLandingMotion(container);
}

const LEGAL_MODAL_INFO = {
  triage: {
    title: 'Estándar de Triaje (I-V)',
    message: 'Sistema alineado con la Resolución 5596 de 2015 del Ministerio de Salud para clasificación de triaje clínico en Colombia (Niveles I a V).'
  },
  inmutabilidad: {
    title: 'Inmutabilidad Clínica (ADR-007)',
    message: 'Inmutabilidad garantizada por arquitectura limpia y registros append-only con enmiendas cronológicas justificadas (ADR-007).'
  },
  breakglass: {
    title: 'Protocolo Break-Glass (ADR-018)',
    message: 'Protocolo de auditoría forense para aperturas de urgencia no programadas en episodios asistenciales críticos (ADR-018).'
  },
  upc: {
    title: 'UPC Valledupar',
    message: 'Universidad Popular del Cesar · Programa de Ingeniería de Sistemas · Proyecto académico y asistencial MediTriaje.'
  },
  stack: {
    title: 'Stack Tecnológico',
    message: 'Arquitectura Limpia · Java 21 · Spring Boot 3 · Oracle Autonomous DB · Vanilla ES Modules · Design Tokens CSS.'
  }
};

function initLegalModals(container) {
  container.querySelectorAll('[data-legal-modal]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const key = btn.getAttribute('data-legal-modal');
      const info = LEGAL_MODAL_INFO[key];
      if (!info) return;
      ui.showModal({
        title: info.title,
        message: info.message,
        confirmText: 'Entendido',
        cancelText: ''
      });
    });
  });
}

function initLandingMotion(container) {
  motion.heroEntrance(container);

  const donutRing = container.querySelector('.hero-donut-svg circle');
  if (donutRing) {
    motion.drawSvg(donutRing, { duration: 700, delay: 200, root: container });
  }

  const metricsContainer = container.querySelector('.metrics-container');
  if (metricsContainer) {
    motion.revealOnScroll(metricsContainer, metricsContainer.querySelectorAll('.metric-card'), {
      root: container,
      onReveal: () => {
        metricsContainer.querySelectorAll('.metric-value[data-metric-target]').forEach((valEl) => {
          const target = Number(valEl.getAttribute('data-metric-target'));
          const suffix = valEl.getAttribute('data-metric-suffix') || '';
          if (Number.isFinite(target) && target > 0) {
            motion.animateCounter(valEl, target, { suffix, fromZero: true, duration: 520 });
          }
        });
      }
    });
  }

  const bentoGrid = container.querySelector('.bento-grid');
  if (bentoGrid) {
    motion.revealOnScroll(bentoGrid, bentoGrid.querySelectorAll('.bento-card'), { root: container });
  }

  const spotlightCard = container.querySelector('.bento-card--spotlight');
  if (spotlightCard) {
    motion.revealOnScroll(spotlightCard, [spotlightCard], { root: container });
  }

  const simSection = container.querySelector('#demo-simulador');
  const simBox = simSection?.querySelector('.simulator-box');
  if (simSection && simBox) {
    motion.revealOnScroll(simSection, [simBox], { root: container });
  }

  const faqSection = container.querySelector('#landing-normativa');
  const faqItems = faqSection?.querySelectorAll('.faq-item');
  if (faqSection && faqItems?.length) {
    motion.revealOnScroll(faqSection, faqItems, { root: container, delayStep: 40 });
  }
}

function initSimulator(container, { userDashboardUrl = '#/patient/dashboard', userDashboardLabel = 'Ir a mi panel' } = {}) {
  const selectSintoma = container.querySelector('#simSintoma');
  const selectDuracion = container.querySelector('#simDuracion');
  const sliderIntensidad = container.querySelector('#simIntensidad');
  const labelIntensidad = container.querySelector('#simIntensidadValor');
  const badgeNivel = container.querySelector('#simNivelBadge');
  const textRuta = container.querySelector('#simRutaText');
  const textMensaje = container.querySelector('#simMensajeText');
  const resultadoBox = container.querySelector('#simResultadoBox');
  const ctaBtn = container.querySelector('#simCtaBtn');
  const ctaText = container.querySelector('#simCtaText');
  const ctaIcon = container.querySelector('#simCtaIcon');

  if (!selectSintoma || !sliderIntensidad) return;

  const updateSimulation = (isUserInteraction = false) => {
    const sintoma = selectSintoma.value;
    const intensidad = parseInt(sliderIntensidad.value, 10);
    const duracion = parseInt(selectDuracion.value, 10);

    labelIntensidad.textContent = `${intensidad} / 10`;

    // 1. Corte de Emergencia (Síntomas de Alarma Vital)
    if (sintoma.startsWith('alarma')) {
      badgeNivel.style.background = 'var(--triage-1-bg)';
      badgeNivel.style.color = 'var(--triage-1-fg)';
      badgeNivel.style.borderColor = 'var(--triage-1-bd)';
      badgeNivel.textContent = 'Nivel I · Emergencia Vital (Rojo)';

      textRuta.textContent = 'Ruta: Urgencias Médicas Inmediatas (Línea 123)';
      textRuta.style.color = 'var(--danger)';

      textMensaje.textContent = '¡CORTE DE EMERGENCIA ACTIVADO! Presentas un síntoma de alarma médica inminente. Llama al 123 o acude de inmediato al centro de urgencias más cercano.';

      resultadoBox.style.borderColor = 'var(--danger)';
      resultadoBox.style.backgroundColor = 'var(--danger-bg)';

      if (ctaBtn && ctaText) {
        ctaBtn.href = 'tel:123';
        ctaBtn.className = 'btn btn-danger btn--sm';
        ctaText.textContent = 'Llamar a Emergencias (123)';
        if (ctaIcon) ctaIcon.innerHTML = ui.icon('phone', 'icon icon--xs');
      }
      if (isUserInteraction) {
        motion.pulseSubtle(resultadoBox);
        motion.pulseSubtle(badgeNivel);
      }
      return;
    }

    if (ctaBtn && ctaText) {
      if (auth.isAuthenticated) {
        ctaBtn.href = auth.isPaciente ? '#/patient/triage' : userDashboardUrl;
        ctaText.textContent = auth.isPaciente ? 'Continuar con mi triaje' : userDashboardLabel;
      } else {
        ctaBtn.href = '#/register';
        ctaText.textContent = 'Agendar cita con esta prioridad';
      }
      ctaBtn.className = 'btn btn-primary btn--sm';
      if (ctaIcon) ctaIcon.innerHTML = ui.icon('arrow-right', 'icon icon--xs');
    }

    // 2. Cálculo determinista según intensidad y duración
    if (intensidad >= 8) {
      badgeNivel.style.background = 'var(--triage-2-bg)';
      badgeNivel.style.color = 'var(--triage-2-fg)';
      badgeNivel.style.borderColor = 'var(--triage-2-bd)';
      badgeNivel.textContent = 'Nivel II · Muy Urgente (Naranja)';

      textRuta.textContent = 'Ruta Sugerida: Atención Prioritaria Inmediata';
      textRuta.style.color = 'var(--warning)';

      textMensaje.textContent = 'Tus síntomas tienen intensidad severa. Requieren valoración clínica prioritaria presencial en las próximas 2 horas.';

      resultadoBox.style.borderColor = 'var(--warning)';
      resultadoBox.style.backgroundColor = 'var(--warning-bg)';
    } else if (intensidad >= 5 || (sintoma === 'fiebre' && duracion >= 48)) {
      badgeNivel.style.background = 'var(--triage-3-bg)';
      badgeNivel.style.color = 'var(--triage-3-fg)';
      badgeNivel.style.borderColor = 'var(--triage-3-bd)';
      badgeNivel.textContent = 'Nivel III · Urgente (Amarillo)';

      textRuta.textContent = 'Ruta Sugerida: Consulta Médica Presencial';
      textRuta.style.color = 'var(--text)';

      textMensaje.textContent = 'Síntomas moderados que justifican consulta médica en el transcurso del día para definir plan terapéutico.';

      resultadoBox.style.borderColor = 'var(--primary-soft-border)';
      resultadoBox.style.backgroundColor = 'var(--primary-soft)';
    } else if (intensidad >= 3) {
      badgeNivel.style.background = 'var(--triage-4-bg)';
      badgeNivel.style.color = 'var(--triage-4-fg)';
      badgeNivel.style.borderColor = 'var(--triage-4-bd)';
      badgeNivel.textContent = 'Nivel IV · Menos Urgente (Verde)';

      textRuta.textContent = 'Ruta Sugerida: Telemedicina o Consulta Externa';
      textRuta.style.color = 'var(--text)';

      textMensaje.textContent = 'Síntomas leves o estables. Puedes agendar una videoconsulta de telemedicina o consulta médica presencial programada.';

      resultadoBox.style.borderColor = 'var(--success)';
      resultadoBox.style.backgroundColor = 'var(--success-bg)';
    } else {
      badgeNivel.style.background = 'var(--triage-5-bg)';
      badgeNivel.style.color = 'var(--triage-5-fg)';
      badgeNivel.style.borderColor = 'var(--triage-5-bd)';
      badgeNivel.textContent = 'Nivel V · No Urgente (Azul)';

      textRuta.textContent = 'Ruta Sugerida: Consulta Médica Programada';
      textRuta.style.color = 'var(--text)';

      textMensaje.textContent = 'Condición sin riesgo agudo identificable. Ideal para chequeos preventivos, lectura de exámenes o citas de control.';

      resultadoBox.style.borderColor = 'var(--info)';
      resultadoBox.style.backgroundColor = 'var(--info-bg)';
    }

    if (isUserInteraction) {
      motion.pulseSubtle(resultadoBox);
      motion.pulseSubtle(badgeNivel);
    }
  };

  const scrollSim = () => {
    container.querySelector('#demo-simulador')?.scrollIntoView({ behavior: 'smooth' });
  };
  container.querySelector('#btnScrollSim1')?.addEventListener('click', scrollSim);
  container.querySelector('#btnScrollSim2')?.addEventListener('click', scrollSim);
  container.querySelector('#btnScrollSim3')?.addEventListener('click', scrollSim);

  selectSintoma.addEventListener('change', () => updateSimulation(true));
  selectDuracion.addEventListener('change', () => updateSimulation(true));
  sliderIntensidad.addEventListener('input', () => updateSimulation(true));

  updateSimulation(false);
}
