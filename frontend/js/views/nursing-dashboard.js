/**
 * MediTriaje 2.0 — Panel y Cola Priorizada de Urgencias (nursing-dashboard.js)
 * Interfaz asistencial para personal de enfermería (ROLE_ENFERMERIA).
 * Monitorización en tiempo real de pacientes en espera, niveles I a V,
 * tiempos de espera normativos (Resolución 5596 de 2015), pacientes NN y asignaciones.
 */

import { emergencyApi } from '../api.js';
import { motion } from '../motion.js';
import { ui, esc } from '../ui.js';
import { openUniversalQrScanner } from '../components/qr-scanner-modal.js';

let dashboardState = {
  filterEstado: '',
  filterNivel: 'ALL',
  searchTerm: '',
  queue: [],
  loading: false,
  timerInterval: null,
  hashCleanup: null
};

const TRIAGE_PILLS = [
  { id: 'ALL', label: 'Todos' },
  { id: 'I', label: 'Nivel I' },
  { id: 'II', label: 'Nivel II' },
  { id: 'III', label: 'Nivel III' },
  { id: 'IV_V', label: 'Nivel IV–V' },
  { id: 'NN', label: 'NN' }
];

function isNursingDashboardRoute() {
  const path = (window.location.hash || '').slice(1).split('?')[0];
  return path === '/nursing' || path === '/nursing/dashboard';
}

function clearDashboardAutoRefresh() {
  if (dashboardState.timerInterval) {
    clearInterval(dashboardState.timerInterval);
    dashboardState.timerInterval = null;
  }
  if (dashboardState.hashCleanup) {
    window.removeEventListener('hashchange', dashboardState.hashCleanup);
    dashboardState.hashCleanup = null;
  }
}

function formatTriageBadge(nivel) {
  switch (nivel) {
    case 'I':
      return `<span class="badge badge--triage-i font-bold">${ui.icon('alert-triangle', 'icon icon--xs')} Nivel I — Reanimación</span>`;
    case 'II':
      return `<span class="badge badge--triage-ii font-bold">${ui.icon('alert-circle', 'icon icon--xs')} Nivel II — Emergencia</span>`;
    case 'III':
      return `<span class="badge badge--triage-iii font-bold">${ui.icon('clock', 'icon icon--xs')} Nivel III — Urgencia</span>`;
    case 'IV':
      return `<span class="badge badge--triage-iv font-semibold">${ui.icon('activity', 'icon icon--xs')} Nivel IV — Prioritaria</span>`;
    case 'V':
      return `<span class="badge badge--triage-v font-semibold">${ui.icon('calendar', 'icon icon--xs')} Nivel V — No Urgente</span>`;
    default:
      return `<span class="badge badge--scheduled font-semibold">${ui.icon('clock', 'icon icon--xs')} Pendiente Triaje</span>`;
  }
}

function formatEpisodeStatusBadge(estado) {
  switch (estado) {
    case 'REGISTRADO':
      return `<span class="badge badge--scheduled">Registrado · Sin triaje</span>`;
    case 'EN_TRIAJE':
      return `<span class="badge badge--warning">En espera médica</span>`;
    case 'EN_ATENCION':
      return `<span class="badge badge--in-progress">En atención</span>`;
    case 'EGRESADO':
    case 'CERRADO':
      return `<span class="badge badge--attended">Egresado</span>`;
    default:
      return `<span class="badge badge--neutral">${esc(estado || 'Registrado')}</span>`;
  }
}

function formatWaitTime(minutos) {
  if (minutos == null || minutos < 0) return '0 min';
  if (minutos < 60) return `${minutos} min`;
  const horas = Math.floor(minutos / 60);
  const rest = minutos % 60;
  return `${horas}h ${rest}m`;
}

function getWaitAlertClass(nivel, minutos) {
  if (minutos == null) return '';
  if (nivel === 'I') return 'text-danger font-bold';
  if (nivel === 'II' && minutos > 30) return 'text-danger font-bold';
  if (nivel === 'III' && minutos > 120) return 'text-warning font-bold';
  if (nivel === 'IV' && minutos > 180) return 'text-warning font-semibold';
  return 'text-muted';
}

function isProvisionalItem(item) {
  return item.codigoProvisional != null || Boolean(item.esIdentidadProvisional) || Boolean(item.esNN);
}

function getFilteredQueue() {
  const term = dashboardState.searchTerm.trim().toLowerCase();
  const nivel = dashboardState.filterNivel;

  return dashboardState.queue.filter(item => {
    const isNN = isProvisionalItem(item);
    if (nivel === 'I' && item.nivelTriaje !== 'I') return false;
    if (nivel === 'II' && item.nivelTriaje !== 'II') return false;
    if (nivel === 'III' && item.nivelTriaje !== 'III') return false;
    if (nivel === 'IV_V' && item.nivelTriaje !== 'IV' && item.nivelTriaje !== 'V') return false;
    if (nivel === 'NN' && !isNN) return false;

    if (!term) return true;
    const haystack = [
      item.pacienteNombre,
      item.identificadorVisible,
      item.documento,
      item.numeroDocumento,
      item.pacienteDocumento,
      item.codigoProvisional,
      item.motivoConsulta,
      item.motivoLlegada,
      item.motivoIngreso
    ].filter(Boolean).join(' ').toLowerCase();
    return haystack.includes(term);
  });
}

export async function nursingDashboardView(container) {
  clearDashboardAutoRefresh();
  dashboardState.filterEstado = '';
  dashboardState.filterNivel = 'ALL';
  dashboardState.searchTerm = '';

  container.innerHTML = `
    <div style="max-width: var(--container); margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Encabezado de Urgencias y Enfermería -->
      <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div>
          <div class="flex flex-wrap items-center gap-2 mb-1">
            <div class="empty-state-icon" style="margin: 0; width: 40px; height: 40px; background-color: var(--primary-soft); color: var(--primary);">
              ${ui.icon('activity', 'icon icon--md')}
            </div>
            <h1 class="text-2xl font-bold m-0">Centro de Urgencias y Triaje</h1>
          </div>
          <p class="text-sm text-muted m-0">Recepción, clasificación presencial (Resolución 5596), monitoreo de espera y reevaluación clínica</p>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <a href="#/hospital/census" class="btn btn-secondary btn--sm flex items-center gap-1" title="Ver censo de camas hospitalarias">
            ${ui.icon('hospital', 'icon icon--sm')}
            <span>Censo Camas</span>
          </a>

          <a href="#/affiliations/search" class="btn btn-secondary btn--sm flex items-center gap-1" title="Consultar aseguramiento EPS">
            ${ui.icon('file-text', 'icon icon--sm')}
            <span>Consulta EPS</span>
          </a>

          <a href="#/operational/dashboard" class="btn btn-secondary btn--sm flex items-center gap-1" title="Ver tablero de mando operativo">
            ${ui.icon('bar-chart-2', 'icon icon--sm')}
            <span>Mando</span>
          </a>

          <button type="button" id="btnNursingScanQr" class="btn btn-secondary btn--sm flex items-center gap-1" title="Escanear código QR con cámara o imagen">
            ${ui.icon('camera', 'icon icon--sm')}
            <span>Escanear QR</span>
          </button>

          <a href="#/nursing/admission" class="btn btn-primary btn--sm flex items-center gap-2">
            ${ui.icon('plus', 'icon icon--sm')}
            <span>Nueva Admisión</span>
          </a>
        </div>
      </div>

      <!-- Tarjetas de Resumen Operativo -->
      <div class="grid grid-cols-1 grid-cols-2-sm grid-cols-4-lg gap-4 mb-6" id="kpiCards">
        <div class="card p-4 flex flex-row items-center justify-between gap-3">
          <div>
            <div class="text-xs text-muted font-bold uppercase tracking-wider">En Espera Total</div>
            <div class="text-2xl font-bold mt-1 tabular-nums" id="kpiTotal">0</div>
          </div>
          <div class="stat-icon-wrapper" aria-hidden="true">${ui.icon('users', 'icon icon--md')}</div>
        </div>

        <div class="card p-4 flex flex-row items-center justify-between gap-3">
          <div>
            <div class="text-xs text-muted font-bold uppercase tracking-wider">Nivel I y II Críticos</div>
            <div class="text-2xl font-bold mt-1 text-danger tabular-nums" id="kpiCriticos">0</div>
          </div>
          <div class="stat-icon-wrapper" style="background-color: var(--danger-bg); color: var(--danger);" aria-hidden="true">${ui.icon('alert-triangle', 'icon icon--md')}</div>
        </div>

        <div class="card p-4 flex flex-row items-center justify-between gap-3">
          <div>
            <div class="text-xs text-muted font-bold uppercase tracking-wider">Pendientes de Triaje</div>
            <div class="text-2xl font-bold mt-1 text-warning tabular-nums" id="kpiPendientesTriaje">0</div>
          </div>
          <div class="stat-icon-wrapper" style="background-color: var(--warning-bg); color: var(--warning);" aria-hidden="true">${ui.icon('clock', 'icon icon--md')}</div>
        </div>

        <div class="card p-4 flex flex-row items-center justify-between gap-3">
          <div>
            <div class="text-xs text-muted font-bold uppercase tracking-wider">Identidad Provisional (NN)</div>
            <div class="text-2xl font-bold mt-1 text-info tabular-nums" id="kpiNN">0</div>
          </div>
          <div class="stat-icon-wrapper" style="background-color: var(--info-bg); color: var(--info);" aria-hidden="true">${ui.icon('help-circle', 'icon icon--md')}</div>
        </div>
      </div>

      <!-- Barra de Filtros y Búsqueda -->
      <div class="card p-4 mb-6">
        <div class="flex flex-col gap-3">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div class="flex flex-wrap items-center gap-2 flex-1 min-w-0">
              <div class="relative flex-1" style="min-width: 220px;">
                <label for="filterSearchInput" class="sr-only">Buscar paciente por nombre, documento o código NN</label>
                <input
                  type="search"
                  id="filterSearchInput"
                  class="form-input form-input--sm"
                  placeholder="Buscar por nombre, documento o código NN..."
                  autocomplete="off"
                >
              </div>
              <label for="filterEstadoSelect" class="sr-only">Filtrar por estado</label>
              <select id="filterEstadoSelect" class="form-select form-input--sm" style="width: auto; min-width: 210px;">
                <option value="">Todos los activos</option>
                <option value="REGISTRADO">Solo Registrados (sin triaje)</option>
                <option value="EN_TRIAJE">En Espera de Atención Médica</option>
                <option value="EN_ATENCION">En Atención Médica</option>
              </select>
            </div>

            <div class="flex flex-wrap items-center gap-2">
              <span class="text-xs text-muted" title="Actualización automática cada 30 segundos">Auto 30s</span>
              <button type="button" id="btnRefreshQueue" class="btn btn-ghost btn--sm flex items-center gap-1">
                ${ui.icon('refresh-cw', 'icon icon--sm')}
                <span>Actualizar Cola</span>
              </button>
            </div>
          </div>

          <div class="chip-group" id="triageFilterPills" role="group" aria-label="Filtrar por nivel de triaje o identidad NN">
            ${TRIAGE_PILLS.map(p => `
              <button
                type="button"
                class="chip ${dashboardState.filterNivel === p.id ? 'is-selected' : ''}"
                data-triage-filter="${p.id}"
                aria-pressed="${dashboardState.filterNivel === p.id}"
                style="min-height: 2.25rem; padding: var(--space-1) var(--space-3); font-size: var(--text-xs);"
              >
                ${esc(p.label)}
              </button>
            `).join('')}
          </div>
        </div>
      </div>

      <!-- Tabla de la Cola Priorizada -->
      <div class="card p-0 overflow-hidden">
        <div class="table-container" style="border: none; border-radius: 0;">
          <table class="table table--stacked" id="urgencyQueueTable" aria-label="Cola priorizada de urgencias">
            <thead>
              <tr>
                <th scope="col">Prioridad / Nivel</th>
                <th scope="col">Paciente</th>
                <th scope="col">Motivo de Ingreso</th>
                <th scope="col">Espera</th>
                <th scope="col">Estado</th>
                <th scope="col">Médico Asignado</th>
                <th scope="col" class="text-right">Acciones</th>
              </tr>
            </thead>
            <tbody id="urgencyQueueBody"></tbody>
          </table>
        </div>
      </div>

    </div>
  `;

  ui.renderSkeleton(document.getElementById('urgencyQueueBody'), 'table-rows');
  motion.staggerCards(container.querySelectorAll('#kpiCards > .card'), { root: container });

  // Hospital único: la cola es la del hospital (el servidor resuelve la sede)
  await loadQueue(container);

  // Escuchadores de eventos
  const searchInput = document.getElementById('filterSearchInput');
  searchInput?.addEventListener('input', (e) => {
    dashboardState.searchTerm = e.target.value;
    renderQueue(container);
  });

  const pillsGroup = document.getElementById('triageFilterPills');
  pillsGroup?.querySelectorAll('button[data-triage-filter]').forEach(btn => {
    btn.addEventListener('click', () => {
      dashboardState.filterNivel = btn.getAttribute('data-triage-filter') || 'ALL';
      pillsGroup.querySelectorAll('button[data-triage-filter]').forEach(b => {
        const active = b.getAttribute('data-triage-filter') === dashboardState.filterNivel;
        b.classList.toggle('is-selected', active);
        b.setAttribute('aria-pressed', active ? 'true' : 'false');
      });
      renderQueue(container);
    });
  });

  const estadoSelect = document.getElementById('filterEstadoSelect');
  estadoSelect?.addEventListener('change', async (e) => {
    dashboardState.filterEstado = e.target.value;
    await loadQueue(container);
  });

  const refreshBtn = document.getElementById('btnRefreshQueue');
  refreshBtn?.addEventListener('click', async () => {
    motion.spinOnce(refreshBtn);
    await loadQueue(container);
    ui.showToast('Cola actualizada.', 'info');
  });

  document.getElementById('btnNursingScanQr')?.addEventListener('click', () => {
    openUniversalQrScanner();
  });

  // Auto-refresco cada 30 segundos con limpieza al salir de la ruta
  dashboardState.hashCleanup = () => {
    if (!isNursingDashboardRoute() || !container.isConnected) {
      clearDashboardAutoRefresh();
    }
  };
  window.addEventListener('hashchange', dashboardState.hashCleanup);

  dashboardState.timerInterval = setInterval(() => {
    if (!isNursingDashboardRoute() || !container.isConnected || !document.getElementById('urgencyQueueBody')) {
      clearDashboardAutoRefresh();
      return;
    }
    loadQueue(container, { silent: true });
  }, 30000);
}

async function loadQueue(container, { silent = false } = {}) {
  const tbody = document.getElementById('urgencyQueueBody');
  if (!tbody) return;

  if (!silent) {
    ui.renderSkeleton(tbody, 'table-rows');
  }

  try {
    const rawQueue = await emergencyApi.listarColaUrgencias(dashboardState.filterEstado || null);
    if (!container.isConnected || !document.getElementById('urgencyQueueBody')) return;
    dashboardState.queue = Array.isArray(rawQueue) ? rawQueue : (rawQueue?.items || rawQueue?.content || []);
    renderQueue(container, { animateRows: !silent });
    updateKpis();
  } catch (err) {
    if (!tbody.isConnected) return;
    tbody.innerHTML = `<tr><td colspan="7" class="text-center p-8 text-danger">Error al cargar la cola de urgencias: ${esc(err.message)}</td></tr>`;
  }
}

function updateKpis() {
  const queue = dashboardState.queue;
  const total = queue.length;
  const criticos = queue.filter(q => q.nivelTriaje === 'I' || q.nivelTriaje === 'II').length;
  const pendientes = queue.filter(q => !q.nivelTriaje || q.nivelTriaje === 'PENDIENTE_VALORACION' || q.estado === 'REGISTRADO' || q.estadoEpisodio === 'REGISTRADO').length;
  const nn = queue.filter(q => isProvisionalItem(q)).length;

  motion.animateCounter(document.getElementById('kpiTotal'), total);
  motion.animateCounter(document.getElementById('kpiCriticos'), criticos);
  motion.animateCounter(document.getElementById('kpiPendientesTriaje'), pendientes);
  motion.animateCounter(document.getElementById('kpiNN'), nn);
}

function renderQueue(container, { animateRows = true } = {}) {
  const tbody = document.getElementById('urgencyQueueBody');
  if (!tbody) return;
  const filtered = getFilteredQueue();

  if (filtered.length === 0) {
    const hasClientFilter = Boolean(dashboardState.searchTerm.trim() || dashboardState.filterNivel !== 'ALL');
    tbody.innerHTML = `
      <tr>
        <td colspan="7" class="text-center p-12 text-muted">
          <div class="text-primary mb-2" style="display: inline-flex;">${ui.icon('check-circle', 'icon icon--lg')}</div>
          <p class="font-medium m-0 mx-auto">No hay pacientes en cola de urgencias para los filtros seleccionados.</p>
          ${hasClientFilter ? `<button type="button" id="btnClearQueueFilters" class="btn btn-secondary btn--sm mt-3">Limpiar filtros</button>` : ''}
        </td>
      </tr>
    `;
    tbody.querySelector('#btnClearQueueFilters')?.addEventListener('click', () => {
      dashboardState.searchTerm = '';
      dashboardState.filterNivel = 'ALL';
      const searchEl = document.getElementById('filterSearchInput');
      if (searchEl) searchEl.value = '';
      document.querySelectorAll('#triageFilterPills button[data-triage-filter]').forEach(b => {
        const active = b.getAttribute('data-triage-filter') === 'ALL';
        b.classList.toggle('is-selected', active);
        b.setAttribute('aria-pressed', active ? 'true' : 'false');
      });
      renderQueue(container);
    });
    return;
  }

  tbody.innerHTML = filtered.map(item => {
    const isNN = isProvisionalItem(item);
    const waitClass = getWaitAlertClass(item.nivelTriaje, item.minutosEspera);
    const epId = item.episodioPublicId || item.episodioId;
    const nombre = item.pacienteNombre || item.identificadorVisible || 'Paciente No Identificado';
    const doc = item.documento || item.numeroDocumento || item.pacienteDocumento || '';
    const motivo = item.motivoConsulta || item.motivoLlegada || item.motivoIngreso || '—';
    const estado = item.estado || item.estadoEpisodio || 'REGISTRADO';
    const medico = item.medicoAsignado || item.profesionalTratanteNombre || 'Sin asignar';

    return `
      <tr data-triage-level="${esc(item.nivelTriaje || '')}">
        <td data-label="Prioridad / Nivel">${formatTriageBadge(item.nivelTriaje)}</td>
        <td data-label="Paciente">
          <div class="font-bold flex flex-wrap items-center gap-1">
            ${isNN ? `<span class="badge badge--warning text-xs">NN</span>` : ''}
            <span>${esc(nombre)}</span>
          </div>
          ${isNN && item.codigoProvisional ? `<div class="text-xs text-muted font-mono">${esc(item.codigoProvisional)}</div>` : ''}
          ${!isNN && doc ? `<div class="text-xs text-muted font-mono">Doc: ${esc(doc)}</div>` : ''}
        </td>
        <td data-label="Motivo de Ingreso">
          <div class="text-sm truncate" style="max-width: 240px;" title="${esc(motivo)}">${esc(motivo)}</div>
        </td>
        <td data-label="Espera">
          <span class="${waitClass} tabular-nums">${formatWaitTime(item.minutosEspera)}</span>
        </td>
        <td data-label="Estado">
          ${formatEpisodeStatusBadge(estado)}
        </td>
        <td data-label="Médico Asignado">
          <span class="text-sm text-muted">${esc(medico)}</span>
        </td>
        <td data-label="Acciones" class="text-right">
          <div class="table-actions flex items-center justify-end gap-1 flex-wrap">
            <a href="#/nursing/assessment/${esc(epId)}" class="btn btn-ghost btn--xs" title="Valorar o reevaluar triaje">
              ${ui.icon('clipboard', 'icon icon--sm')}
              <span>Triaje</span>
            </a>

            <a href="#/hospital/beds/${esc(epId)}" class="btn btn-ghost btn--xs" title="Asignar o trasladar cama hospitalaria">
              ${ui.icon('bed', 'icon icon--sm')}
              <span>Cama</span>
            </a>

            ${isNN ? `
              <a href="#/nursing/identity/${esc(epId)}" class="btn btn-ghost btn--xs" title="Reconciliar identidad NN">
                ${ui.icon('user-check', 'icon icon--sm')}
                <span>Identidad</span>
              </a>
            ` : ''}

            <button
              type="button"
              class="btn btn-ghost btn--xs btn-close-episode"
              data-id="${esc(epId)}"
              title="Egresar o cerrar episodio"
              aria-label="Egresar o cerrar episodio de ${esc(nombre)}"
            >
              ${ui.icon('log-out', 'icon icon--sm')}
            </button>
          </div>
        </td>
      </tr>
    `;
  }).join('');

  if (animateRows) {
    motion.staggerCards(tbody.querySelectorAll('tr[data-triage-level]'), {
      delayStep: 25,
      maxDelay: 160,
      root: container
    });
    const urgentRows = tbody.querySelectorAll('tr[data-triage-level="I"], tr[data-triage-level="II"]');
    if (urgentRows.length > 0) {
      motion.pulseUrgency(urgentRows, { root: container });
    }
  }

  // Botones de cierre de episodio
  tbody.querySelectorAll('.btn-close-episode').forEach(btn => {
    btn.addEventListener('click', () => {
      const epId = btn.dataset.id;
      ui.showModal({
        title: 'Egresar Paciente de Urgencias',
        message: '¿Confirma el cierre y egreso asistencial de este episodio de atención?',
        confirmText: 'Sí, egresar',
        cancelText: 'Cancelar',
        onConfirm: async () => {
          try {
            await emergencyApi.cerrarEpisodio(epId);
            ui.showToast('Episodio de urgencias egresado correctamente.', 'success');
            await loadQueue(container);
          } catch (err) {
            ui.showToast('Error al egresar episodio: ' + (err.message || 'Error de servidor'), 'error');
          }
        }
      });
    });
  });
}
