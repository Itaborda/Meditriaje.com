/**
 * MediTriaje 2.0 — Vista del Dashboard del Paciente (patient-dashboard.js)
 * Carga reactiva de perfil, próxima cita, historial y recetas recientes (M8.2a, HU-09).
 */

import { api } from '../api.js';
import { auth } from '../auth.js';
import { motion } from '../motion.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

/**
 * Formatea una fecha ISO a formato colombiano legible
 * @param {string} isoString Fecha en formato ISO
 * @param {boolean} includeTime Si incluye hora y minutos
 * @returns {string} Fecha formateada
 */
function formatDate(isoString, includeTime = true) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    const options = {
      timeZone: 'America/Bogota',
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    };
    if (includeTime) {
      options.hour = '2-digit';
      options.minute = '2-digit';
      options.hour12 = true;
    }
    return new Intl.DateTimeFormat('es-CO', options).format(d);
  } catch {
    return isoString;
  }
}

/**
 * Renderiza el dashboard principal del paciente
 * @param {HTMLElement} container Contenedor principal
 */
export async function patientDashboardView(container) {
  // 1. Estructura base inicial con skeletons de carga
  container.innerHTML = `
    <div style="padding-top: var(--space-4); padding-bottom: var(--space-12);">
      <!-- Saludo y bienvenida -->
      <div id="patientGreetingHeader" class="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div>
          <div class="skeleton skeleton-title" style="width: min(240px, 85%); height: 32px; margin-bottom: 8px;"></div>
          <div class="skeleton skeleton-text" style="width: min(320px, 100%); height: 16px;"></div>
        </div>
        <span class="badge badge--confirmed">
          ${ui.icon('check', 'icon icon--sm')}
          <span>Paciente Activo</span>
        </span>
      </div>

      <!-- Tarjeta destacada de Triaje -->
      <div class="card card--highlight mb-8">
        <div class="card-header flex items-center justify-start gap-3">
          <div style="background-color: var(--primary-soft); border: 1px solid var(--primary-soft-border); color: var(--primary); padding: 10px; border-radius: var(--radius-md); display: flex; flex-shrink: 0;">
            ${ui.icon('activity', 'icon icon--md')}
          </div>
          <div>
            <h2 class="card-title text-xl">¿Necesitas orientación médica hoy?</h2>
            <p class="card-subtitle text-sm text-muted">Evalúa tus síntomas de forma ágil y segura</p>
          </div>
        </div>
        <div class="card-body">
          <p class="text-sm mb-5" style="max-width: 65ch;">
            Inicia nuestro sistema de triaje clínico para clasificar tu nivel de prioridad asistencial y sugerirte de inmediato la ruta más oportuna de atención.
          </p>
          <a href="#/patient/triage" class="btn btn-primary btn--lg">
            ${ui.icon('activity')}
            <span>Iniciar nuevo triaje</span>
          </a>
        </div>
      </div>

      <!-- Grid con Próxima Cita y Resúmenes -->
      <div class="grid grid-cols-1 grid-cols-2-md gap-6 mb-8">
        <!-- Próxima Cita -->
        <div class="card" id="cardNextAppointment">
          <div class="card-header flex flex-wrap items-center justify-between gap-2">
            <h3 class="card-title text-lg flex items-center gap-2">
              ${ui.icon('calendar', 'icon icon--sm text-primary')}
              <span>Próxima Cita</span>
            </h3>
            <a href="#/patient/appointments" class="text-sm font-medium text-primary">Ver todas</a>
          </div>
          <div class="card-body" id="appointmentContainer">
            <div class="skeleton skeleton-card" style="height: 120px;"></div>
          </div>
        </div>

        <!-- Resumen de Atenciones Recientes -->
        <div class="card" id="cardRecentHistory">
          <div class="card-header flex flex-wrap items-center justify-between gap-2">
            <h3 class="card-title text-lg flex items-center gap-2">
              ${ui.icon('file-text', 'icon icon--sm text-primary')}
              <span>Últimas Atenciones</span>
            </h3>
            <a href="#/patient/history" class="text-sm font-medium text-primary">Ver historia</a>
          </div>
          <div class="card-body" id="historyContainer">
            <div class="skeleton skeleton-card" style="height: 120px;"></div>
          </div>
        </div>
      </div>

      <!-- Recetas Médicas Recientes -->
      <div class="card mb-8" id="cardRecentPrescriptions">
        <div class="card-header flex flex-wrap items-center justify-between gap-2">
          <h3 class="card-title text-lg flex items-center gap-2">
            ${ui.icon('pill', 'icon icon--sm text-primary')}
            <span>Mis Recetas Médicas</span>
          </h3>
          <a href="#/patient/prescriptions" class="text-sm font-medium text-primary">Ver todas las recetas</a>
        </div>
        <div class="card-body" id="prescriptionsContainer">
          <div class="skeleton skeleton-card" style="height: 100px;"></div>
        </div>
      </div>

      <!-- Seguimiento Post-Atención (F2.2.4) -->
      <div class="card mb-8" id="cardRecentFollowUps">
        <div class="card-header flex flex-wrap items-center justify-between gap-2">
          <h3 class="card-title text-lg flex items-center gap-2">
            ${ui.icon('shield', 'icon icon--sm text-primary')}
            <span>Seguimiento Post-Atención</span>
          </h3>
          <a href="#/patient/follow-ups" class="text-sm font-medium text-primary">Ver todas mis tareas</a>
        </div>
        <div class="card-body" id="followUpsContainer">
          <div class="skeleton skeleton-card" style="height: 100px;"></div>
        </div>
      </div>

      <!-- Resumen y Código QR de Emergencia (F2.3.5) -->
      <div class="card mb-8" id="cardEmergencyQr" style="border-left: 4px solid var(--danger); background: linear-gradient(90deg, var(--danger-bg) 0%, var(--surface) 100%);">
        <div class="card-body flex flex-wrap items-center justify-between gap-4">
          <div class="flex items-center gap-3">
            <div style="background-color: #DC2626; color: #FFFFFF; width: 44px; height: 44px; border-radius: var(--radius-md); display: flex; align-items: center; justify-content: center; flex-shrink: 0;">
              ${ui.icon('shield', 'icon icon--md')}
            </div>
            <div>
              <div class="flex flex-wrap items-center gap-2 mb-1">
                <h3 class="text-lg font-bold" style="color: var(--text);">Resumen de Salud y Código QR de Emergencia</h3>
                <span class="badge badge--danger text-xs font-semibold">15 Minutos · Seguro</span>
              </div>
              <p class="text-sm text-muted" style="margin: 0; max-width: 60ch;">
                Genera un código QR temporal para paramédicos y personal de ambulancia con tus alergias, medicamentos activos y contacto de emergencia.
              </p>
            </div>
          </div>
          <div>
            <a href="#/patient/emergency-qr" class="btn btn-primary">
              ${ui.icon('shield', 'icon icon--sm')}
              <span>Gestionar Código QR</span>
            </a>
          </div>
        </div>
      </div>

      <!-- Mis Alergias e Hipersensibilidades (D3, T4) -->
      <div class="card mb-8" id="cardPatientAllergies" style="border-left: 4px solid var(--warning);">
        <div class="card-body flex flex-wrap items-center justify-between gap-4">
          <div class="flex items-center gap-3">
            <div style="background-color: var(--warning-bg); color: var(--on-warning-bg); width: 44px; height: 44px; border-radius: var(--radius-md); display: flex; align-items: center; justify-content: center; flex-shrink: 0;">
              ${ui.icon('alert-circle', 'icon icon--md')}
            </div>
            <div>
              <div class="flex flex-wrap items-center gap-2 mb-1">
                <h3 class="text-lg font-bold" style="color: var(--text);">Mis Alergias e Hipersensibilidades</h3>
                <span class="badge badge--warning text-xs font-semibold">Trazabilidad Clínica</span>
              </div>
              <p class="text-sm text-muted" style="margin: 0; max-width: 60ch;">
                Declara y gestiona tus hipersensibilidades a fármacos o alimentos para que el personal de salud las consulte antes de prescribir.
              </p>
            </div>
          </div>
          <div>
            <a href="#/patient/allergies" class="btn btn-secondary">
              ${ui.icon('alert-circle', 'icon icon--sm')}
              <span>Ver mis alergias</span>
            </a>
          </div>
        </div>
      </div>
    </div>
  `;

  motion.staggerCards(container.querySelectorAll('.card'), { root: container });

  // 2. Carga en paralelo de datos del paciente
  const [perfilResult, appointmentsResult, historyResult, prescriptionsResult, followUpsResult] = await Promise.allSettled([
    api.get('/patients/me'),
    api.get('/patients/me/appointments?page=0&size=5'),
    api.get('/patients/me/history?page=0&size=3'),
    api.get('/patients/me/prescriptions?page=0&size=3'),
    api.get('/patients/me/follow-ups?page=0&size=3')
  ]);

  // 3. Renderizar Saludo
  const greetingEl = container.querySelector('#patientGreetingHeader');
  if (greetingEl) {
    if (perfilResult.status === 'fulfilled' && perfilResult.value) {
      const p = perfilResult.value;
      const nombreCompleto = `${p.nombres || ''} ${p.apellidos || ''}`.trim() || auth.user?.email || 'Paciente';
      greetingEl.querySelector('div').innerHTML = `
        <h1 class="text-2xl font-bold mb-1">¡Hola, ${esc(nombreCompleto)}!</h1>
        <p class="text-muted text-sm">Documento: ${esc(p.tipoDocumento)} ${esc(p.numeroDocumento)} · Portal personal de salud</p>
      `;
    } else {
      greetingEl.querySelector('div').innerHTML = `
        <h1 class="text-2xl font-bold mb-1">¡Bienvenido a MediTriaje!</h1>
        <p class="text-muted text-sm">${esc(auth.user?.email || 'Portal del paciente')}</p>
      `;
    }
  }

  const appointmentContainer = container.querySelector('#appointmentContainer');
  const historyContainer = container.querySelector('#historyContainer');
  const prescriptionsContainer = container.querySelector('#prescriptionsContainer');
  const followUpsContainer = container.querySelector('#followUpsContainer');

  const retrySection = async (el, endpoint, renderer, errorMessage) => {
    if (!el) return;
    el.innerHTML = `<div class="skeleton skeleton-card" style="height: 100px;"></div>`;
    try {
      const data = await api.get(endpoint);
      renderer(data);
      motion.revealPanel(el, { duration: 220, y: 6, root: container });
    } catch {
      renderCardError(el, errorMessage, () => retrySection(el, endpoint, renderer, errorMessage));
    }
  };

  // 4. Renderizar Próxima Cita
  const renderAppointmentsSection = (data) => {
    if (!appointmentContainer) return;
    const citas = data?.content || [];
    const proximaCita = citas.find(c => c.estado === 'PROGRAMADA' || c.estado === 'CONFIRMADA');

    if (proximaCita) {
      const badgeClass = proximaCita.estado === 'CONFIRMADA' ? 'badge--confirmed' : 'badge--scheduled';
      appointmentContainer.innerHTML = `
        <div style="background-color: var(--surface-2); border-radius: var(--radius-md); padding: var(--space-4); border-left: 4px solid var(--primary);">
          <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
            <span class="badge ${badgeClass}">${esc(proximaCita.estado)}</span>
            <span class="text-xs text-muted font-medium">${esc(proximaCita.modalidad || 'PRESENCIAL')}</span>
          </div>

          <div class="font-semibold text-base mb-1" style="color: var(--text);">
            ${esc(proximaCita.especialidadNombre || 'Consulta General')}
          </div>

          <div class="text-sm text-muted mb-3">
            <div><strong>Fecha:</strong> ${formatDate(proximaCita.fechaHoraInicio)}</div>
            <div><strong>Profesional:</strong> ${esc(proximaCita.profesionalNombre || 'Por asignar')}</div>
            <div><strong>Sede:</strong> ${esc(proximaCita.sedeNombre || 'Sede Principal')}${proximaCita.sedeDireccion ? ` · ${esc(proximaCita.sedeDireccion)}` : ''}</div>
          </div>

          <div class="flex gap-2">
            <a href="#/patient/appointments" class="btn btn-secondary btn--sm">
              <span>Gestionar cita</span>
            </a>
          </div>
        </div>
      `;
    } else {
      appointmentContainer.innerHTML = `
        <div class="empty-state text-center py-4">
          <div class="empty-state-icon" style="margin: 0 auto 8px auto; width: 40px; height: 40px;">
            ${ui.icon('calendar', 'icon icon--md text-muted')}
          </div>
          <p class="text-sm font-medium mb-1">Aún no tienes citas agendadas</p>
          <p class="text-xs text-muted mb-4">Puedes consultar horarios disponibles o realizar un triaje previo.</p>
          <a href="#/patient/book" class="btn btn-secondary btn--sm">
            ${ui.icon('calendar', 'icon icon--sm')}
            <span>Agendar nueva cita</span>
          </a>
        </div>
      `;
    }
  };

  if (appointmentsResult.status === 'fulfilled' && appointmentsResult.value?.content) {
    renderAppointmentsSection(appointmentsResult.value);
  } else {
    renderCardError(
      appointmentContainer,
      'No fue posible cargar tus citas en este momento.',
      () => retrySection(appointmentContainer, '/patients/me/appointments?page=0&size=5', renderAppointmentsSection, 'No fue posible cargar tus citas en este momento.')
    );
  }

  // 5. Renderizar Últimas Atenciones
  const renderHistorySection = (data) => {
    if (!historyContainer) return;
    const atenciones = data?.content || [];
    if (atenciones.length > 0) {
      historyContainer.innerHTML = `
        <div class="flex flex-col gap-3">
          ${atenciones.map(a => `
            <div style="border-bottom: 1px solid var(--border); padding-bottom: var(--space-3);" class="last-no-border">
              <div class="flex flex-wrap justify-between items-center gap-2 text-xs text-muted mb-1">
                <span>${formatDate(a.fechaAtencion || a.createdAt, false)}</span>
                <span class="badge badge--neutral">${esc(a.profesionalEspecialidad || 'Atención')}</span>
              </div>
              <div class="font-medium text-sm mb-1">${esc(a.motivoConsulta || 'Consulta médica')}</div>
              <div class="text-xs text-muted">
                ${a.diagnosticos?.length ? `<strong>Diag:</strong> ${esc(a.diagnosticos[0].codigoCie10)} - ${esc(a.diagnosticos[0].descripcion)}` : 'Diagnóstico registrado en historia clínica'}
              </div>
            </div>
          `).join('')}
        </div>
      `;
    } else {
      historyContainer.innerHTML = `
        <div class="empty-state text-center py-4">
          <div class="empty-state-icon" style="margin: 0 auto 8px auto; width: 40px; height: 40px;">
            ${ui.icon('file-text', 'icon icon--md text-muted')}
          </div>
          <p class="text-sm font-medium mb-1">Sin atenciones registradas</p>
          <p class="text-xs text-muted">Tu historial clínico aparecerá aquí después de tus consultas.</p>
        </div>
      `;
    }
  };

  if (historyResult.status === 'fulfilled' && historyResult.value?.content) {
    renderHistorySection(historyResult.value);
  } else {
    renderCardError(
      historyContainer,
      'No fue posible cargar tu historial clínico.',
      () => retrySection(historyContainer, '/patients/me/history?page=0&size=3', renderHistorySection, 'No fue posible cargar tu historial clínico.')
    );
  }

  // 6. Renderizar Recetas Recientes
  const renderPrescriptionsSection = (data) => {
    if (!prescriptionsContainer) return;
    const recetas = data?.content || [];
    if (recetas.length > 0) {
      prescriptionsContainer.innerHTML = `
        <div class="grid grid-cols-1 grid-cols-2-md gap-4">
          ${recetas.map(r => `
            <div class="card" style="background-color: var(--surface-2); padding: var(--space-4); border: 1px solid var(--border);">
              <div class="flex flex-wrap justify-between items-start gap-2 mb-2">
                <div>
                  <span class="badge badge--confirmed mb-1">Vigente (${esc(r.vigenciaDias)} días)</span>
                  <div class="text-xs text-muted">Expedida: ${formatDate(r.createdAt, false)}</div>
                </div>
                <span class="text-xs font-medium text-muted">${esc(r.profesionalNombre || 'Médico tratante')}</span>
              </div>

              <div class="text-sm mt-3">
                <div class="font-medium mb-1 text-xs text-muted">Medicamentos prescritos:</div>
                <ul style="padding-left: var(--space-4); margin: 0;" class="text-xs">
                  ${r.detalles?.map(d => `
                    <li><strong>${esc(d.nombreComercial)}</strong> (${esc(d.principioActivo)} ${esc(d.concentracion)}) - ${esc(d.dosis)}, ${esc(d.frecuencia)}</li>
                  `).join('') || '<li>Detalles disponibles en receta</li>'}
                </ul>
              </div>
            </div>
          `).join('')}
        </div>
      `;
    } else {
      prescriptionsContainer.innerHTML = `
        <div class="empty-state text-center py-4">
          <div class="empty-state-icon" style="margin: 0 auto 8px auto; width: 40px; height: 40px;">
            ${ui.icon('pill', 'icon icon--md text-muted')}
          </div>
          <p class="text-sm font-medium mb-1">No tienes recetas médicas recientes</p>
          <p class="text-xs text-muted">Las fórmulas farmacológicas emitidas por tus médicos aparecerán aquí.</p>
        </div>
      `;
    }
  };

  if (prescriptionsResult.status === 'fulfilled' && prescriptionsResult.value?.content) {
    renderPrescriptionsSection(prescriptionsResult.value);
  } else {
    renderCardError(
      prescriptionsContainer,
      'No fue posible cargar tus recetas médicas.',
      () => retrySection(prescriptionsContainer, '/patients/me/prescriptions?page=0&size=3', renderPrescriptionsSection, 'No fue posible cargar tus recetas médicas.')
    );
  }

  // 7. Renderizar Seguimientos Post-Atención Recientes (F2.2.4)
  const renderFollowUpsSection = (data) => {
    if (!followUpsContainer) return;
    const seguimientos = data?.content || [];
    if (seguimientos.length > 0) {
      followUpsContainer.innerHTML = `
        <div class="grid grid-cols-1 grid-cols-2-md gap-4">
          ${seguimientos.map(s => {
            const isPending = s.estado === 'PENDIENTE';
            const badgeClass = isPending ? 'badge--scheduled' : (s.estado === 'COMPLETADO' ? 'badge--confirmed' : 'badge--cancelled');
            const tipoLabel = s.tipo ? s.tipo.replace(/_/g, ' ') : 'SEGUIMIENTO';
            return `
              <div class="card" style="background-color: var(--surface-2); padding: var(--space-4); border: 1px solid var(--border); border-left: 3px solid ${isPending ? 'var(--primary)' : 'var(--success)'};">
                <div class="flex flex-wrap justify-between items-start gap-2 mb-2">
                  <span class="badge ${badgeClass}">${esc(s.estado)}</span>
                  <span class="text-xs text-muted uppercase font-medium">${esc(tipoLabel)}</span>
                </div>
                <div class="font-medium text-sm mb-1">${esc(s.profesionalNombre || 'Médico tratante')}${s.profesionalEspecialidad ? ` · <span class="text-xs text-muted">${esc(s.profesionalEspecialidad)}</span>` : ''}</div>
                <p class="text-xs text-muted mb-3" style="display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;">${esc(s.indicaciones || '')}</p>
                <div class="flex flex-wrap justify-between items-center gap-2 text-xs">
                  <span class="text-muted">${s.fechaSugeridaControl ? 'Control: ' + esc(s.fechaSugeridaControl) : ''}</span>
                  <a href="#/patient/follow-ups" class="font-medium text-primary">${isPending ? 'Reportar evolución' : 'Ver detalle'}</a>
                </div>
              </div>
            `;
          }).join('')}
        </div>
      `;
    } else {
      followUpsContainer.innerHTML = `
        <div class="empty-state text-center py-4">
          <div class="empty-state-icon" style="margin: 0 auto 8px auto; width: 40px; height: 40px;">
            ${ui.icon('shield', 'icon icon--md text-muted')}
          </div>
          <p class="text-sm font-medium mb-1">Sin tareas de seguimiento pendientes</p>
          <p class="text-xs text-muted">Cuando tus profesionales indiquen controles o exámenes posteriores, se mostrarán aquí.</p>
        </div>
      `;
    }
  };

  if (followUpsResult.status === 'fulfilled' && followUpsResult.value?.content) {
    renderFollowUpsSection(followUpsResult.value);
  } else {
    renderCardError(
      followUpsContainer,
      'No fue posible cargar tus tareas de seguimiento.',
      () => retrySection(followUpsContainer, '/patients/me/follow-ups?page=0&size=3', renderFollowUpsSection, 'No fue posible cargar tus tareas de seguimiento.')
    );
  }

  // Revelado suave de contenedores cargados en paralelo
  [appointmentContainer, historyContainer, prescriptionsContainer, followUpsContainer].forEach((el) => {
    if (el) motion.revealPanel(el, { duration: 220, y: 6, root: container });
  });
}

function renderCardError(containerEl, message, onRetry) {
  if (!containerEl) return;
  containerEl.innerHTML = `
    <div class="alert alert--warning text-xs flex-wrap items-center justify-between gap-2 mb-0" role="alert">
      <div class="flex items-center gap-2">
        ${ui.icon('alert-triangle', 'icon alert-icon')}
        <span>${esc(message)}</span>
      </div>
      <button type="button" class="btn btn-secondary btn--xs btn-card-retry">
        ${ui.icon('refresh-cw', 'icon icon--xs')}
        <span>Reintentar</span>
      </button>
    </div>
  `;
  containerEl.querySelector('.btn-card-retry')?.addEventListener('click', onRetry);
}

