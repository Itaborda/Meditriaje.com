/**
 * MediTriaje 2.0 — Vista de Historia Clínica del Paciente (patient-history.js)
 * Línea de tiempo cronológica de atenciones cerradas inmutables, diagnósticos CIE-10,
 * signos vitales y enmiendas clínicas append-only (M8.2c, HU-09, ADR-007, ADR-008).
 */

import { api } from '../api.js';
import { motion } from '../motion.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

let historyState = {
  atenciones: [],
  page: 0,
  size: 20,
  totalPages: 1,
  totalElements: 0,
  searchQuery: ''
};

/**
 * Normaliza texto para filtrado insensible a mayúsculas y tildes.
 */
function normalizeText(value) {
  return String(value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}

/**
 * Formatea una fecha ISO en formato colombiano
 */
function formatClinicalDate(isoString) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true
    }).format(d);
  } catch {
    return String(isoString);
  }
}

/**
 * Vista principal de Historia Clínica del Paciente (#/patient/history)
 * @param {HTMLElement} container Contenedor principal del DOM
 */
export async function patientHistoryView(container) {
  historyState.page = 0;
  historyState.searchQuery = '';

  container.innerHTML = `
    <div style="max-width: var(--container); margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      <nav class="breadcrumb mb-3" aria-label="Ruta de navegación">
        <a href="#/patient/dashboard" class="flex items-center gap-1">
          ${ui.icon('home', 'icon icon--sm')}
          <span>Inicio</span>
        </a>
        <span class="breadcrumb-separator" aria-hidden="true">&gt;</span>
        <span class="breadcrumb-current" aria-current="page">Mi Historia Clínica</span>
      </nav>

      <!-- Encabezado con acción de impresión -->
      <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div class="min-w-0">
          <div class="flex items-center gap-2 mb-1">
            <a href="#/patient/dashboard" class="btn btn-ghost btn--sm btn--icon-only" aria-label="Volver al panel" title="Volver al panel">
              ${ui.icon('arrow-left')}
            </a>
            <h1 class="text-2xl font-bold m-0">Mi Historia Clínica</h1>
          </div>
          <p class="text-sm text-muted m-0">Registro cronológico inmutable de atenciones y parámetros clínicos</p>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <button type="button" class="btn btn-secondary btn--sm" id="btnPrintHistory" title="Imprimir o guardar como PDF">
            ${ui.icon('printer', 'icon icon--sm')}
            <span>Imprimir historia</span>
          </button>
        </div>
      </div>

      <!-- Barra de búsqueda rápida en página (persistente para conservar foco al escribir) -->
      <div id="historySearchBar" class="card p-3 mb-5" style="display: none;">
        <div class="flex flex-wrap items-center gap-2">
          <label for="historySearchInput" class="sr-only">Buscar por especialidad, diagnóstico CIE-10 o profesional</label>
          <div class="flex items-center gap-2 flex-1 min-w-0">
            <span class="text-muted shrink-0">${ui.icon('search', 'icon icon--sm')}</span>
            <input
              type="search"
              id="historySearchInput"
              class="form-input form-input--sm"
              placeholder="Filtrar por especialidad, diagnóstico CIE-10 o médico..."
              autocomplete="off"
            />
          </div>
          <button type="button" id="btnClearHistorySearch" class="btn btn-ghost btn--sm" style="display: none;">
            ${ui.icon('x', 'icon icon--sm')}
            <span>Limpiar</span>
          </button>
        </div>
      </div>

      <!-- Contenedor dinámico del historial -->
      <div id="historyTimelineContainer"></div>
    </div>
  `;

  container.querySelector('#btnPrintHistory')?.addEventListener('click', () => {
    window.print();
  });

  const searchInput = container.querySelector('#historySearchInput');
  const clearBtn = container.querySelector('#btnClearHistorySearch');

  searchInput?.addEventListener('input', () => {
    historyState.searchQuery = searchInput.value;
    if (clearBtn) {
      clearBtn.style.display = historyState.searchQuery.trim() ? 'inline-flex' : 'none';
    }
    renderTimeline(container);
  });

  clearBtn?.addEventListener('click', () => {
    historyState.searchQuery = '';
    if (searchInput) {
      searchInput.value = '';
      searchInput.focus();
    }
    clearBtn.style.display = 'none';
    renderTimeline(container);
  });

  await loadClinicalHistory(container);
}

/**
 * Consulta la historia clínica paginada desde la API
 */
async function loadClinicalHistory(container) {
  const timelineContainer = container.querySelector('#historyTimelineContainer');
  const searchBar = container.querySelector('#historySearchBar');
  if (!timelineContainer) return;

  ui.renderSkeleton(timelineContainer, 'card');

  try {
    const res = await api.get('/patients/me/history', {
      page: historyState.page,
      size: historyState.size
    });

    historyState.atenciones = res?.content || [];
    historyState.totalPages = Math.max(1, res?.totalPages || 1);
    historyState.totalElements = res?.totalElements ?? historyState.atenciones.length;

    if (searchBar) {
      searchBar.style.display = historyState.atenciones.length > 0 ? 'block' : 'none';
    }

    renderTimeline(container);
  } catch (err) {
    if (searchBar) searchBar.style.display = 'none';
    ui.renderError(timelineContainer, {
      title: 'No fue posible cargar tu historia clínica',
      message: esc(err.message || 'Ocurrió un problema al consultar tus registros clínicos.'),
      onRetry: () => loadClinicalHistory(container)
    });
  }
}

/**
 * Renderiza la línea de tiempo vertical de atenciones
 */
function renderTimeline(container) {
  const timelineContainer = container.querySelector('#historyTimelineContainer');
  if (!timelineContainer) return;

  if (historyState.atenciones.length === 0) {
    ui.renderEmpty(timelineContainer, {
      icon: 'file-text',
      title: 'Aún no tienes atenciones registradas',
      description: 'Cuando asistas a una consulta médica con nuestros profesionales, tu valoración clínica e indicaciones aparecerán aquí de forma inmutable.',
      actionText: 'Iniciar triaje o consulta',
      onAction: () => router.navigate('/patient/triage')
    });
    return;
  }

  const normalizedQuery = normalizeText(historyState.searchQuery);
  const indexedAtenciones = historyState.atenciones.map((atencion, idx) => ({ atencion, originalIdx: idx }));
  const filteredAtenciones = normalizedQuery
    ? indexedAtenciones.filter(({ atencion }) => {
        const haystack = normalizeText([
          atencion.especialidadNombre,
          atencion.diagnosticoCodigo,
          atencion.diagnosticoDescripcion,
          atencion.profesionalNombre
        ].join(' '));
        return haystack.includes(normalizedQuery);
      })
    : indexedAtenciones;

  if (filteredAtenciones.length === 0) {
    ui.renderEmpty(timelineContainer, {
      icon: 'search',
      title: 'Sin atenciones que coincidan con el filtro',
      description: `No se encontraron atenciones para "${esc(historyState.searchQuery.trim())}" en esta página.`,
      actionText: 'Limpiar filtro',
      onAction: () => {
        historyState.searchQuery = '';
        const input = container.querySelector('#historySearchInput');
        const clearBtn = container.querySelector('#btnClearHistorySearch');
        if (input) {
          input.value = '';
          input.focus();
        }
        if (clearBtn) clearBtn.style.display = 'none';
        renderTimeline(container);
      }
    });
    return;
  }

  const countLabel = normalizedQuery
    ? `${filteredAtenciones.length} de ${historyState.atenciones.length} atención(es) en esta página`
    : `${historyState.totalElements} atención(es) clínica(s) registrada(s)`;

  timelineContainer.innerHTML = `
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
      <span class="text-xs text-muted font-bold uppercase tracking-wider">
        ${esc(countLabel)}
      </span>
      <span class="badge badge--confirmed text-xs">
        ${ui.icon('shield', 'icon icon--sm')} Registros Clínicos Inmutables
      </span>
    </div>

    <div class="timeline" style="margin-top: var(--space-3);">
      ${filteredAtenciones.map(({ atencion, originalIdx }) => {
        const signos = atencion.signosVitales;
        const enmiendas = atencion.enmiendas || [];
        const hasEnmiendas = enmiendas.length > 0;
        const atencionNumero = Math.max(1, historyState.totalElements - (historyState.page * historyState.size) - originalIdx);

        return `
          <div class="timeline-item" id="atencion-${esc(atencion.publicId)}" style="margin-bottom: var(--space-5);">
            <div class="timeline-marker"></div>

            <div class="card" style="padding: clamp(var(--space-4), 3vw, var(--space-6)); border-top: 4px solid var(--primary);">
              <!-- Encabezado de la atención -->
              <div class="flex flex-wrap items-start justify-between gap-3 pb-3 border-b mb-4">
                <div class="min-w-0">
                  <span class="badge badge--scheduled text-xs font-semibold mb-1">
                    Atención #${atencionNumero}
                  </span>
                  <h2 class="text-lg font-bold m-0 capitalize">
                    ${esc(formatClinicalDate(atencion.fechaCierre || atencion.createdAt))}
                  </h2>
                </div>

                <div class="flex flex-col items-start gap-1 min-w-0">
                  <span class="text-sm font-semibold block">${esc(atencion.profesionalNombre || 'Profesional Médico')}</span>
                  <span class="badge badge--neutral text-xs">${esc(atencion.especialidadNombre || 'Medicina General')}</span>
                </div>
              </div>

              <!-- Diagnóstico Principal CIE-10 -->
              <div class="mb-4 p-3" style="background-color: var(--primary-soft); border: 1px solid var(--primary-soft-border); border-radius: var(--radius-md); border-left: 4px solid var(--primary);">
                <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-1">Diagnóstico Principal (CIE-10):</span>
                <p class="text-md font-bold m-0 text-primary">
                  ${atencion.diagnosticoCodigo ? `${esc(atencion.diagnosticoCodigo)} — ` : ''}${esc(atencion.diagnosticoDescripcion || 'Diagnóstico Clínico')}
                </p>
              </div>

              <!-- Motivo y Evolución Clínica -->
              <div class="grid grid-cols-1 grid-cols-2-md gap-4 mb-4">
                <div class="min-w-0">
                  <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-1">Motivo de Consulta:</span>
                  <p class="text-sm m-0" style="color: var(--text);">
                    ${esc(atencion.motivoConsulta || 'Consulta médica programada.')}
                  </p>
                </div>

                <div class="min-w-0">
                  <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-1">Evolución Clínica:</span>
                  <p class="text-sm m-0" style="color: var(--text);">
                    ${esc(atencion.evolucion || 'Paciente valorado en consulta externa.')}
                  </p>
                </div>
              </div>

              <!-- Signos Vitales Registrados -->
              ${signos ? `
                <div class="mb-4">
                  <span class="text-xs text-muted font-bold uppercase tracking-wider block mb-2">Parámetros Fisiológicos (Signos Vitales):</span>
                  <div class="vitals-grid">
                    ${signos.presionSistolica && signos.presionDiastolica ? `
                      <div class="vital-card">
                        <span class="vital-card-label">Presión Arterial</span>
                        <span class="vital-card-val">${esc(signos.presionSistolica)}/${esc(signos.presionDiastolica)} <small style="font-size: 11px;">mmHg</small></span>
                      </div>
                    ` : ''}

                    ${signos.frecuenciaCardiaca ? `
                      <div class="vital-card">
                        <span class="vital-card-label">Frec. Cardíaca</span>
                        <span class="vital-card-val">${esc(signos.frecuenciaCardiaca)} <small style="font-size: 11px;">lpm</small></span>
                      </div>
                    ` : ''}

                    ${signos.frecuenciaRespiratoria ? `
                      <div class="vital-card">
                        <span class="vital-card-label">Frec. Respiratoria</span>
                        <span class="vital-card-val">${esc(signos.frecuenciaRespiratoria)} <small style="font-size: 11px;">rpm</small></span>
                      </div>
                    ` : ''}

                    ${signos.temperatura ? `
                      <div class="vital-card">
                        <span class="vital-card-label">Temperatura</span>
                        <span class="vital-card-val">${esc(signos.temperatura)} <small style="font-size: 11px;">°C</small></span>
                      </div>
                    ` : ''}

                    ${signos.saturacionOxigeno ? `
                      <div class="vital-card">
                        <span class="vital-card-label">Sat. Oxígeno</span>
                        <span class="vital-card-val">${esc(signos.saturacionOxigeno)}%</span>
                      </div>
                    ` : ''}

                    ${signos.pesoKg ? `
                      <div class="vital-card">
                        <span class="vital-card-label">Peso</span>
                        <span class="vital-card-val">${esc(signos.pesoKg)} <small style="font-size: 11px;">kg</small></span>
                      </div>
                    ` : ''}

                    ${signos.tallaCm ? `
                      <div class="vital-card">
                        <span class="vital-card-label">Talla</span>
                        <span class="vital-card-val">${esc(signos.tallaCm)} <small style="font-size: 11px;">cm</small></span>
                      </div>
                    ` : ''}
                  </div>
                </div>
              ` : ''}

              <!-- Indicaciones y Recomendaciones del Médico -->
              ${atencion.indicaciones ? `
                <div class="p-3 mb-4" style="background-color: var(--surface-2); border-radius: var(--radius-md);">
                  <strong class="text-xs font-bold uppercase tracking-wider text-muted block mb-1">
                    Indicaciones Terapéuticas:
                  </strong>
                  <p class="text-sm m-0" style="color: var(--text); line-height: var(--leading-relaxed);">
                    ${esc(atencion.indicaciones)}
                  </p>
                </div>
              ` : ''}

              <!-- Enmiendas Clínicas (Append-Only) -->
              ${hasEnmiendas ? `
                <div class="mt-4 pt-4 border-t">
                  <div class="flex flex-wrap items-center gap-2 mb-3">
                    ${ui.icon('alert-triangle', 'icon icon--sm text-warning')}
                    <h3 class="text-sm font-bold text-warning uppercase tracking-wider m-0">
                      Aclaraciones y Enmiendas Médicas (${enmiendas.length})
                    </h3>
                  </div>

                  <div class="flex flex-col gap-3">
                    ${enmiendas.map(enm => `
                      <div class="p-3" style="background-color: var(--warning-bg); border-left: 3px solid var(--warning); border-radius: var(--radius-sm); font-size: var(--text-xs);">
                        <div class="flex flex-wrap justify-between items-center gap-2 mb-1">
                          <strong style="color: var(--on-warning-bg);">${esc(enm.profesionalNombre || 'Médico Autor')}</strong>
                          <span class="text-muted">${esc(formatClinicalDate(enm.fechaEnmienda))}</span>
                        </div>
                        <p class="m-0 mb-1" style="color: var(--on-warning-bg);">${esc(enm.contenido)}</p>
                        ${enm.motivo ? `
                          <span class="block text-muted italic">Motivo de aclaración: ${esc(enm.motivo)}</span>
                        ` : ''}
                      </div>
                    `).join('')}
                  </div>
                </div>
              ` : ''}

            </div>
          </div>
        `;
      }).join('')}
    </div>

    ${historyState.totalPages > 1 ? `
      <div class="pagination-bar" role="navigation" aria-label="Paginación de historia clínica">
        <button type="button" class="btn btn-secondary btn--sm" id="btnHistoryPrev" ${historyState.page <= 0 ? 'disabled' : ''}>
          ${ui.icon('chevron-left', 'icon icon--sm')}
          <span>Anterior</span>
        </button>
        <span class="text-xs text-muted">
          Página <strong>${historyState.page + 1}</strong> de <strong>${historyState.totalPages}</strong> (${historyState.totalElements} atenciones)
        </span>
        <button type="button" class="btn btn-secondary btn--sm" id="btnHistoryNext" ${historyState.page + 1 >= historyState.totalPages ? 'disabled' : ''}>
          <span>Siguiente</span>
          ${ui.icon('chevron-right', 'icon icon--sm')}
        </button>
      </div>
    ` : ''}
  `;

  timelineContainer.querySelector('#btnHistoryPrev')?.addEventListener('click', () => {
    if (historyState.page > 0) {
      historyState.page--;
      loadClinicalHistory(container);
    }
  });

  timelineContainer.querySelector('#btnHistoryNext')?.addEventListener('click', () => {
    if (historyState.page + 1 < historyState.totalPages) {
      historyState.page++;
      loadClinicalHistory(container);
    }
  });

  motion.staggerCards('.timeline-item', { root: container });
  motion.staggerCards('.vital-card', { root: container, delayStep: 20, maxDelay: 160, y: 6 });
}
