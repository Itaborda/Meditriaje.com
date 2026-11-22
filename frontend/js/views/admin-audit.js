/**
 * MediTriaje 2.0 — Visor de Auditoría de Seguridad (admin-audit.js)
 * F2.7, RF-26, RNF-11, ADR-007, ADR-011, ADR-019.
 * Supervisión técnica e inmutable de la bitácora de eventos del sistema.
 * REGLA ESTRICTA: Cero contenido clínico ni diagnósticos individuales (ADR-007).
 */

import { api } from '../api.js';
import { motion } from '../motion.js';
import { ui, esc } from '../ui.js';

/** Fecha de hoy en formato YYYY-MM-DD en zona America/Bogota. */
function todayBogota() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date());
}

/** Formatea fecha y hora en zona America/Bogota. */
function formatDateTimeBogota(isoString) {
  if (!isoString) return '—';
  try {
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: true
    }).format(new Date(isoString));
  } catch {
    return isoString;
  }
}

/** Sanitiza el contenido de una celda CSV neutralizando fórmulas de hoja de cálculo (SEC-001 / DDE). */
function sanitizeCsvCell(val) {
  if (val === null || val === undefined) return '';
  const s = String(val).replace(/"/g, '""');
  if (/^[=+\-@\t\r]/.test(s)) {
    return `'${s}`;
  }
  return s;
}

/** Badge según el resultado del evento auditado. */
function resultadoBadge(resultado) {
  const res = (resultado || '').toUpperCase();
  if (res === 'EXITO') {
    return `<span class="badge badge--confirmed flex items-center gap-1">${ui.icon('check', 'icon icon--xs')} ÉXITO</span>`;
  }
  if (res === 'FALLO') {
    return `<span class="badge badge--cancelled flex items-center gap-1">${ui.icon('x', 'icon icon--xs')} FALLO</span>`;
  }
  if (res === 'BLOQUEADO') {
    return `<span class="badge badge--rescheduled flex items-center gap-1">${ui.icon('shield-alert', 'icon icon--xs')} BLOQUEADO</span>`;
  }
  return `<span class="badge badge--neutral">${esc(resultado)}</span>`;
}

/** Trunca identificadores largos conservando prefijo legible. */
function truncateText(text, maxLen = 14) {
  if (!text) return '—';
  if (text.length <= maxLen) return text;
  return text.substring(0, maxLen) + '…';
}

/**
 * Renderiza la pantalla del Visor de Auditoría de Seguridad.
 */
export async function renderAudit(container) {
  let page = 0;
  const size = 15;
  const hoy = todayBogota();
  let fechaDesde = '';
  let fechaHasta = '';
  let accionFiltro = '';
  let resultadoFiltro = '';
  let quickQuery = '';
  let lastData = null;
  let visibleList = [];

  container.innerHTML = `
    <div class="audit-view">
      <!-- Encabezado de la bitácora -->
      <div class="card mb-6">
        <div class="flex justify-between items-center flex-wrap gap-4">
          <div>
            <h2 class="text-xl font-bold mb-1 flex items-center gap-2">
              ${ui.icon('shield', 'icon icon--md text-primary')}
              <span>Bitácora de Auditoría de Seguridad</span>
            </h2>
            <p class="text-sm text-muted m-0">
              Supervisión técnica e inmutable de eventos, autenticaciones y operaciones del sistema (RF-26, RNF-11, ADR-019).
            </p>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <button type="button" id="btnExportAuditCsv" class="btn btn-secondary btn--sm" title="Exportar eventos visibles a CSV seguro">
              ${ui.icon('download', 'icon icon--sm')}
              <span>Exportar CSV</span>
            </button>
            <div class="badge badge--neutral flex items-center gap-1">
              ${ui.icon('lock', 'icon icon--xs')}
              <span>ADR-007: Cero acceso a datos clínicos</span>
            </div>
          </div>
        </div>

        <!-- Filtros de búsqueda -->
        <div class="mt-6 pt-4 border-t">
          <form id="auditFilterForm" class="grid grid-cols-1 grid-cols-2-md grid-cols-6-lg gap-3 items-end">
            <div class="form-group m-0">
              <label for="auditDesde" class="form-label text-xs">Desde:</label>
              <input type="date" id="auditDesde" class="form-input form-input--sm" max="${hoy}">
            </div>
            <div class="form-group m-0">
              <label for="auditHasta" class="form-label text-xs">Hasta:</label>
              <input type="date" id="auditHasta" class="form-input form-input--sm" max="${hoy}">
            </div>
            <div class="form-group m-0">
              <label for="auditAccion" class="form-label text-xs">Acción:</label>
              <select id="auditAccion" class="form-select form-select--sm">
                <option value="">Todas las acciones</option>
                <optgroup label="Autenticación y Sesión">
                  <option value="LOGIN_EXITOSO">LOGIN_EXITOSO</option>
                  <option value="LOGIN_FALLIDO">LOGIN_FALLIDO</option>
                  <option value="LOGOUT">LOGOUT</option>
                  <option value="CAMBIO_PASSWORD">CAMBIO_PASSWORD</option>
                  <option value="SOLICITUD_RESET_PASSWORD">SOLICITUD_RESET_PASSWORD</option>
                  <option value="RESET_PASSWORD_EXITOSO">RESET_PASSWORD_EXITOSO</option>
                  <option value="MFA_ACTIVADO">MFA_ACTIVADO</option>
                  <option value="MFA_DESACTIVADO">MFA_DESACTIVADO</option>
                </optgroup>
                <optgroup label="Administración e Infraestructura">
                  <option value="REGISTRO_PACIENTE">REGISTRO_PACIENTE</option>
                  <option value="ALTA_PROFESIONAL">ALTA_PROFESIONAL</option>
                  <option value="CAMBIO_ADMINISTRATIVO">CAMBIO_ADMINISTRATIVO</option>
                </optgroup>
                <optgroup label="Citas y Triaje">
                  <option value="RESERVA_CITA">RESERVA_CITA</option>
                  <option value="CONFIRMACION_CITA">CONFIRMACION_CITA</option>
                  <option value="CANCELACION_CITA">CANCELACION_CITA</option>
                  <option value="REPROGRAMACION_CITA">REPROGRAMACION_CITA</option>
                  <option value="TRIAJE_REALIZADO">TRIAJE_REALIZADO</option>
                  <option value="TRIAJE_EMERGENCIA">TRIAJE_EMERGENCIA</option>
                </optgroup>
                <optgroup label="Atención Clínica y Farmacia">
                  <option value="CREACION_ATENCION">CREACION_ATENCION</option>
                  <option value="ENMIENDA_ATENCION">ENMIENDA_ATENCION</option>
                  <option value="ACCESO_HISTORIA_CLINICA">ACCESO_HISTORIA_CLINICA</option>
                  <option value="CREACION_RECETA">CREACION_RECETA</option>
                  <option value="DISPENSACION_PARCIAL">DISPENSACION_PARCIAL</option>
                  <option value="DISPENSACION_TOTAL">DISPENSACION_TOTAL</option>
                  <option value="ACCESO_EMERGENCIA_BREAK_GLASS">ACCESO_EMERGENCIA_BREAK_GLASS</option>
                </optgroup>
              </select>
            </div>
            <div class="form-group m-0">
              <label for="auditResultado" class="form-label text-xs">Resultado:</label>
              <select id="auditResultado" class="form-select form-select--sm">
                <option value="">Todos los resultados</option>
                <option value="EXITO">ÉXITO</option>
                <option value="FALLO">FALLO</option>
                <option value="BLOQUEADO">BLOQUEADO</option>
              </select>
            </div>
            <div class="form-group m-0">
              <label for="auditQuickFilter" class="form-label text-xs">Filtro rápido:</label>
              <input type="search" id="auditQuickFilter" class="form-input form-input--sm" placeholder="Email, IP o recurso..." aria-label="Filtro rápido por correo, IP o recurso">
            </div>
            <div class="flex items-center gap-2">
              <button type="submit" id="btnFiltrarAudit" class="btn btn-primary btn--sm flex-1">
                ${ui.icon('search', 'icon icon--sm')}
                <span>Filtrar</span>
              </button>
              <button type="button" id="btnLimpiarAudit" class="btn btn-secondary btn--sm" title="Limpiar filtros" aria-label="Limpiar filtros de auditoría">
                ${ui.icon('x', 'icon icon--sm')}
              </button>
            </div>
          </form>
        </div>
      </div>

      <!-- Contenedor dinámico de resultados -->
      <div id="auditTableContainer" aria-live="polite"></div>
    </div>
  `;

  const filterForm = container.querySelector('#auditFilterForm');
  const desdeInput = container.querySelector('#auditDesde');
  const hastaInput = container.querySelector('#auditHasta');
  const accionSelect = container.querySelector('#auditAccion');
  const resultadoSelect = container.querySelector('#auditResultado');
  const quickFilterInput = container.querySelector('#auditQuickFilter');
  const btnLimpiar = container.querySelector('#btnLimpiarAudit');
  const btnExportCsv = container.querySelector('#btnExportAuditCsv');
  const tableContainer = container.querySelector('#auditTableContainer');

  function renderTableSkeleton() {
    tableContainer.innerHTML = `
      <div class="card p-0 mb-4 overflow-hidden">
        <div class="p-3 border-b bg-surface-2 flex flex-wrap justify-between items-center gap-2 text-xs">
          <span class="font-medium text-slate-700">Eventos de Bitácora Registrados</span>
          <span class="badge badge--neutral">Consultando...</span>
        </div>
        <div class="table-container m-0">
          <table class="table table--stacked">
            <thead>
              <tr>
                <th scope="col" style="width: 70px;">ID</th>
                <th scope="col">Fecha / Hora (Bogotá)</th>
                <th scope="col">Usuario</th>
                <th scope="col">Acción</th>
                <th scope="col">Tipo Recurso</th>
                <th scope="col">ID Recurso</th>
                <th scope="col">Resultado</th>
                <th scope="col">IP Origen</th>
              </tr>
            </thead>
            <tbody id="auditSkeletonTbody"></tbody>
          </table>
        </div>
      </div>
    `;
    ui.renderSkeleton(tableContainer.querySelector('#auditSkeletonTbody'), 'table-rows');
  }

  async function cargarEventos() {
    renderTableSkeleton();
    try {
      const params = {
        page,
        size
      };
      if (fechaDesde) params.desde = fechaDesde;
      if (fechaHasta) params.hasta = fechaHasta;
      if (accionFiltro) params.accion = accionFiltro;
      if (resultadoFiltro) params.resultado = resultadoFiltro;

      const data = await api.get('/admin/audit', params);
      lastData = data;
      renderTable(data);
    } catch (err) {
      ui.renderError(tableContainer, {
        title: 'Error al consultar la bitácora',
        message: err.message || 'No fue posible acceder a los registros de auditoría.',
        retryText: 'Reintentar',
        onRetry: () => cargarEventos()
      });
    }
  }

  function renderTable(data) {
    const rawList = data?.content || [];
    const q = quickQuery.trim().toLowerCase();
    const list = q ? rawList.filter(r =>
      String(r.usuarioEmail || '').toLowerCase().includes(q) ||
      String(r.ipOrigen || '').toLowerCase().includes(q) ||
      String(r.tipoRecurso || '').toLowerCase().includes(q) ||
      String(r.recursoPublicId || '').toLowerCase().includes(q) ||
      String(r.accion || r.tipoEvento || '').toLowerCase().includes(q)
    ) : rawList;

    visibleList = list;
    const total = data?.totalElements || 0;
    const totalPages = Math.max(1, data?.totalPages || 1);

    if (list.length === 0) {
      ui.renderEmpty(tableContainer, {
        icon: 'shield',
        title: 'Sin eventos de auditoría',
        description: 'No se encontraron eventos en la bitácora con los criterios de búsqueda especificados.'
      });
      return;
    }

    tableContainer.innerHTML = `
      <div class="card p-0 mb-4 overflow-hidden">
        <div class="p-3 border-b bg-surface-2 flex flex-wrap justify-between items-center gap-2 text-xs">
          <span class="font-medium text-slate-700">Eventos de Bitácora Registrados</span>
          <span id="auditTotalBadge" class="badge badge--neutral">Total: <strong>${total}</strong> registros</span>
        </div>
        <div class="table-container m-0">
          <table class="table table--stacked">
            <thead>
              <tr>
                <th scope="col" style="width: 70px;">ID</th>
                <th scope="col">Fecha / Hora (Bogotá)</th>
                <th scope="col">Usuario</th>
                <th scope="col">Acción</th>
                <th scope="col">Tipo Recurso</th>
                <th scope="col">ID Recurso</th>
                <th scope="col">Resultado</th>
                <th scope="col">IP Origen</th>
              </tr>
            </thead>
            <tbody>
              ${list.map(r => `
                <tr>
                  <td data-label="ID"><span class="font-mono text-xs text-muted">#${esc(r.id ?? '—')}</span></td>
                  <td data-label="Fecha / Hora"><span class="text-xs whitespace-nowrap">${formatDateTimeBogota(r.fechaHora)}</span></td>
                  <td data-label="Usuario">
                    <span class="text-xs font-semibold text-slate-800" title="${esc(r.usuarioEmail || '')}">
                      ${esc(r.usuarioEmail || 'Sistema / Anónimo')}
                    </span>
                  </td>
                  <td data-label="Acción">
                    <span class="badge badge--neutral text-xs font-mono">${esc(r.accion || r.tipoEvento || 'EVENTO')}</span>
                  </td>
                  <td data-label="Tipo Recurso">
                    <span class="text-xs text-muted font-medium">${esc(r.tipoRecurso || '—')}</span>
                  </td>
                  <td data-label="ID Recurso">
                    ${r.recursoPublicId ? `
                      <span class="font-mono text-xs text-muted" title="${esc(r.recursoPublicId)}">
                        ${esc(truncateText(r.recursoPublicId, 12))}
                      </span>
                    ` : '<span class="text-muted text-xs">—</span>'}
                  </td>
                  <td data-label="Resultado">${resultadoBadge(r.resultado)}</td>
                  <td data-label="IP Origen"><span class="font-mono text-xs text-muted">${esc(r.ipOrigen || '—')}</span></td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      </div>

      <!-- Barra de Paginación Accesible -->
      <div class="flex items-center justify-between gap-3 flex-wrap">
        <button type="button" id="btnAuditPrev" class="btn btn-secondary btn--sm" ${!data?.hasPrevious ? 'disabled' : ''}>
          ${ui.icon('arrow-left', 'icon icon--xs')}
          <span>Anterior</span>
        </button>
        <span class="text-xs text-muted">
          Página <strong>${page + 1}</strong> de <strong>${totalPages}</strong> (${total} eventos en total)
        </span>
        <button type="button" id="btnAuditNext" class="btn btn-secondary btn--sm" ${!data?.hasNext ? 'disabled' : ''}>
          <span>Siguiente</span>
          ${ui.icon('arrow-right', 'icon icon--xs')}
        </button>
      </div>
    `;

    motion.staggerCards(tableContainer.querySelectorAll('tbody tr'), { delayStep: 18, maxDelay: 150, root: container });
    motion.pulseSubtle(tableContainer.querySelector('#auditTotalBadge'));

    tableContainer.querySelector('#btnAuditPrev')?.addEventListener('click', () => {
      if (page > 0) {
        page--;
        cargarEventos();
      }
    });

    tableContainer.querySelector('#btnAuditNext')?.addEventListener('click', () => {
      if (data?.hasNext) {
        page++;
        cargarEventos();
      }
    });
  }

  btnExportCsv?.addEventListener('click', () => {
    if (!visibleList || visibleList.length === 0) {
      ui.showToast('No hay eventos cargados para exportar.', 'warning');
      return;
    }

    const rows = [
      ['BITACORA DE AUDITORIA DE SEGURIDAD — MEDITRIAJE 2.0'],
      ['Politica de Privacidad', 'ADR-007: Cero acceso a datos clinicos'],
      [],
      ['ID', 'FECHA_HORA_BOGOTA', 'USUARIO', 'ACCION', 'TIPO_RECURSO', 'ID_RECURSO', 'RESULTADO', 'IP_ORIGEN']
    ];

    visibleList.forEach(r => {
      rows.push([
        r.id ?? '',
        formatDateTimeBogota(r.fechaHora),
        r.usuarioEmail || 'Sistema / Anonimo',
        r.accion || r.tipoEvento || 'EVENTO',
        r.tipoRecurso || '',
        r.recursoPublicId || '',
        r.resultado || '',
        r.ipOrigen || ''
      ]);
    });

    const csvContent = '\uFEFF' + rows.map(row => row.map(cell => `"${sanitizeCsvCell(cell)}"`).join(',')).join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `auditoria_meditriaje_${hoy.replace(/-/g, '')}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);

    ui.showToast('Bitácora de auditoría exportada a CSV correctamente.', 'success');
  });

  quickFilterInput?.addEventListener('input', (e) => {
    quickQuery = e.target.value;
    if (lastData) renderTable(lastData);
  });

  filterForm.addEventListener('submit', (e) => {
    e.preventDefault();
    fechaDesde = desdeInput.value || '';
    fechaHasta = hastaInput.value || '';
    if (fechaDesde && fechaHasta && fechaDesde > fechaHasta) {
      ui.showToast('La fecha desde no puede ser posterior a la fecha hasta.', 'error');
      return;
    }
    accionFiltro = accionSelect.value || '';
    resultadoFiltro = resultadoSelect.value || '';
    page = 0;
    cargarEventos();
  });

  btnLimpiar.addEventListener('click', () => {
    filterForm.reset();
    fechaDesde = '';
    fechaHasta = '';
    accionFiltro = '';
    resultadoFiltro = '';
    quickQuery = '';
    page = 0;
    cargarEventos();
  });

  await cargarEventos();
}
