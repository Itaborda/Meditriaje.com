/**
 * MediTriaje 2.0 — Ventanilla de Dispensación Farmacéutica (pharmacy-dispensation.js)
 * Interfaz asistencial para regentes de farmacia (ROLE_FARMACEUTICO).
 * Búsqueda de recetas por código de reclamación o documento, control estricto
 * de saldos acumulados, trazabilidad de lote INVIMA y comprobantes inmutables (F2.4, ADR-016).
 */

import { pharmacyApi } from '../api.js';
import { motion } from '../motion.js';
import { ui, esc } from '../ui.js';
import { openUniversalQrScanner } from '../components/qr-scanner-modal.js';

let pharmacyState = {
  searchQuery: '',
  prescriptions: [],
  page: 0,
  size: 10,
  totalPages: 1,
  totalElements: 0,
  selectedReceta: null, // RecetaDispensacionResponse
  lastComprobante: null  // DispensacionResponse
};

/**
 * Fecha actual en zona America/Bogota en formato YYYY-MM-DD para validación de vencimiento de lote.
 */
function getTodayBogotaIso() {
  try {
    return new Intl.DateTimeFormat('en-CA', {
      timeZone: 'America/Bogota',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).format(new Date());
  } catch {
    return new Date().toISOString().slice(0, 10);
  }
}

/**
 * Formatea una fecha y hora ISO en formato colombiano (America/Bogota)
 */
function formatDateTime(isoString) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(d);
  } catch {
    return String(isoString);
  }
}

/**
 * Formatea fecha solo (sin hora)
 */
function formatDate(isoString) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    }).format(d);
  } catch {
    return String(isoString);
  }
}

/**
 * Retorna badge visual para el estado de dispensación
 */
function renderDispensationBadge(estado) {
  switch (estado) {
    case 'DISPENSADA_TOTAL':
      return `<span class="badge badge--confirmed font-semibold">${ui.icon('check', 'icon icon--sm')} Dispensada Total</span>`;
    case 'DISPENSADA_PARCIAL':
      return `<span class="badge badge--warning font-semibold">${ui.icon('clock', 'icon icon--sm')} Dispensada Parcial</span>`;
    case 'PENDIENTE':
    default:
      return `<span class="badge badge--scheduled font-semibold">${ui.icon('pill', 'icon icon--sm')} Pendiente</span>`;
  }
}

/**
 * Vista principal de la Ventanilla de Farmacia (#/pharmacy/dispensation)
 */
export async function pharmacyDispensationView(container) {
  pharmacyState.searchQuery = '';
  pharmacyState.prescriptions = [];
  pharmacyState.page = 0;
  pharmacyState.totalPages = 1;
  pharmacyState.totalElements = 0;
  pharmacyState.selectedReceta = null;

  container.innerHTML = `
    <div style="max-width: var(--container); margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Encabezado de la Ventanilla -->
      <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div class="min-w-0">
          <div class="flex items-center gap-2 mb-1">
            <div class="empty-state-icon shrink-0" style="margin: 0; width: 40px; height: 40px; background-color: var(--primary-soft); border: 1px solid var(--primary-soft-border); color: var(--primary);">
              ${ui.icon('pill', 'icon icon--md')}
            </div>
            <h1 class="text-2xl font-bold m-0">Ventanilla de Farmacia</h1>
          </div>
          <p class="text-sm text-muted m-0">Dispensación y entrega controlada de medicamentos con trazabilidad de lote y control de saldos</p>
        </div>

        <!-- Hospital único: el servidor registra la entrega en la única sede -->
      </div>

      <!-- Área de Búsqueda de Recetas -->
      <div class="card p-5 mb-6">
        <form id="pharmacySearchForm" class="flex flex-wrap items-end gap-3">
          <div class="form-group m-0 flex-1 min-w-0" style="min-width: min(220px, 100%);">
            <label for="pharmacySearchInput" class="form-label text-xs font-bold uppercase tracking-wider text-muted">
              Código de Reclamación o Documento del Paciente
            </label>
            <input
              type="text"
              id="pharmacySearchInput"
              class="form-input text-base"
              placeholder="Ej: REC-A1B2C3D4 o número de cédula (ej: 10203040)"
              autocomplete="off"
            />
          </div>

          <button type="submit" class="btn btn-primary" id="btnSearchPrescriptions">
            ${ui.icon('search', 'icon icon--sm')}
            <span>Buscar Receta</span>
          </button>

          <button type="button" class="btn btn-secondary" id="btnPharmacyScanQr" title="Escanear código QR con cámara o imagen">
            ${ui.icon('camera', 'icon icon--sm')}
            <span>Escanear QR</span>
          </button>

          <button type="button" class="btn btn-secondary" id="btnResetSearch" title="Limpiar búsqueda">
            ${ui.icon('x', 'icon icon--sm')}
            <span>Limpiar</span>
          </button>
        </form>
      </div>

      <!-- Contenedor Principal de Resultados / Estación de Trabajo -->
      <div id="pharmacyMainContent"></div>

    </div>
  `;

  renderInitialState(container);

  const form = container.querySelector('#pharmacySearchForm');
  const searchInput = container.querySelector('#pharmacySearchInput');
  const resetBtn = container.querySelector('#btnResetSearch');
  const scanBtn = container.querySelector('#btnPharmacyScanQr');

  scanBtn?.addEventListener('click', () => {
    openUniversalQrScanner({
      title: 'Escanear QR en Farmacia',
      subtitle: 'Escanea el código de reclamación de receta o un carnet QR de emergencia.',
      onParsed: (parsed) => {
        if (parsed.kind === 'emergency-summary' || parsed.kind === 'tracking-qr') {
          return false;
        }
        searchInput.value = parsed.token;
        form.requestSubmit();
        return true;
      }
    });
  });

  form?.addEventListener('submit', async (e) => {
    e.preventDefault();
    pharmacyState.searchQuery = searchInput.value.trim();
    pharmacyState.page = 0;
    await executeSearch(container);
  });

  resetBtn?.addEventListener('click', () => {
    searchInput.value = '';
    pharmacyState.searchQuery = '';
    pharmacyState.selectedReceta = null;
    pharmacyState.prescriptions = [];
    renderInitialState(container);
  });
}

/**
 * Renderiza el estado vacío inicial
 */
function renderInitialState(container) {
  const mainContent = container.querySelector('#pharmacyMainContent');
  if (!mainContent) return;

  mainContent.innerHTML = `
    <div class="card p-8 text-center" style="background-color: var(--surface-2); border: 2px dashed var(--border);">
      <div class="empty-state-icon" style="margin: 0 auto var(--space-3); width: 48px; height: 48px; color: var(--text-muted);">
        ${ui.icon('search', 'icon icon--lg')}
      </div>
      <h3 class="text-base font-semibold mb-1">Ingresa el código o documento para iniciar</h3>
      <p class="text-xs text-muted m-0 mx-auto" style="max-width: 44ch;">El paciente presenta su código de reclamación alfanumérico generado en su portal.</p>
    </div>
  `;
}

/**
 * Ejecuta la búsqueda de recetas en la API
 */
async function executeSearch(container) {
  const mainContent = container.querySelector('#pharmacyMainContent');
  const searchBtn = container.querySelector('#btnSearchPrescriptions');
  if (!mainContent) return;

  ui.setButtonLoading(searchBtn, true);
  ui.renderSkeleton(mainContent, 'table');

  try {
    const res = await pharmacyApi.buscarRecetas(pharmacyState.searchQuery, pharmacyState.page, pharmacyState.size);
    pharmacyState.prescriptions = res?.content || [];
    pharmacyState.totalPages = Math.max(1, res?.totalPages || 1);
    pharmacyState.totalElements = res?.totalElements ?? pharmacyState.prescriptions.length;

    if (pharmacyState.prescriptions.length === 0) {
      ui.renderEmpty(mainContent, {
        icon: 'file-text',
        title: 'No se encontraron recetas médicas',
        description: `No hay prescripciones asociadas al criterio "${esc(pharmacyState.searchQuery)}". Verifica el código o documento.`,
        actionText: 'Nueva búsqueda',
        onAction: () => {
          container.querySelector('#pharmacySearchInput')?.focus();
        }
      });
      return;
    }

    // Si hay exactamente 1 coincidencia directa, abrir de inmediato la estación de trabajo
    if (pharmacyState.prescriptions.length === 1 && pharmacyState.searchQuery.length >= 6) {
      await loadAndDisplayWorkspace(pharmacyState.prescriptions[0].recetaPublicId, container);
      return;
    }

    renderSearchResults(container, { direction: 'forward' });
  } catch (err) {
    ui.renderError(mainContent, {
      title: 'Error en la búsqueda de recetas',
      message: esc(err.message || 'No fue posible completar la consulta en el servidor.'),
      onRetry: () => executeSearch(container)
    });
  } finally {
    ui.setButtonLoading(searchBtn, false);
  }
}

/**
 * Renderiza la lista de resultados de búsqueda
 */
function renderSearchResults(container, { direction = 'forward' } = {}) {
  const mainContent = container.querySelector('#pharmacyMainContent');
  if (!mainContent) return;

  mainContent.innerHTML = `
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
      <span class="text-xs text-muted font-bold uppercase tracking-wider">
        ${esc(pharmacyState.totalElements)} receta(s) encontrada(s)
      </span>
      <span class="badge badge--neutral text-xs">Ventanilla Activa</span>
    </div>

    <div class="card p-0 overflow-hidden mb-4">
      <div class="table-responsive">
        <table class="table table--stacked">
          <thead>
            <tr>
              <th scope="col">Código Reclamación</th>
              <th scope="col">Paciente</th>
              <th scope="col">Médico / Especialidad</th>
              <th scope="col">Emisión / Vencimiento</th>
              <th scope="col">Vigencia</th>
              <th scope="col">Estado Entrega</th>
              <th scope="col" class="text-right">Acción</th>
            </tr>
          </thead>
          <tbody>
            ${pharmacyState.prescriptions.map(r => `
              <tr>
                <td data-label="Código">
                  <strong class="font-mono text-primary font-bold">${esc(r.codigoReclamacion)}</strong>
                </td>
                <td data-label="Paciente">
                  <div>
                    <strong>${esc(r.pacienteNombre)}</strong>
                    <span class="text-xs text-muted block font-mono">${esc(r.pacienteDocumento)}</span>
                  </div>
                </td>
                <td data-label="Prescriptor">
                  <div>
                    <span class="font-medium text-xs block">${esc(r.profesionalNombre)}</span>
                    <span class="badge badge--neutral text-xs">${esc(r.especialidadNombre)}</span>
                  </div>
                </td>
                <td data-label="Emisión / Vencimiento" class="text-xs">
                  <div>
                    <span>${esc(formatDate(r.fechaEmision))}</span>
                    <span class="text-muted block">Hasta: ${esc(formatDate(r.fechaVencimiento))}</span>
                  </div>
                </td>
                <td data-label="Vigencia">
                  <span class="badge ${r.vencida ? 'badge--neutral' : 'badge--confirmed'} text-xs font-bold">
                    ${r.vencida ? 'Vencida' : 'Vigente'}
                  </span>
                </td>
                <td data-label="Estado">${renderDispensationBadge(r.estadoDispensacion)}</td>
                <td data-label="Acción" class="text-right">
                  <button type="button" class="btn btn-primary btn--sm btn-select-receta" data-receta-id="${esc(r.recetaPublicId)}">
                    ${ui.icon('pill', 'icon icon--sm')}
                    <span>Atender</span>
                  </button>
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    </div>

    ${pharmacyState.totalPages > 1 ? `
      <div class="pagination-bar" role="navigation" aria-label="Paginación de resultados farmacéuticos">
        <button type="button" class="btn btn-secondary btn--sm" id="btnPharmacyPrev" ${pharmacyState.page <= 0 ? 'disabled' : ''}>
          ${ui.icon('chevron-left', 'icon icon--sm')}
          <span>Anterior</span>
        </button>
        <span class="text-xs text-muted">
          Página <strong>${pharmacyState.page + 1}</strong> de <strong>${pharmacyState.totalPages}</strong> (${esc(pharmacyState.totalElements)} recetas)
        </span>
        <button type="button" class="btn btn-secondary btn--sm" id="btnPharmacyNext" ${pharmacyState.page + 1 >= pharmacyState.totalPages ? 'disabled' : ''}>
          <span>Siguiente</span>
          ${ui.icon('chevron-right', 'icon icon--sm')}
        </button>
      </div>
    ` : ''}
  `;

  mainContent.querySelectorAll('.btn-select-receta').forEach(btn => {
    btn.addEventListener('click', async () => {
      const recetaId = btn.dataset.recetaId;
      await loadAndDisplayWorkspace(recetaId, container);
    });
  });

  mainContent.querySelector('#btnPharmacyPrev')?.addEventListener('click', () => {
    if (pharmacyState.page > 0) {
      pharmacyState.page--;
      executeSearch(container);
    }
  });

  mainContent.querySelector('#btnPharmacyNext')?.addEventListener('click', () => {
    if (pharmacyState.page + 1 < pharmacyState.totalPages) {
      pharmacyState.page++;
      executeSearch(container);
    }
  });

  motion.stepTransition(mainContent, direction);
}

/**
 * Carga el detalle completo de la receta seleccionada e inicia la estación de trabajo
 */
async function loadAndDisplayWorkspace(recetaPublicId, container) {
  const mainContent = container.querySelector('#pharmacyMainContent');
  if (!mainContent) return;

  ui.renderSkeleton(mainContent, 'card');

  try {
    const receta = await pharmacyApi.consultarReceta(recetaPublicId);
    pharmacyState.selectedReceta = receta;
    renderDispensaryWorkspace(container);
  } catch (err) {
    ui.renderError(mainContent, {
      title: 'No fue posible cargar la receta',
      message: esc(err.message || 'Error al obtener los detalles de la prescripción.'),
      onRetry: () => loadAndDisplayWorkspace(recetaPublicId, container)
    });
  }
}

/**
 * Recalcula el total de unidades a entregar en la estación de trabajo
 */
function updateTotalUnitsIndicator(container) {
  const summaryEl = container.querySelector('#pharmacyTotalUnitsValue');
  if (!summaryEl) return;

  let total = 0;
  container.querySelectorAll('.item-row .input-cantidad').forEach(input => {
    const val = parseInt(input.value, 10);
    if (Number.isFinite(val) && val > 0) {
      total += val;
    }
  });
  summaryEl.textContent = String(total);
}

/**
 * Marca o limpia el estado visual de invalidez en una fila de medicamento
 */
function setRowInvalidState(row, inputEl, message = null) {
  if (!row) return;
  if (message) {
    row.style.backgroundColor = 'var(--danger-bg)';
    inputEl?.classList.add('form-input--error');
    const warnEl = row.querySelector('.row-validation-msg');
    if (warnEl) {
      warnEl.textContent = message;
      warnEl.style.display = 'block';
    }
  } else {
    row.style.backgroundColor = '';
    row.querySelectorAll('.form-input--error').forEach(el => el.classList.remove('form-input--error'));
    const warnEl = row.querySelector('.row-validation-msg');
    if (warnEl) {
      warnEl.textContent = '';
      warnEl.style.display = 'none';
    }
  }
}

/**
 * Renderiza la estación de trabajo para dispensar la receta
 */
function renderDispensaryWorkspace(container) {
  const mainContent = container.querySelector('#pharmacyMainContent');
  if (!mainContent || !pharmacyState.selectedReceta) return;

  const r = pharmacyState.selectedReceta;
  const items = r.items || [];
  const entregas = r.entregasPrevias || [];
  const puedeDispensar = !r.vencida && r.estadoDispensacion !== 'DISPENSADA_TOTAL';
  const todayBogota = getTodayBogotaIso();

  mainContent.innerHTML = `
    <!-- Barra superior de navegación de la estación -->
    <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
      <button type="button" class="btn btn-ghost btn--sm" id="btnBackToResults">
        ${ui.icon('arrow-left', 'icon icon--sm')}
        <span>Volver a la lista</span>
      </button>

      <span class="text-xs text-muted font-mono">Receta: ${esc(r.recetaPublicId)}</span>
    </div>

    <!-- Banner de Alerta Sanitaria si está vencida o completada -->
    ${r.vencida ? `
      <div class="alert alert--danger mb-5" role="alert">
        ${ui.icon('alert-triangle', 'icon icon--lg alert-icon')}
        <div class="alert-content">
          <div class="alert-title m-0">RECETA MÉDICA VENCIDA</div>
          <p class="text-xs m-0">
            La vigencia de ${esc(r.vigenciaDias)} días expiró el ${esc(formatDate(r.fechaVencimiento))}. Por normatividad sanitaria no se permite la entrega de medicamentos.
          </p>
        </div>
      </div>
    ` : ''}

    ${r.estadoDispensacion === 'DISPENSADA_TOTAL' ? `
      <div class="alert alert--success mb-5" role="status">
        ${ui.icon('check-circle', 'icon icon--lg alert-icon')}
        <div class="alert-content">
          <div class="alert-title m-0">RECETA COMPLETAMENTE DISPENSADA</div>
          <p class="text-xs m-0">Todos los medicamentos prescritos han sido entregados en su totalidad. Saldo pendiente: 0.</p>
        </div>
      </div>
    ` : ''}

    <!-- Ficha Informativa del Paciente y Prescriptor -->
    <div class="card mb-6" style="padding: clamp(var(--space-4), 2.5vw, var(--space-5)); border-top: 4px solid var(--primary);">
      <div class="flex flex-wrap items-start justify-between gap-3 pb-3 border-b mb-4">
        <div class="min-w-0">
          <span class="text-xs font-bold uppercase tracking-wider text-muted block">Código de Reclamación</span>
          <strong class="font-mono text-2xl text-primary font-bold tracking-wider break-all">${esc(r.codigoReclamacion)}</strong>
        </div>

        <div class="flex flex-col items-start gap-1">
          <span class="text-xs text-muted block">Estado General de Dispensación</span>
          ${renderDispensationBadge(r.estadoDispensacion)}
        </div>
      </div>

      <div class="grid grid-cols-1 grid-cols-3-md gap-4 text-xs">
        <div>
          <strong class="text-muted block">Paciente:</strong>
          <span class="font-semibold text-text text-sm">${esc(r.pacienteNombre)}</span>
          <span class="text-muted block font-mono">${esc(r.pacienteDocumento)}</span>
        </div>

        <div>
          <strong class="text-muted block">Médico Prescriptor:</strong>
          <span class="font-semibold text-text text-sm">${esc(r.profesionalNombre)}</span>
          <span class="badge badge--neutral text-xs mt-1">${esc(r.especialidadNombre)}</span>
        </div>

        <div>
          <strong class="text-muted block">Vigencia de la Receta:</strong>
          <span>Emisión: <strong>${esc(formatDate(r.fechaEmision))}</strong></span>
          <span class="block">Vence: <strong>${esc(formatDate(r.fechaVencimiento))}</strong> (${esc(r.vigenciaDias)} días)</span>
        </div>
      </div>
    </div>

    <!-- Formulario de Entrega y Control de Saldos -->
    <div class="card mb-6" style="padding: clamp(var(--space-4), 2.5vw, var(--space-5));">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div class="min-w-0">
          <h2 class="text-base font-bold m-0">Medicamentos y Control de Saldos</h2>
          <p class="text-xs text-muted m-0">Ingresa la cantidad a entregar, el número de lote INVIMA y la fecha de vencimiento</p>
        </div>
        <span class="badge badge--scheduled text-xs">Trazabilidad Sanitaria Obligatoria</span>
      </div>

      <div class="table-container mb-4">
        <table class="table table--stacked text-xs">
          <thead>
            <tr>
              <th scope="col" style="min-width: 180px;">Medicamento</th>
              <th scope="col" class="text-center">Prescrito</th>
              <th scope="col" class="text-center">Dispensado</th>
              <th scope="col" class="text-center">Saldo</th>
              <th scope="col">Estado</th>
              ${puedeDispensar ? `
                <th scope="col" style="width: 110px;" class="text-center">A Entregar</th>
                <th scope="col" style="min-width: 140px;">Lote INVIMA</th>
                <th scope="col" style="min-width: 140px;">Vence Lote</th>
              ` : ''}
            </tr>
          </thead>
          <tbody>
            ${items.map(item => `
              <tr class="item-row" data-med-id="${esc(item.medicamentoPublicId)}" data-med-name="${esc(item.nombreComercial)}" data-saldo="${esc(item.saldoPendiente)}">
                <td data-label="Medicamento">
                  <div class="min-w-0">
                    <strong class="block">${esc(item.nombreComercial)}</strong>
                    <span class="text-muted block">${esc(item.presentacion)} — ${esc(item.concentracion)}</span>
                    <span class="text-xs text-muted block">Dosis: ${esc(item.dosis)} (${esc(item.frecuencia)})</span>
                    <span class="row-validation-msg text-xs text-danger font-semibold mt-1" role="alert" style="display: none;"></span>
                  </div>
                </td>
                <td data-label="Prescrito" class="text-center font-medium">${esc(item.cantidadPrescrita)}</td>
                <td data-label="Dispensado" class="text-center font-medium text-success">${esc(item.cantidadDispensada)}</td>
                <td data-label="Saldo" class="text-center font-bold ${item.saldoPendiente > 0 ? 'text-primary' : 'text-muted'}">
                  ${esc(item.saldoPendiente)}
                </td>
                <td data-label="Estado">${renderDispensationBadge(item.estado)}</td>
                ${puedeDispensar ? `
                  <td data-label="A Entregar">
                    ${item.saldoPendiente > 0 ? `
                      <div style="min-width: 90px; max-width: 130px; margin-left: auto;">
                        <input
                          type="number"
                          class="form-input form-input--sm text-center font-bold input-cantidad"
                          aria-label="Cantidad a entregar de ${esc(item.nombreComercial)}"
                          min="0"
                          max="${esc(item.saldoPendiente)}"
                          value="${esc(item.saldoPendiente)}"
                          data-med-id="${esc(item.medicamentoPublicId)}"
                        />
                      </div>
                    ` : `
                      <span class="text-muted text-center block">—</span>
                    `}
                  </td>
                  <td data-label="Lote INVIMA">
                    ${item.saldoPendiente > 0 ? `
                      <div style="min-width: 130px; max-width: 190px; margin-left: auto;">
                        <input
                          type="text"
                          class="form-input form-input--sm font-mono input-lote"
                          aria-label="Lote INVIMA de ${esc(item.nombreComercial)}"
                          placeholder="Ej: LOTE-8942A"
                          maxlength="50"
                          data-med-id="${esc(item.medicamentoPublicId)}"
                        />
                      </div>
                    ` : `
                      <span class="text-muted block">—</span>
                    `}
                  </td>
                  <td data-label="Vence Lote">
                    ${item.saldoPendiente > 0 ? `
                      <div style="min-width: 140px; max-width: 190px; margin-left: auto;">
                        <input
                          type="date"
                          class="form-input form-input--sm input-vencimiento"
                          aria-label="Fecha de vencimiento del lote de ${esc(item.nombreComercial)}"
                          min="${esc(todayBogota)}"
                          data-med-id="${esc(item.medicamentoPublicId)}"
                        />
                        <span class="lot-expiry-warning text-xs text-danger font-semibold mt-1" role="alert" style="display: none;">Lote vencido</span>
                      </div>
                    ` : `
                      <span class="text-muted block">—</span>
                    `}
                  </td>
                ` : ''}
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>

      ${puedeDispensar ? `
        <!-- Observaciones de Dispensación -->
        <div class="form-group mb-4">
          <label for="dispenseObservations" class="form-label text-xs font-bold uppercase tracking-wider text-muted">
            Observaciones Sanitarias o de Entrega (Opcional)
          </label>
          <textarea
            id="dispenseObservations"
            class="form-textarea text-xs"
            rows="2"
            maxlength="500"
            placeholder="Indicaciones adicionales de entrega, condiciones especiales de conservación o notas farmacéuticas..."
          ></textarea>
        </div>

        <!-- Alerta de error de envío conservando inputs digitados -->
        <div id="pharmacySubmitError" class="alert alert--danger mb-4 text-xs" role="alert" style="display: none;">
          ${ui.icon('alert-circle', 'icon icon--sm alert-icon')}
          <div class="alert-content">
            <div class="alert-title m-0">No se pudo registrar la dispensación</div>
            <p id="pharmacySubmitErrorMsg" class="m-0"></p>
          </div>
        </div>

        <!-- Resumen de unidades y Botones de Acción -->
        <div class="flex flex-wrap items-center justify-between gap-3 pt-3 border-t">
          <div id="pharmacyTotalUnitsSummary" class="badge badge--soft-primary text-xs">
            ${ui.icon('package', 'icon icon--xs')}
            <span>Total a entregar: <strong id="pharmacyTotalUnitsValue" class="tabular-nums">0</strong> unidad(es)</span>
          </div>

          <div class="flex flex-wrap items-center gap-3">
            <button type="button" class="btn btn-secondary" id="btnCancelDispense">
              Cancelar
            </button>

            <button type="button" class="btn btn-primary" id="btnConfirmDispense">
              ${ui.icon('check', 'icon icon--sm')}
              <span>Confirmar y Registrar Entrega</span>
            </button>
          </div>
        </div>
      ` : ''}
    </div>

    <!-- Historial de Entregas Previas (Inmutables) -->
    <div class="card mb-6" style="padding: clamp(var(--space-4), 2.5vw, var(--space-5));">
      <h3 class="text-base font-bold mb-1">Historial de Entregas Farmacéuticas (${entregas.length})</h3>
      <p class="text-xs text-muted mb-4">Registro inmutable de dispensaciones realizadas en ventanilla para esta receta</p>

      ${entregas.length === 0 ? `
        <p class="text-xs text-muted m-0 p-4 text-center" style="background-color: var(--surface-2); border-radius: var(--radius-sm);">
          No se registran entregas previas para esta prescripción médica.
        </p>
      ` : `
        <div class="flex flex-col gap-4">
          ${entregas.map(ent => `
            <div class="p-4 pharmacy-history-card" style="background-color: var(--surface-2); border-radius: var(--radius-md); border: 1px solid var(--border);">
              <div class="flex flex-wrap items-start justify-between gap-3 text-xs pb-2 border-b mb-3">
                <div class="min-w-0">
                  <strong class="text-sm block">Comprobante #${esc(String(ent.publicId || '').slice(0, 8))}</strong>
                  <span class="text-muted">Fecha: ${esc(formatDateTime(ent.createdAt))}</span>
                </div>

                <div class="flex flex-col items-start gap-1 min-w-0">
                  <span class="badge badge--neutral text-xs">${esc(ent.sedeNombre)}</span>
                  <span class="text-muted block text-xs">Dispensado por: <strong>${esc(ent.dispensadorNombre)}</strong></span>
                </div>
              </div>

              ${ent.observaciones ? `
                <p class="text-xs text-muted mb-3"><strong>Observaciones:</strong> ${esc(ent.observaciones)}</p>
              ` : ''}

              <div class="table-container">
                <table class="table table--stacked text-xs" style="margin: 0;">
                  <thead>
                    <tr>
                      <th scope="col">Medicamento</th>
                      <th scope="col">Lote INVIMA</th>
                      <th scope="col">Vencimiento Lote</th>
                      <th scope="col" class="text-center">Cantidad Entregada</th>
                    </tr>
                  </thead>
                  <tbody>
                    ${(ent.detalles || []).map(d => `
                      <tr>
                        <td data-label="Medicamento">
                          <div><strong>${esc(d.nombreComercial)}</strong> (${esc(d.principioActivo)})</div>
                        </td>
                        <td data-label="Lote INVIMA">
                          <span class="badge badge--neutral font-mono text-xs">${esc(d.lote || 'N/A')}</span>
                        </td>
                        <td data-label="Vencimiento Lote">${esc(d.fechaVencimientoLote || 'N/A')}</td>
                        <td data-label="Cantidad Entregada" class="text-center font-bold text-success">${esc(d.cantidadEntregada)}</td>
                      </tr>
                    `).join('')}
                  </tbody>
                </table>
              </div>
            </div>
          `).join('')}
        </div>
      `}
    </div>
  `;

  updateTotalUnitsIndicator(container);

  mainContent.querySelectorAll('.input-cantidad').forEach(input => {
    input.addEventListener('input', () => {
      const row = input.closest('.item-row');
      const saldo = parseInt(row?.dataset.saldo || '0', 10) || 0;
      const val = parseInt(input.value, 10);
      if (Number.isFinite(val) && (val < 0 || val > saldo)) {
        setRowInvalidState(row, input, `Cantidad permitida: 0 a ${saldo}.`);
      } else {
        setRowInvalidState(row, input, null);
      }
      updateTotalUnitsIndicator(container);
    });
  });

  mainContent.querySelectorAll('.input-vencimiento').forEach(vencInput => {
    vencInput.addEventListener('change', () => {
      const row = vencInput.closest('.item-row');
      const expiryWarn = vencInput.parentElement?.querySelector('.lot-expiry-warning');
      const val = vencInput.value;
      if (val && val < todayBogota) {
        if (expiryWarn) expiryWarn.style.display = 'block';
        setRowInvalidState(row, vencInput, 'La fecha de vencimiento del lote ya expiró.');
      } else {
        if (expiryWarn) expiryWarn.style.display = 'none';
        setRowInvalidState(row, vencInput, null);
      }
    });
  });

  const navigateBack = () => {
    if (pharmacyState.prescriptions.length > 1) {
      renderSearchResults(container, { direction: 'back' });
    } else {
      renderInitialState(container);
    }
  };

  mainContent.querySelector('#btnBackToResults')?.addEventListener('click', navigateBack);
  mainContent.querySelector('#btnCancelDispense')?.addEventListener('click', navigateBack);

  mainContent.querySelector('#btnConfirmDispense')?.addEventListener('click', async () => {
    await processDispensation(container);
  });

  motion.stepTransition(mainContent, 'forward');
  if (entregas.length > 0) {
    motion.staggerCards('.pharmacy-history-card', { root: container });
  }
}

/**
 * Valida y envía la solicitud de dispensación
 */
async function processDispensation(container) {
  const r = pharmacyState.selectedReceta;
  if (!r) return;

  const submitErrorBox = container.querySelector('#pharmacySubmitError');
  if (submitErrorBox) submitErrorBox.style.display = 'none';

  const todayBogota = getTodayBogotaIso();
  const rows = container.querySelectorAll('.item-row');
  const detalles = [];
  let totalUnidades = 0;

  for (const row of rows) {
    setRowInvalidState(row, null, null);
    const expiryWarn = row.querySelector('.lot-expiry-warning');
    if (expiryWarn) expiryWarn.style.display = 'none';
  }

  for (const row of rows) {
    const medId = row.dataset.medId;
    const medName = row.dataset.medName || 'el medicamento';
    const saldo = parseInt(row.dataset.saldo, 10) || 0;
    const qtyInput = row.querySelector('.input-cantidad');
    const loteInput = row.querySelector('.input-lote');
    const vencInput = row.querySelector('.input-vencimiento');

    if (!qtyInput) continue;

    const rawVal = qtyInput.value.trim();
    const cantidad = rawVal === '' ? 0 : parseInt(rawVal, 10);

    if (!Number.isFinite(cantidad) || cantidad < 0) {
      setRowInvalidState(row, qtyInput, 'Ingresa una cantidad válida mayor o igual a 0.');
      ui.showToast(`Cantidad inválida para ${esc(medName)}.`, 'danger');
      qtyInput.focus();
      return;
    }

    if (cantidad === 0) continue;

    if (cantidad > saldo) {
      setRowInvalidState(row, qtyInput, `Supera el saldo pendiente (${saldo}).`);
      ui.showToast(`La cantidad ingresada (${cantidad}) supera el saldo pendiente (${saldo}) para ${esc(medName)}.`, 'danger');
      qtyInput.focus();
      return;
    }

    const lote = loteInput ? loteInput.value.trim() : null;
    const fechaVenc = vencInput && vencInput.value ? vencInput.value : null;

    if (fechaVenc && fechaVenc < todayBogota) {
      const expiryWarn = vencInput.parentElement?.querySelector('.lot-expiry-warning');
      if (expiryWarn) expiryWarn.style.display = 'block';
      setRowInvalidState(row, vencInput, 'Lote vencido: no puede ser anterior a hoy.');
      ui.showToast(`El lote de ${esc(medName)} está vencido (${esc(fechaVenc)}). Verifica la fecha antes de dispensar.`, 'danger');
      vencInput.focus();
      return;
    }

    detalles.push({
      medicamentoPublicId: medId,
      cantidadEntregada: cantidad,
      lote: lote || null,
      fechaVencimientoLote: fechaVenc || null
    });

    totalUnidades += cantidad;
  }

  if (detalles.length === 0) {
    ui.showToast('Debes ingresar una cantidad a entregar mayor a 0 para al menos un medicamento.', 'warning');
    return;
  }

  const obsInput = container.querySelector('#dispenseObservations');
  const observaciones = obsInput ? obsInput.value.trim() : null;

  // Confirmación modal (preserva inputs digitados si ocurre un error en servidor)
  ui.showModal({
    title: 'Confirmar Dispensación Farmacéutica',
    message: `¿Estás seguro de registrar la entrega de <strong>${totalUnidades} unidad(es)</strong> correspondientes a ${detalles.length} medicamento(s)?<br><br><small class="text-muted">Esta acción es inmutable y quedará registrada en el sistema de trazabilidad legal.</small>`,
    confirmText: 'Sí, registrar entrega',
    cancelText: 'Revisar',
    onConfirm: async () => {
      const confirmModalBtn = document.querySelector('#modal-container .modal-backdrop.is-open:last-of-type .btn-confirm-modal');
      const btnConfirmDispense = container.querySelector('#btnConfirmDispense');

      ui.setButtonLoading(confirmModalBtn, true);
      ui.setButtonLoading(btnConfirmDispense, true);

      try {
        const payload = {
          recetaPublicId: r.recetaPublicId,
          observaciones: observaciones || null,
          detalles
        };

        const result = await pharmacyApi.registrarDispensacion(payload);
        pharmacyState.lastComprobante = result;

        ui.showToast('Dispensación registrada exitosamente.', 'success');
        renderComprobante(container, result);
        return true;
      } catch (err) {
        ui.setButtonLoading(confirmModalBtn, false);
        ui.setButtonLoading(btnConfirmDispense, false);

        const errMsg = err.message || 'Ocurrió un error al procesar la entrega de medicamentos.';
        const errBox = container.querySelector('#pharmacySubmitError');
        const errText = container.querySelector('#pharmacySubmitErrorMsg');
        if (errBox && errText) {
          errText.textContent = errMsg;
          errBox.style.display = 'flex';
          motion.pulseSubtle(errBox);
          errBox.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }
        ui.showToast(esc(errMsg), 'danger');
        return true;
      }
    }
  });
}

/**
 * Renderiza el comprobante de entrega tras una dispensación exitosa
 */
function renderComprobante(container, comprobante) {
  const mainContent = container.querySelector('#pharmacyMainContent');
  if (!mainContent) return;

  const r = pharmacyState.selectedReceta;

  mainContent.innerHTML = `
    <div class="card mb-6" style="padding: clamp(var(--space-4), 3vw, var(--space-6)); border-top: 6px solid var(--primary); max-width: 720px; margin: 0 auto;">
      
      <!-- Encabezado del comprobante -->
      <div class="flex flex-wrap items-start justify-between gap-3 pb-4 border-b mb-5">
        <div class="flex items-start gap-3 min-w-0">
          <div class="receipt-success-icon shrink-0" style="width: 44px; height: 44px; border-radius: var(--radius-full); background-color: var(--success-bg); color: var(--success); border: 1px solid var(--success); display: inline-flex; align-items: center; justify-content: center;">
            ${ui.icon('check-circle', 'icon icon--md')}
          </div>
          <div class="min-w-0">
            <span class="badge badge--confirmed text-xs font-bold mb-1">
              ${ui.icon('check', 'icon icon--sm')} Entrega Exitosa
            </span>
            <h2 class="text-xl font-bold m-0">Comprobante de Dispensación Farmacéutica</h2>
            <span class="text-xs text-muted font-mono break-all">No. ${esc(comprobante.publicId)}</span>
          </div>
        </div>

        <button type="button" class="btn btn-secondary btn--sm shrink-0" id="btnPrintReceipt">
          ${ui.icon('printer', 'icon icon--sm')}
          <span>Imprimir Comprobante</span>
        </button>
      </div>

      <!-- Datos generales de la entrega -->
      <div class="grid grid-cols-1 grid-cols-2-md gap-4 mb-6 text-xs p-4" style="background-color: var(--surface-2); border-radius: var(--radius-md);">
        <div>
          <strong class="text-muted block">Código de Reclamación:</strong>
          <span class="font-mono text-base font-bold text-primary">${esc(r?.codigoReclamacion || '—')}</span>
        </div>

        <div>
          <strong class="text-muted block">Fecha y Hora de Entrega:</strong>
          <span class="font-medium text-text">${esc(formatDateTime(comprobante.createdAt))}</span>
        </div>

        <div>
          <strong class="text-muted block">Paciente:</strong>
          <span class="font-medium text-text">${esc(r?.pacienteNombre || '—')}</span>
          <span class="text-muted font-mono block">${esc(r?.pacienteDocumento || '')}</span>
        </div>

        <div>
          <strong class="text-muted block">Sede y Regente Farmacéutico:</strong>
          <span class="font-medium text-text">${esc(comprobante.sedeNombre)}</span>
          <span class="text-muted block">Dispensado por: ${esc(comprobante.dispensadorNombre)}</span>
        </div>
      </div>

      <!-- Tabla de medicamentos entregados -->
      <h3 class="text-xs font-bold uppercase tracking-wider text-muted mb-2">Detalle de Medicamentos Entregados:</h3>
      <div class="table-container mb-6">
        <table class="table table--stacked text-xs">
          <thead>
            <tr>
              <th scope="col">Medicamento</th>
              <th scope="col">Lote INVIMA</th>
              <th scope="col">Vencimiento Lote</th>
              <th scope="col" class="text-center">Cantidad Entregada</th>
            </tr>
          </thead>
          <tbody>
            ${(comprobante.detalles || []).map(d => `
              <tr>
                <td data-label="Medicamento">
                  <div>
                    <strong>${esc(d.nombreComercial)}</strong>
                    <span class="text-muted block text-xs">${esc(d.presentacion)} — ${esc(d.concentracion)}</span>
                  </div>
                </td>
                <td data-label="Lote INVIMA"><span class="badge badge--neutral font-mono text-xs">${esc(d.lote || 'N/A')}</span></td>
                <td data-label="Vencimiento Lote">${esc(d.fechaVencimientoLote || 'N/A')}</td>
                <td data-label="Cantidad Entregada" class="text-center font-bold text-success text-sm">${esc(d.cantidadEntregada)}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>

      ${comprobante.observaciones ? `
        <div class="mb-6 p-3" style="background-color: var(--surface-2); border-radius: var(--radius-sm); font-size: var(--text-xs);">
          <strong class="text-muted block">Observaciones Registradas:</strong>
          <p class="m-0">${esc(comprobante.observaciones)}</p>
        </div>
      ` : ''}

      <!-- Acciones de cierre -->
      <div class="flex flex-wrap items-center justify-between gap-3 pt-4 border-t">
        <button type="button" class="btn btn-secondary" id="btnNewDispensation">
          ${ui.icon('search', 'icon icon--sm')}
          <span>Nueva Dispensación</span>
        </button>

        <button type="button" class="btn btn-primary" id="btnViewPrescriptionUpdated">
          ${ui.icon('pill', 'icon icon--sm')}
          <span>Ver Saldos Actualizados</span>
        </button>
      </div>

    </div>
  `;

  motion.stepTransition(mainContent, 'forward');
  const receiptIcon = mainContent.querySelector('.receipt-success-icon');
  if (receiptIcon) {
    motion.springPop(receiptIcon, { root: container });
    motion.drawSvg(receiptIcon.querySelector('svg'), { root: container, delay: 80 });
  }

  mainContent.querySelector('#btnPrintReceipt')?.addEventListener('click', () => {
    window.print();
  });

  mainContent.querySelector('#btnNewDispensation')?.addEventListener('click', () => {
    pharmacyState.selectedReceta = null;
    pharmacyState.prescriptions = [];
    pharmacyState.searchQuery = '';
    const searchInput = container.querySelector('#pharmacySearchInput');
    if (searchInput) searchInput.value = '';
    renderInitialState(container);
  });

  mainContent.querySelector('#btnViewPrescriptionUpdated')?.addEventListener('click', async () => {
    await loadAndDisplayWorkspace(comprobante.recetaPublicId, container);
  });
}
