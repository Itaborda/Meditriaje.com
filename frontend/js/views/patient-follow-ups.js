/**
 * MediTriaje 2.0 — Seguimiento Post-Atención del Paciente (patient-follow-ups.js)
 * Tareas de control, evolución de síntomas, exámenes pendientes y adherencia a tratamiento (ADR-015, §5.16).
 * Permite registrar reportes de evolución del paciente sin generar diagnósticos automáticos.
 */

import { api } from '../api.js';
import { motion } from '../motion.js';
import { ui, esc } from '../ui.js';

function formatDateTime(iso) {
  if (!iso) return '—';
  try {
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    }).format(new Date(iso));
  } catch {
    return String(iso);
  }
}

function formatDate(dateStr) {
  if (!dateStr) return '—';
  try {
    const parts = String(dateStr).split('-');
    if (parts.length === 3) {
      const d = new Date(parts[0], parts[1] - 1, parts[2]);
      return new Intl.DateTimeFormat('es-CO', {
        day: 'numeric',
        month: 'long',
        year: 'numeric'
      }).format(d);
    }
    return String(dateStr);
  } catch {
    return String(dateStr);
  }
}

function tipoBadge(tipo) {
  switch (tipo) {
    case 'CONTROL_MEDICO':
      return `<span class="badge badge--scheduled">${ui.icon('calendar', 'icon icon--sm')} Control Médico</span>`;
    case 'EVOLUCION_SINTOMAS':
      return `<span class="badge badge--confirmed">${ui.icon('activity', 'icon icon--sm')} Evolución de Síntomas</span>`;
    case 'EXAMEN_PENDIENTE':
      return `<span class="badge badge--rescheduled">${ui.icon('file-text', 'icon icon--sm')} Examen Pendiente</span>`;
    case 'ADHERENCIA_TRATAMIENTO':
      return `<span class="badge badge--attended">${ui.icon('pill', 'icon icon--sm')} Adherencia a Tratamiento</span>`;
    default:
      return `<span class="badge badge--scheduled">${esc(tipo)}</span>`;
  }
}

function estadoBadge(estado) {
  switch (estado) {
    case 'PENDIENTE':
      return `<span class="badge badge--scheduled">Pendiente</span>`;
    case 'COMPLETADO':
      return `<span class="badge badge--confirmed">${ui.icon('check', 'icon icon--sm')} Completado</span>`;
    case 'CANCELADO':
      return `<span class="badge badge--cancelled">Cancelado</span>`;
    default:
      return `<span class="badge badge--neutral">${esc(estado)}</span>`;
  }
}

export async function patientFollowUpsView(container) {
  let currentFilter = '';
  let totalAllCount = null;
  let currentItems = [];

  container.innerHTML = `
    <div style="padding-top: var(--space-4); padding-bottom: var(--space-12); max-width: 56rem; margin: 0 auto;">
      <nav class="breadcrumb mb-3" aria-label="Ruta de navegación">
        <a href="#/patient/dashboard" class="flex items-center gap-1">
          ${ui.icon('home', 'icon icon--sm')}
          <span>Inicio</span>
        </a>
        <span class="breadcrumb-separator" aria-hidden="true">&gt;</span>
        <span class="breadcrumb-current" aria-current="page">Seguimiento Post-Atención</span>
      </nav>

      <!-- Cabecera -->
      <div class="flex flex-wrap items-center justify-between gap-3 mb-6">
        <div class="min-w-0">
          <div class="flex items-center gap-2 mb-1">
            <a href="#/patient/dashboard" class="btn btn-ghost btn--sm btn--icon-only" aria-label="Volver al inicio">
              ${ui.icon('arrow-left')}
            </a>
            <h1 class="text-2xl font-bold m-0">Seguimiento Post-Atención</h1>
          </div>
          <p class="text-sm text-muted m-0">Planes de cuidado e indicaciones prescritas por tus profesionales tratantes</p>
        </div>
        <span class="badge badge--attended shrink-0">
          ${ui.icon('shield', 'icon icon--sm')}
          <span>Insumo Clínico Seguro</span>
        </span>
      </div>

      <!-- Filtros por estado (persistentes para conservar foco del botón al cambiar filtro) -->
      <div class="flex flex-wrap gap-2 mb-6" role="group" aria-label="Filtrar por estado">
        <button type="button" class="btn btn--sm btn-primary" data-filter="" aria-pressed="true">
          Todos<span id="followUpsAllCount"></span>
        </button>
        <button type="button" class="btn btn--sm btn-secondary" data-filter="PENDIENTE" aria-pressed="false">
          Pendientes
        </button>
        <button type="button" class="btn btn--sm btn-secondary" data-filter="COMPLETADO" aria-pressed="false">
          Completados
        </button>
      </div>

      <!-- Contenedor de lista -->
      <div id="followUpsList" class="flex flex-col gap-4"></div>
    </div>
  `;

  function syncFilterButtons() {
    container.querySelectorAll('button[data-filter]').forEach(btn => {
      const isActive = (btn.dataset.filter || '') === currentFilter;
      btn.classList.toggle('btn-primary', isActive);
      btn.classList.toggle('btn-secondary', !isActive);
      btn.setAttribute('aria-pressed', isActive ? 'true' : 'false');
    });

    const countEl = container.querySelector('#followUpsAllCount');
    if (countEl && totalAllCount !== null) {
      countEl.textContent = ` (${totalAllCount})`;
    }
  }

  container.querySelectorAll('button[data-filter]').forEach(btn => {
    btn.addEventListener('click', () => {
      currentFilter = btn.dataset.filter || '';
      syncFilterButtons();
      loadData();
    });
  });

  async function loadData() {
    const listEl = container.querySelector('#followUpsList');
    if (!listEl) return;

    ui.renderSkeleton(listEl, 'card');

    try {
      const params = { page: 0, size: 50 };
      if (currentFilter) {
        params.estado = currentFilter;
      }
      const res = await api.get('/patients/me/follow-ups', params);
      currentItems = res?.content || [];

      if (currentFilter === '') {
        totalAllCount = res?.totalElements ?? currentItems.length;
      }
      syncFilterButtons();
      renderList(currentItems);
    } catch (err) {
      ui.renderError(listEl, {
        title: 'No fue posible cargar tus seguimientos',
        message: esc(err.message || 'Ocurrió un problema al consultar tus tareas de seguimiento.'),
        onRetry: loadData
      });
    }
  }

  function renderList(items) {
    const listEl = container.querySelector('#followUpsList');
    if (!listEl) return;

    if (items.length === 0) {
      listEl.innerHTML = renderEmptyState();
      return;
    }

    listEl.innerHTML = items.map(renderCard).join('');

    listEl.querySelectorAll('.btn-report-evolution').forEach(btn => {
      btn.addEventListener('click', () => {
        const publicId = btn.dataset.id;
        const item = currentItems.find(i => i.publicId === publicId);
        if (item) {
          openReportModal(item);
        }
      });
    });

    motion.staggerCards('#followUpsList article.card', { root: container });
  }

  function renderCard(item) {
    const isPending = item.estado === 'PENDIENTE';
    const isCompleted = item.estado === 'COMPLETADO';

    return `
      <article class="card p-5" style="border-left: 4px solid ${isPending ? 'var(--primary)' : (isCompleted ? 'var(--success)' : 'var(--neutral-300)')};">
        <div class="flex flex-wrap items-center justify-between gap-2 mb-3">
          <div class="flex flex-wrap items-center gap-2">
            ${tipoBadge(item.tipo)}
            ${estadoBadge(item.estado)}
          </div>
          <span class="text-xs text-muted">Prescrito el ${esc(formatDateTime(item.createdAt))}</span>
        </div>

        <div class="mb-3">
          <div class="text-sm font-bold" style="color: var(--text);">
            ${esc(item.profesionalNombre)} · <span class="text-muted font-normal">${esc(item.profesionalEspecialidad)}</span>
          </div>
          ${item.fechaSugeridaControl ? `
            <div class="text-xs text-primary font-medium mt-1 flex items-center gap-1">
              ${ui.icon('calendar', 'icon icon--sm')}
              <span>Fecha sugerida de control: <strong>${esc(formatDate(item.fechaSugeridaControl))}</strong></span>
            </div>
          ` : ''}
        </div>

        <div class="p-3 mb-3" style="background: var(--surface-2); border-radius: var(--radius-md);">
          <span class="text-xs font-bold text-muted uppercase block mb-1">Indicaciones médicas</span>
          <p class="text-sm m-0" style="white-space: pre-wrap;">${esc(item.indicaciones)}</p>
        </div>

        ${isCompleted && item.reportePaciente ? `
          <div class="p-3 mb-3" style="background-color: var(--primary-soft); border: 1px solid var(--primary-soft-border); border-left: 3px solid var(--primary); border-radius: var(--radius-sm);">
            <div class="flex flex-wrap items-center justify-between gap-2 mb-1">
              <span class="text-xs font-bold text-primary uppercase">Tu reporte de evolución</span>
              <span class="text-xs text-muted">${esc(formatDateTime(item.fechaRespuestaPaciente))}</span>
            </div>
            <p class="text-sm m-0" style="white-space: pre-wrap; color: var(--text);">${esc(item.reportePaciente)}</p>
          </div>
        ` : ''}

        ${isPending ? `
          <div class="flex justify-end pt-2">
            <button type="button" class="btn btn-primary btn--sm btn-report-evolution" data-id="${esc(item.publicId)}">
              ${ui.icon('message-square', 'icon icon--sm')}
              <span>Reportar mi evolución</span>
            </button>
          </div>
        ` : ''}
      </article>
    `;
  }

  function renderEmptyState() {
    const filterLabels = {
      PENDIENTE: 'pendientes',
      COMPLETADO: 'completadas',
      CANCELADO: 'canceladas'
    };
    const suffix = filterLabels[currentFilter] ? ` ${filterLabels[currentFilter]}` : '';

    return `
      <div class="card p-8 text-center">
        <div class="empty-state-icon" style="background-color: var(--primary-soft); border: 1px solid var(--primary-soft-border); color: var(--primary); margin: 0 auto var(--space-4);">
          ${ui.icon('shield', 'icon icon--lg')}
        </div>
        <h3 class="text-lg font-bold mb-2">No tienes tareas de seguimiento${suffix}</h3>
        <p class="text-sm text-muted mb-4" style="max-width: 48ch; margin-left: auto; margin-right: auto;">
          Cuando tu médico tratante formule indicaciones o exámenes posteriores a tu consulta, aparecerán listados aquí.
        </p>
        <a href="#/patient/dashboard" class="btn btn-secondary btn--sm">Volver al inicio</a>
      </div>
    `;
  }

  function openReportModal(item) {
    const modalContent = `
      <div style="text-align: left;">
        <div class="p-3 mb-4" style="background: var(--surface-2); border-radius: var(--radius-md);">
          <span class="text-xs font-bold text-muted uppercase block mb-1">Indicación del profesional</span>
          <p class="text-sm m-0">${esc(item.indicaciones)}</p>
        </div>

        <div class="alert alert--info mb-4 text-xs" role="note">
          ${ui.icon('alert-circle', 'icon icon--sm alert-icon')}
          <div class="alert-content">
            <strong>Aviso clínico:</strong> Este reporte es un insumo confidencial para tu médico tratante y no genera diagnósticos automáticos ni sustituye atención de urgencias (§5.16).
          </div>
        </div>

        <div class="form-group mb-0">
          <div class="flex flex-wrap items-center justify-between gap-2 mb-1">
            <label for="modalReportText" class="form-label text-sm font-semibold m-0">Describe tu evolución o respuesta al tratamiento *</label>
            <span id="modalReportCounter" class="text-xs text-muted font-mono">0 / 2000</span>
          </div>
          <textarea id="modalReportText" class="form-textarea" rows="4" maxlength="2000" placeholder="Ej: He seguido el tratamiento indicado, la fiebre cedió desde ayer y el dolor ha disminuido favorablemente..." autofocus></textarea>
          <span class="form-help text-xs text-muted">Máximo 2000 caracteres.</span>
        </div>
        <p id="modalReportError" class="text-xs text-danger m-0 mt-2" role="alert"></p>
      </div>
    `;

    ui.showModal({
      title: 'Reportar Evolución Clínica',
      message: modalContent,
      confirmText: 'Enviar reporte',
      cancelText: 'Cancelar',
      onConfirm: async () => {
        const textEl = document.getElementById('modalReportText');
        const errEl = document.getElementById('modalReportError');
        const confirmBtn = document.querySelector('#modal-container .modal-backdrop.is-open:last-of-type .btn-confirm-modal');
        const reporte = textEl ? textEl.value.trim() : '';

        if (errEl) errEl.textContent = '';

        if (!reporte) {
          if (errEl) {
            errEl.textContent = 'Por favor escribe tu reporte de evolución antes de enviar.';
            motion.pulseSubtle(errEl);
          }
          textEl?.focus();
          return false;
        }

        try {
          ui.setButtonLoading(confirmBtn, true);
          await api.post(`/patients/me/follow-ups/${encodeURIComponent(item.publicId)}/report`, { reporte });
          ui.showToast('Reporte de evolución registrado correctamente.', 'success');
          await loadData();
          return true;
        } catch (ex) {
          ui.setButtonLoading(confirmBtn, false);
          if (errEl) {
            errEl.textContent = ex.message || 'Error al enviar el reporte.';
            motion.pulseSubtle(errEl);
          }
          ui.showToast(esc(ex.message || 'Error al enviar el reporte.'), 'danger');
          return false;
        }
      }
    });

    const textEl = document.getElementById('modalReportText');
    const counterEl = document.getElementById('modalReportCounter');
    const errEl = document.getElementById('modalReportError');
    textEl?.addEventListener('input', () => {
      if (counterEl) {
        counterEl.textContent = `${textEl.value.length} / 2000`;
      }
      if (errEl && textEl.value.trim()) {
        errEl.textContent = '';
      }
    });
  }

  await loadData();
}
