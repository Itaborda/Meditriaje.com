/**
 * MediTriaje 2.0 — Vista de Recetas Médicas del Paciente (patient-prescriptions.js)
 * Listado de fórmulas médicas digitales, código de reclamación alfanumérico,
 * cálculo de vigencia, saldos de dispensación y trazabilidad farmacéutica (M8.2c, HU-08, F2.4, ADR-016).
 */

import { api, patientApi } from '../api.js';
import { motion } from '../motion.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

let prescriptionsState = {
  recetas: [],
  page: 0,
  size: 20,
  totalPages: 1,
  totalElements: 0,
  statusFilter: 'ALL', // 'ALL' | 'VIGENTE' | 'VENCIDA'
  dispensationDetails: new Map() // recetaPublicId -> RecetaDispensacionResponse
};

/**
 * Formatea una fecha ISO en formato colombiano
 */
function formatPrescriptionDate(isoString) {
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
 * Formatea una fecha y hora ISO en formato colombiano
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
 * Calcula el estado de vigencia de una receta
 * @param {string} createdAt Fecha de emisión
 * @param {number} vigenciaDias Días de vigencia
 * @returns {{ isVigente: boolean, fechaExpiracion: string, diasRestantes: number }}
 */
function checkPrescriptionValidity(createdAt, vigenciaDias = 30) {
  try {
    const createdDate = new Date(createdAt);
    const expireTime = createdDate.getTime() + (vigenciaDias * 24 * 60 * 60 * 1000);
    const now = Date.now();
    const diffMillis = expireTime - now;
    const diffDays = Math.ceil(diffMillis / (1000 * 60 * 60 * 24));

    const expDate = new Date(expireTime);
    const expFormatted = new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: 'numeric',
      month: 'short',
      year: 'numeric'
    }).format(expDate);

    return {
      isVigente: diffMillis > 0,
      fechaExpiracion: expFormatted,
      diasRestantes: diffDays
    };
  } catch {
    return { isVigente: false, fechaExpiracion: '—', diasRestantes: 0 };
  }
}

/**
 * Retorna el badge visual para el estado de dispensación
 */
function renderDispensationBadge(estado) {
  switch (estado) {
    case 'DISPENSADA_TOTAL':
      return `<span class="badge badge--confirmed font-semibold">${ui.icon('check', 'icon icon--sm')} Totalmente Entregada</span>`;
    case 'DISPENSADA_PARCIAL':
      return `<span class="badge badge--warning font-semibold">${ui.icon('clock', 'icon icon--sm')} Entrega Parcial</span>`;
    case 'PENDIENTE':
    default:
      return `<span class="badge badge--scheduled font-semibold">${ui.icon('pill', 'icon icon--sm')} Pendiente de Entrega</span>`;
  }
}

/**
 * Limpia el modo de impresión individual de receta
 */
function clearSinglePrintState() {
  document.body.classList.remove('is-printing-single');
  document.querySelectorAll('.receta-card.is-print-target').forEach(el => el.classList.remove('is-print-target'));
}

/**
 * Vista principal de Recetas Médicas del Paciente (#/patient/prescriptions)
 * @param {HTMLElement} container Contenedor DOM
 */
export async function patientPrescriptionsView(container) {
  prescriptionsState.page = 0;
  prescriptionsState.statusFilter = 'ALL';
  prescriptionsState.dispensationDetails.clear();
  clearSinglePrintState();

  container.innerHTML = `
    <div style="max-width: var(--container); margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      <nav class="breadcrumb mb-3" aria-label="Ruta de navegación">
        <a href="#/patient/dashboard" class="flex items-center gap-1">
          ${ui.icon('home', 'icon icon--sm')}
          <span>Inicio</span>
        </a>
        <span class="breadcrumb-separator" aria-hidden="true">&gt;</span>
        <span class="breadcrumb-current" aria-current="page">Mis Recetas Médicas</span>
      </nav>

      <!-- Encabezado con acción global de impresión -->
      <div class="flex flex-wrap items-center justify-between gap-4 mb-5">
        <div class="min-w-0">
          <div class="flex items-center gap-2 mb-1">
            <a href="#/patient/dashboard" class="btn btn-ghost btn--sm btn--icon-only" aria-label="Volver al panel" title="Volver al panel">
              ${ui.icon('arrow-left')}
            </a>
            <h1 class="text-2xl font-bold m-0">Mis Recetas Médicas</h1>
          </div>
          <p class="text-sm text-muted m-0">Prescripciones farmacológicas digitales con código de reclamación y seguimiento de entregas</p>
        </div>

        <button type="button" class="btn btn-secondary btn--sm" id="btnPrintPrescriptions" title="Imprimir fórmulas">
          ${ui.icon('printer', 'icon icon--sm')}
          <span>Imprimir recetas</span>
        </button>
      </div>

      <!-- Filtros rápidos por vigencia (persistentes para conservar foco) -->
      <div id="prescriptionsFilterBar" class="flex flex-wrap items-center gap-2 mb-5" role="group" aria-label="Filtrar recetas por vigencia" style="display: none;">
        <button type="button" class="btn btn--sm btn-primary" data-status-filter="ALL" aria-pressed="true">
          Todas
        </button>
        <button type="button" class="btn btn--sm btn-secondary" data-status-filter="VIGENTE" aria-pressed="false">
          Vigentes
        </button>
        <button type="button" class="btn btn--sm btn-secondary" data-status-filter="VENCIDA" aria-pressed="false">
          Vencidas
        </button>
      </div>

      <!-- Contenedor dinámico de recetas -->
      <div id="prescriptionsListContainer"></div>
    </div>
  `;

  container.querySelector('#btnPrintPrescriptions')?.addEventListener('click', () => {
    clearSinglePrintState();
    window.print();
  });

  container.querySelectorAll('button[data-status-filter]').forEach(btn => {
    btn.addEventListener('click', () => {
      prescriptionsState.statusFilter = btn.dataset.statusFilter || 'ALL';
      syncFilterButtons(container);
      renderPrescriptionsList(container);
    });
  });

  await loadPrescriptions(container);
}

/**
 * Sincroniza el estado visual y accesible de los botones de filtro
 */
function syncFilterButtons(container) {
  const recetas = prescriptionsState.recetas || [];
  let vigentes = 0;
  let vencidas = 0;
  for (const r of recetas) {
    if (checkPrescriptionValidity(r.createdAt, r.vigenciaDias).isVigente) vigentes++;
    else vencidas++;
  }

  const labels = {
    ALL: `Todas (${recetas.length})`,
    VIGENTE: `Vigentes (${vigentes})`,
    VENCIDA: `Vencidas (${vencidas})`
  };

  container.querySelectorAll('button[data-status-filter]').forEach(btn => {
    const key = btn.dataset.statusFilter;
    const isActive = prescriptionsState.statusFilter === key;
    btn.classList.toggle('btn-primary', isActive);
    btn.classList.toggle('btn-secondary', !isActive);
    btn.setAttribute('aria-pressed', isActive ? 'true' : 'false');
    if (labels[key]) {
      btn.textContent = labels[key];
    }
  });
}

/**
 * Consulta las recetas médicas desde la API
 */
async function loadPrescriptions(container) {
  const listContainer = container.querySelector('#prescriptionsListContainer');
  const filterBar = container.querySelector('#prescriptionsFilterBar');
  if (!listContainer) return;

  ui.renderSkeleton(listContainer, 'card');

  try {
    const res = await api.get('/patients/me/prescriptions', {
      page: prescriptionsState.page,
      size: prescriptionsState.size
    });

    prescriptionsState.recetas = res?.content || [];
    prescriptionsState.totalPages = Math.max(1, res?.totalPages || 1);
    prescriptionsState.totalElements = res?.totalElements ?? prescriptionsState.recetas.length;

    if (filterBar) {
      filterBar.style.display = prescriptionsState.recetas.length > 0 ? 'flex' : 'none';
      syncFilterButtons(container);
    }

    renderPrescriptionsList(container);
  } catch (err) {
    if (filterBar) filterBar.style.display = 'none';
    ui.renderError(listContainer, {
      title: 'No fue posible cargar tus recetas',
      message: esc(err.message || 'Ocurrió un error al obtener las recetas médicas.'),
      onRetry: () => loadPrescriptions(container)
    });
  }
}

/**
 * Renderiza la lista de recetas
 */
function renderPrescriptionsList(container) {
  const listContainer = container.querySelector('#prescriptionsListContainer');
  if (!listContainer) return;

  if (prescriptionsState.recetas.length === 0) {
    ui.renderEmpty(listContainer, {
      icon: 'pill',
      title: 'No tienes recetas médicas emitidas',
      description: 'Cuando un médico te formule medicamentos durante una consulta, tus prescripciones aparecerán aquí con su código de reclamación en farmacia.',
      actionText: 'Ver mis citas',
      onAction: () => router.navigate('/patient/appointments')
    });
    return;
  }

  const filteredRecetas = prescriptionsState.recetas.filter(receta => {
    if (prescriptionsState.statusFilter === 'ALL') return true;
    const { isVigente } = checkPrescriptionValidity(receta.createdAt, receta.vigenciaDias);
    return prescriptionsState.statusFilter === 'VIGENTE' ? isVigente : !isVigente;
  });

  if (filteredRecetas.length === 0) {
    ui.renderEmpty(listContainer, {
      icon: 'pill',
      title: prescriptionsState.statusFilter === 'VIGENTE'
        ? 'No tienes recetas vigentes en esta página'
        : 'No tienes recetas vencidas en esta página',
      description: 'Cambia el filtro para revisar todas tus prescripciones médicas registradas.',
      actionText: 'Ver todas las recetas',
      onAction: () => {
        prescriptionsState.statusFilter = 'ALL';
        syncFilterButtons(container);
        renderPrescriptionsList(container);
      }
    });
    return;
  }

  const summaryText = prescriptionsState.statusFilter === 'ALL'
    ? `${prescriptionsState.totalElements} receta(s) médica(s) registrada(s)`
    : `${filteredRecetas.length} receta(s) filtrada(s) en esta página`;

  listContainer.innerHTML = `
    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
      <span class="text-xs text-muted font-bold uppercase tracking-wider">
        ${esc(summaryText)}
      </span>
      <span class="badge badge--confirmed text-xs">
        ${ui.icon('shield', 'icon icon--sm')} Fórmulas Farmacológicas Inmutables
      </span>
    </div>

    <div class="flex flex-col gap-6">
      ${filteredRecetas.map(receta => {
        const vigenciaInfo = checkPrescriptionValidity(receta.createdAt, receta.vigenciaDias);
        const detalles = receta.detalles || [];
        const rawId = String(receta.publicId || '');
        const codigoReclamacion = 'REC-' + rawId.replace(/-/g, '').substring(0, 8).toUpperCase();

        return `
          <article class="card receta-card" id="receta-${esc(rawId)}" style="padding: clamp(var(--space-4), 3vw, var(--space-6)); border-top: 4px solid ${vigenciaInfo.isVigente ? 'var(--primary)' : 'var(--border)'};">
            
            <!-- Encabezado de la receta -->
            <div class="flex flex-wrap items-start justify-between gap-3 pb-3 border-b mb-4">
              <div class="min-w-0">
                <div class="flex flex-wrap items-center gap-2 mb-1">
                  <span class="badge ${vigenciaInfo.isVigente ? 'badge--confirmed' : 'badge--neutral'} text-xs font-bold">
                    ${vigenciaInfo.isVigente ? 'Vigente' : 'Vencida'}
                  </span>
                  <span class="text-xs text-muted font-mono">UUID: ${esc(rawId.slice(0, 8))}</span>
                </div>
                <h2 class="text-lg font-bold m-0">Receta Médica Digital</h2>
              </div>

              <div class="flex flex-col items-start gap-1 min-w-0">
                <span class="text-sm font-semibold block">${esc(receta.profesionalNombre || 'Profesional Médico')}</span>
                <span class="badge badge--neutral text-xs">${esc(receta.especialidadNombre || 'Medicina General')}</span>
              </div>
            </div>

            <!-- Código de Reclamación Farmacéutica (ADR-016) -->
            <div class="card p-4 mb-4" style="background-color: var(--primary-soft); border: 2px dashed var(--primary); border-radius: var(--radius-md);">
              <div class="flex flex-wrap items-center justify-between gap-3">
                <div class="flex items-center gap-3 min-w-0">
                  <div class="empty-state-icon shrink-0" style="margin: 0; width: 44px; height: 44px; background-color: var(--primary); color: var(--on-primary);">
                    ${ui.icon('pill', 'icon icon--md')}
                  </div>
                  <div class="min-w-0">
                    <span class="text-xs font-bold uppercase tracking-wider text-muted block">Código para Reclamar en Farmacia</span>
                    <strong class="font-mono text-2xl text-primary font-bold tracking-wider break-all">${esc(codigoReclamacion)}</strong>
                  </div>
                </div>

                <div class="flex flex-wrap items-center gap-2">
                  <button type="button" class="btn btn-secondary btn--sm btn-copy-claim" data-claim="${esc(codigoReclamacion)}" title="Copiar código para ventanilla">
                    ${ui.icon('copy', 'icon icon--sm')}
                    <span>Copiar código</span>
                  </button>
                  <button type="button" class="btn btn-primary btn--sm btn-view-dispensation" data-receta-id="${esc(rawId)}" aria-expanded="false" aria-controls="dispensation-box-${esc(rawId)}">
                    ${ui.icon('activity', 'icon icon--sm')}
                    <span class="btn-dispensation-label">Estado de Entrega</span>
                  </button>
                </div>
              </div>
            </div>

            <!-- Metadatos de Fecha y Vigencia -->
            <div class="grid grid-cols-1 grid-cols-3-md gap-3 mb-4 p-3" style="background-color: var(--surface-2); border-radius: var(--radius-md); font-size: var(--text-xs);">
              <div>
                <strong class="text-muted block">Fecha de Emisión:</strong>
                <span class="font-medium text-text">${esc(formatPrescriptionDate(receta.createdAt))}</span>
              </div>

              <div>
                <strong class="text-muted block">Vigencia Total:</strong>
                <span class="font-medium text-text">${esc(receta.vigenciaDias)} días</span>
              </div>

              <div>
                <strong class="text-muted block">Vencimiento:</strong>
                <span class="font-medium text-text">${esc(vigenciaInfo.fechaExpiracion)}</span>
                ${vigenciaInfo.isVigente ? `
                  <span class="text-success font-semibold ml-1">(${esc(vigenciaInfo.diasRestantes)} días restantes)</span>
                ` : '<span class="text-danger font-semibold ml-1">(Expirada)</span>'}
              </div>
            </div>

            <!-- Tabla de Medicamentos Prescritos -->
            <div class="mb-4">
              <h3 class="text-xs font-bold uppercase tracking-wider text-muted mb-2">
                Medicamentos Prescritos (${detalles.length}):
              </h3>

              <div class="table-container">
                <table class="table table--stacked">
                  <thead>
                    <tr>
                      <th scope="col">Medicamento</th>
                      <th scope="col">Concentración / Presentación</th>
                      <th scope="col">Dosis y Frecuencia</th>
                      <th scope="col">Duración / Cantidad</th>
                      <th scope="col">Indicaciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    ${detalles.map(item => `
                      <tr>
                        <td data-label="Medicamento">
                          <div class="min-w-0">
                            <strong class="block">${esc(item.nombreComercial)}</strong>
                            <span class="text-xs text-muted">Principio activo: ${esc(item.principioActivo)}</span>
                          </div>
                        </td>
                        <td data-label="Concentración">
                          <div>
                            <span class="badge badge--neutral">${esc(item.concentracion)}</span>
                            <span class="text-xs block text-muted mt-1">${esc(item.presentacion)}</span>
                          </div>
                        </td>
                        <td data-label="Dosis / Frecuencia">
                          <div>
                            <strong>${esc(item.dosis)}</strong>
                            <span class="text-xs block text-muted">${esc(item.frecuencia)}</span>
                          </div>
                        </td>
                        <td data-label="Duración / Cantidad">
                          <div>
                            <span class="font-medium">${esc(item.duracionDias)} días</span>
                            <span class="text-xs block text-muted">${esc(item.cantidad)} unidad(es)</span>
                          </div>
                        </td>
                        <td data-label="Indicaciones" class="text-xs">
                          <span>${esc(item.indicaciones || 'Tomar según indicación médica.')}</span>
                        </td>
                      </tr>
                    `).join('')}
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Panel Expandible de Estado de Dispensación Farmacéutica (ADR-016) -->
            <div id="dispensation-box-${esc(rawId)}" class="mt-4 pt-4 border-t" style="display: none;"></div>

            <!-- Pie de Receta -->
            <div class="flex flex-wrap items-center justify-between gap-3 pt-3 border-t">
              <span class="text-xs text-muted">
                Fórmula médica electrónica verificada. Válida para reclamación y dispensación farmacéutica.
              </span>

              <button type="button" class="btn btn-secondary btn--sm btn-print-single" data-receta-id="${esc(rawId)}">
                ${ui.icon('printer', 'icon icon--sm')}
                <span>Imprimir esta receta</span>
              </button>
            </div>

          </article>
        `;
      }).join('')}
    </div>

    ${prescriptionsState.totalPages > 1 ? `
      <div class="pagination-bar" role="navigation" aria-label="Paginación de recetas médicas">
        <button type="button" class="btn btn-secondary btn--sm" id="btnPrescriptionsPrev" ${prescriptionsState.page <= 0 ? 'disabled' : ''}>
          ${ui.icon('chevron-left', 'icon icon--sm')}
          <span>Anterior</span>
        </button>
        <span class="text-xs text-muted">
          Página <strong>${prescriptionsState.page + 1}</strong> de <strong>${prescriptionsState.totalPages}</strong> (${prescriptionsState.totalElements} recetas)
        </span>
        <button type="button" class="btn btn-secondary btn--sm" id="btnPrescriptionsNext" ${prescriptionsState.page + 1 >= prescriptionsState.totalPages ? 'disabled' : ''}>
          <span>Siguiente</span>
          ${ui.icon('chevron-right', 'icon icon--sm')}
        </button>
      </div>
    ` : ''}
  `;

  // Listener para botones de copiado de código de reclamación
  listContainer.querySelectorAll('.btn-copy-claim').forEach(btn => {
    btn.addEventListener('click', async () => {
      const claimCode = btn.dataset.claim;
      motion.pulseSubtle(btn);
      try {
        await navigator.clipboard.writeText(claimCode);
        ui.showToast(`Código ${esc(claimCode)} copiado al portapapeles.`, 'success');
      } catch {
        const temp = document.createElement('input');
        temp.value = claimCode;
        document.body.appendChild(temp);
        temp.select();
        document.execCommand('copy');
        temp.remove();
        ui.showToast(`Código ${esc(claimCode)} copiado al portapapeles.`, 'success');
      }
    });
  });

  // Listener para consultar / expandir estado de dispensación (con caché)
  listContainer.querySelectorAll('.btn-view-dispensation').forEach(btn => {
    btn.addEventListener('click', async () => {
      const recetaId = btn.dataset.recetaId;
      const safeId = typeof CSS !== 'undefined' && typeof CSS.escape === 'function' ? CSS.escape(recetaId) : recetaId;
      const box = listContainer.querySelector(`#dispensation-box-${safeId}`);
      const labelEl = btn.querySelector('.btn-dispensation-label');
      if (!box) return;

      const isExpanded = btn.getAttribute('aria-expanded') === 'true' || box.style.display === 'block';
      if (isExpanded) {
        box.style.display = 'none';
        btn.setAttribute('aria-expanded', 'false');
        btn.classList.remove('btn-secondary');
        btn.classList.add('btn-primary');
        if (labelEl) labelEl.textContent = 'Estado de Entrega';
        return;
      }

      box.style.display = 'block';
      btn.setAttribute('aria-expanded', 'true');
      btn.classList.remove('btn-primary');
      btn.classList.add('btn-secondary');
      if (labelEl) labelEl.textContent = 'Ocultar entrega';

      await renderDispensationDetails(box, recetaId, container, false);
    });
  });

  // Asignar listeners de impresión individual (solo imprime la receta seleccionada)
  listContainer.querySelectorAll('.btn-print-single').forEach(btn => {
    btn.addEventListener('click', () => {
      const card = btn.closest('.receta-card');
      clearSinglePrintState();
      if (card) {
        card.classList.add('is-print-target');
        document.body.classList.add('is-printing-single');
      }

      let cleaned = false;
      const cleanup = () => {
        if (cleaned) return;
        cleaned = true;
        clearSinglePrintState();
        window.removeEventListener('afterprint', cleanup);
      };

      window.addEventListener('afterprint', cleanup, { once: true });
      setTimeout(cleanup, 2500);
      window.print();
    });
  });

  listContainer.querySelector('#btnPrescriptionsPrev')?.addEventListener('click', () => {
    if (prescriptionsState.page > 0) {
      prescriptionsState.page--;
      loadPrescriptions(container);
    }
  });

  listContainer.querySelector('#btnPrescriptionsNext')?.addEventListener('click', () => {
    if (prescriptionsState.page + 1 < prescriptionsState.totalPages) {
      prescriptionsState.page++;
      loadPrescriptions(container);
    }
  });

  motion.staggerCards('.receta-card', { root: container });
}

/**
 * Renderiza el HTML del detalle de dispensación dentro de su caja
 */
function renderDispensationMarkup(boxElement, dispData, recetaPublicId, rootContainer) {
  const items = dispData.items || [];
  const entregas = dispData.entregasPrevias || [];

  boxElement.innerHTML = `
    <div class="card p-4" style="background-color: var(--surface-1); border-left: 4px solid var(--primary);">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div class="flex flex-wrap items-center gap-2">
          <h3 class="text-sm font-bold uppercase tracking-wider m-0">Trazabilidad de Farmacia</h3>
          ${renderDispensationBadge(dispData.estadoDispensacion)}
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <span class="text-xs text-muted">
            Código: <strong class="font-mono text-primary">${esc(dispData.codigoReclamacion)}</strong>
          </span>
          <button type="button" class="btn btn-ghost btn--xs btn-refresh-dispensation" title="Actualizar estado de entregas">
            ${ui.icon('refresh-cw', 'icon icon--xs')}
            <span>Actualizar</span>
          </button>
        </div>
      </div>

      <!-- Tabla de saldos por medicamento -->
      <h4 class="text-xs font-bold text-muted uppercase tracking-wider mb-2">Saldos de Medicamentos:</h4>
      <div class="table-container mb-4">
        <table class="table table--stacked text-xs">
          <thead>
            <tr>
              <th scope="col">Medicamento</th>
              <th scope="col" class="text-center">Prescrito</th>
              <th scope="col" class="text-center">Entregado</th>
              <th scope="col" class="text-center">Saldo Pendiente</th>
              <th scope="col">Estado</th>
            </tr>
          </thead>
          <tbody>
            ${items.map(item => `
              <tr>
                <td data-label="Medicamento">
                  <div>
                    <strong>${esc(item.nombreComercial)}</strong>
                    <span class="text-muted block">${esc(item.presentacion)} — ${esc(item.concentracion)}</span>
                  </div>
                </td>
                <td data-label="Prescrito" class="text-center font-medium">${esc(item.cantidadPrescrita)}</td>
                <td data-label="Entregado" class="text-center font-medium text-success">${esc(item.cantidadDispensada)}</td>
                <td data-label="Saldo Pendiente" class="text-center font-bold ${item.saldoPendiente > 0 ? 'text-primary' : 'text-muted'}">
                  ${esc(item.saldoPendiente)}
                </td>
                <td data-label="Estado">${renderDispensationBadge(item.estado)}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>

      <!-- Historial de entregas realizadas -->
      <h4 class="text-xs font-bold text-muted uppercase tracking-wider mb-2">Entregas Realizadas (${entregas.length}):</h4>
      ${entregas.length === 0 ? `
        <p class="text-xs text-muted m-0 p-3" style="background-color: var(--surface-2); border-radius: var(--radius-sm);">
          No se han registrado entregas todavía para esta receta. Preséntate en ventanilla con tu código de reclamación.
        </p>
      ` : `
        <div class="flex flex-col gap-3">
          ${entregas.map(ent => `
            <div class="p-3" style="background-color: var(--surface-2); border-radius: var(--radius-sm); border: 1px solid var(--border);">
              <div class="flex flex-wrap items-center justify-between gap-2 text-xs mb-2">
                <span><strong>Fecha:</strong> ${esc(formatDateTime(ent.createdAt))}</span>
                <span><strong>Sede:</strong> ${esc(ent.sedeNombre)}</span>
                <span class="text-muted"><strong>Dispensador:</strong> ${esc(ent.dispensadorNombre)}</span>
              </div>
              ${ent.observaciones ? `
                <p class="text-xs text-muted mb-2"><strong>Observaciones:</strong> ${esc(ent.observaciones)}</p>
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

  const refreshBtn = boxElement.querySelector('.btn-refresh-dispensation');
  refreshBtn?.addEventListener('click', () => {
    motion.spinOnce(refreshBtn);
    renderDispensationDetails(boxElement, recetaPublicId, rootContainer, true);
  });

  motion.revealPanel(boxElement, { root: rootContainer });
}

/**
 * Consulta y renderiza el desglose de saldos y entregas previas de una receta
 */
async function renderDispensationDetails(boxElement, recetaPublicId, rootContainer, forceRefresh = false) {
  if (!forceRefresh && prescriptionsState.dispensationDetails.has(recetaPublicId)) {
    const cached = prescriptionsState.dispensationDetails.get(recetaPublicId);
    renderDispensationMarkup(boxElement, cached, recetaPublicId, rootContainer);
    return;
  }

  ui.renderSkeleton(boxElement, 'card');

  try {
    const dispData = await patientApi.consultarDispensacionReceta(recetaPublicId);
    prescriptionsState.dispensationDetails.set(recetaPublicId, dispData);
    renderDispensationMarkup(boxElement, dispData, recetaPublicId, rootContainer);
  } catch (err) {
    ui.renderError(boxElement, {
      title: 'No fue posible obtener el estado de dispensación',
      message: esc(err.message || 'Error al consultar datos de farmacia.'),
      onRetry: () => renderDispensationDetails(boxElement, recetaPublicId, rootContainer, true)
    });
  }
}
