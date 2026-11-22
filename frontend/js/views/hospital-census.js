/**
 * MediTriaje 2.0 — Centro de Control Hospitalario y Censo de Camas (hospital-census.js)
 * Monitorización en tiempo real de capacidad hospitalaria, ocupación de camas,
 * disponibilidad por pabellón y desinfección (Fase H, H06, ADR-024).
 */

import { hospitalApi } from '../api.js';
import { auth } from '../auth.js';
import { motion } from '../motion.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';
import { openUniversalQrScanner } from '../components/qr-scanner-modal.js';

let censusState = {
  censusData: null,
  beds: [],
  filterEstado: '',
  filterArea: ''
};

function formatBedBadge(estado) {
  switch (estado) {
    case 'DISPONIBLE':
      return `<span class="badge badge--confirmed font-semibold">Disponible</span>`;
    case 'OCUPADA':
      return `<span class="badge badge--cancelled font-semibold">Ocupada</span>`;
    case 'LIMPIEZA':
      return `<span class="badge badge--warning font-semibold">En Limpieza</span>`;
    case 'MANTENIMIENTO':
      return `<span class="badge badge--neutral font-semibold">Mantenimiento</span>`;
    default:
      return `<span class="badge badge--dark">${esc(estado)}</span>`;
  }
}

export async function hospitalCensusView(container) {
  container.innerHTML = `
    <div style="max-width: var(--container); margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Encabezado del Centro de Control -->
      <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div>
          <div class="flex items-center gap-2 mb-1">
            <div class="empty-state-icon" style="margin: 0; width: 40px; height: 40px; background-color: var(--indigo-50, #eef2ff); color: var(--indigo-600, #4f46e5);">
              ${ui.icon('grid', 'icon icon--md')}
            </div>
            <h1 class="text-2xl font-bold m-0">Centro de Control Hospitalario</h1>
          </div>
          <p class="text-sm text-muted m-0">Censo de camas en tiempo real, tasas de ocupación por pabellón y gestión de capacidad instalada</p>
        </div>

        <div class="flex flex-wrap items-center gap-3">
          <!-- Hospital único: el servidor resuelve la sede; solo se muestra su nombre -->
          <span id="censusHospitalName" class="text-sm font-semibold text-muted"></span>

          ${auth.isAdmin ? `
            <button type="button" id="btnAddHospitalArea" class="btn btn-primary btn--sm flex items-center gap-1" title="Crear nueva área o pabellón hospitalario">
              ${ui.icon('plus', 'icon icon--sm')}
              <span>Nueva Área</span>
            </button>
            <button type="button" id="btnAddHospitalRoom" class="btn btn-secondary btn--sm flex items-center gap-1" title="Crear nueva habitación o sala">
              ${ui.icon('plus', 'icon icon--sm')}
              <span>Nueva Habitación</span>
            </button>
            <button type="button" id="btnAddHospitalBed" class="btn btn-secondary btn--sm flex items-center gap-1" title="Registrar nuevas camas en una habitación">
              ${ui.icon('bed', 'icon icon--sm')}
              <span>Nuevas Camas</span>
            </button>
          ` : ''}

          <button type="button" id="btnCensusScanQr" class="btn btn-secondary btn--sm flex items-center gap-1" title="Escanear código QR con cámara o imagen">
            ${ui.icon('camera', 'icon icon--sm')}
            <span>Escanear QR</span>
          </button>

          <button type="button" id="btnRefreshCensus" class="btn btn-ghost btn--sm flex items-center gap-1">
            ${ui.icon('refresh-cw', 'icon icon--sm')}
            <span>Actualizar</span>
          </button>
        </div>
      </div>

      <!-- Tarjetas de Métricas de Capacidad (KPIs) -->
      <div class="grid grid-cols-2 grid-cols-3-sm grid-cols-6-lg gap-3 mb-6" id="censusKpiGrid">
        <div class="card p-3 text-center">
          <div class="text-xs text-muted font-bold uppercase">Total Camas</div>
          <div class="text-2xl font-bold mt-1 tabular-nums" id="kpiTotalCamas">0</div>
        </div>

        <div class="card p-3 text-center" style="border-top: 3px solid var(--danger);">
          <div class="text-xs text-muted font-bold uppercase">Ocupadas</div>
          <div class="text-2xl font-bold mt-1 text-danger tabular-nums" id="kpiOcupadas">0</div>
        </div>

        <div class="card p-3 text-center" style="border-top: 3px solid var(--success);">
          <div class="text-xs text-muted font-bold uppercase">Disponibles</div>
          <div class="text-2xl font-bold mt-1 text-success tabular-nums" id="kpiDisponibles">0</div>
        </div>

        <div class="card p-3 text-center" style="border-top: 3px solid var(--warning);">
          <div class="text-xs text-muted font-bold uppercase">Limpieza</div>
          <div class="text-2xl font-bold mt-1 text-warning tabular-nums" id="kpiLimpieza">0</div>
        </div>

        <div class="card p-3 text-center" style="border-top: 3px solid var(--border-strong);">
          <div class="text-xs text-muted font-bold uppercase">Mantenimiento</div>
          <div class="text-2xl font-bold mt-1 text-muted tabular-nums" id="kpiMantenimiento">0</div>
        </div>

        <div class="card p-3 text-center" style="border-top: 3px solid var(--primary);">
          <div class="text-xs text-muted font-bold uppercase">% Ocupación</div>
          <div class="text-2xl font-bold mt-1 text-primary tabular-nums" id="kpiTasaOcupacion">0%</div>
        </div>
      </div>

      <!-- Pabellones y Áreas Hospitalarias -->
      <div class="card p-4 mb-6">
        <div class="flex items-center justify-between mb-3">
          <h2 class="text-base font-bold m-0">Ocupación por Áreas y Pabellones</h2>
          <span class="text-xs text-muted">Capacidad por servicio asistencial</span>
        </div>
        <div class="grid grid-cols-1 grid-cols-2-md grid-cols-4-lg gap-3" id="areaSummaryGrid">
          <p class="text-sm text-muted">Consultando áreas...</p>
        </div>
      </div>

      <!-- Barra de Filtros del Mapa de Camas -->
      <div class="card p-4 mb-6">
        <div class="flex flex-wrap items-center justify-between gap-4">
          <div class="flex flex-wrap items-center gap-3">
            <div class="flex items-center gap-2">
              <span class="text-xs font-semibold">Estado:</span>
              <select id="filterBedEstado" class="form-select form-input--sm" style="width: auto;">
                <option value="">Todos los estados</option>
                <option value="DISPONIBLE">Disponibles</option>
                <option value="OCUPADA">Ocupadas</option>
                <option value="LIMPIEZA">En Limpieza</option>
                <option value="MANTENIMIENTO">Mantenimiento</option>
              </select>
            </div>

            <div class="flex items-center gap-2">
              <span class="text-xs font-semibold">Área:</span>
              <select id="filterBedArea" class="form-select form-input--sm" style="width: auto;">
                <option value="">Todas las áreas</option>
              </select>
            </div>
          </div>

          <div class="text-xs text-muted" id="lblBedCount">Mostrando 0 camas</div>
        </div>
      </div>

      <!-- Grid Visual de Camas -->
      <div class="grid grid-cols-1 grid-cols-2-md grid-cols-4-lg gap-4" id="bedCardsGrid">
        <div class="p-8 text-center text-muted col-span-full">Cargando inventario de camas...</div>
      </div>

    </div>
  `;

  motion.staggerCards(container.querySelectorAll('#censusKpiGrid > .card'), { root: container });

  await loadData();

  document.getElementById('btnCensusScanQr')?.addEventListener('click', () => {
    openUniversalQrScanner();
  });

  document.getElementById('btnRefreshCensus').addEventListener('click', async () => {
    await loadData();
    ui.showToast('Censo hospitalario actualizado.', 'info');
  });

  document.getElementById('filterBedEstado').addEventListener('change', (e) => {
    censusState.filterEstado = e.target.value;
    renderBeds();
  });

  document.getElementById('filterBedArea').addEventListener('change', (e) => {
    censusState.filterArea = e.target.value;
    renderBeds();
  });

  if (auth.isAdmin) {
    document.getElementById('btnAddHospitalArea')?.addEventListener('click', openCreateAreaModal);
    document.getElementById('btnAddHospitalRoom')?.addEventListener('click', openCreateRoomModal);
    document.getElementById('btnAddHospitalBed')?.addEventListener('click', openCreateBedsModal);
  }
}

function openCreateAreaModal() {
  const body = `
    <form id="formCreateArea" class="flex flex-col gap-3">
      <div class="form-group">
        <label for="txtAreaCodigo" class="form-label font-semibold">Código <span class="text-danger">*</span></label>
        <input type="text" id="txtAreaCodigo" class="form-input" maxlength="30" placeholder="Ej. HOSP-P4" required autofocus>
      </div>
      <div class="form-group">
        <label for="txtAreaNombre" class="form-label font-semibold">Nombre del Área / Pabellón <span class="text-danger">*</span></label>
        <input type="text" id="txtAreaNombre" class="form-input" maxlength="100" placeholder="Ej. Hospitalización Piso 4" required>
      </div>
      <div class="form-group">
        <label for="selAreaTipo" class="form-label font-semibold">Tipo de Servicio <span class="text-danger">*</span></label>
        <select id="selAreaTipo" class="form-select" required>
          <option value="URGENCIAS">Urgencias</option>
          <option value="OBSERVACION">Observación</option>
          <option value="HOSPITALIZACION_GENERAL" selected>Hospitalización General</option>
          <option value="UCI_ADULTOS">UCI Adultos</option>
          <option value="UCI_PEDIATRICA">UCI Pediátrica</option>
          <option value="QUIROFANO">Quirófano</option>
          <option value="RECUPERACION">Recuperación</option>
          <option value="SALA_PARTOS">Sala de Partos</option>
        </select>
      </div>
      <div class="form-group m-0">
        <label for="txtAreaPiso" class="form-label font-semibold">Piso / Ubicación</label>
        <input type="text" id="txtAreaPiso" class="form-input" maxlength="20" placeholder="Ej. Piso 4">
      </div>
    </form>
  `;

  ui.showModal({
    title: 'Crear Área o Pabellón Hospitalario',
    body,
    confirmText: 'Crear Área',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      const codigo = document.getElementById('txtAreaCodigo')?.value.trim();
      const nombre = document.getElementById('txtAreaNombre')?.value.trim();
      const tipo = document.getElementById('selAreaTipo')?.value;
      const piso = document.getElementById('txtAreaPiso')?.value.trim();
      if (!codigo || !nombre || !tipo) {
        ui.showToast('Código, nombre y tipo son obligatorios.', 'warning');
        return false;
      }
      try {
        await hospitalApi.crearArea({ codigo, nombre, tipo, piso: piso || null });
        ui.showToast(`Área "${nombre}" creada con éxito.`, 'success');
        await loadData();
      } catch (err) {
        ui.showToast('Error creando área: ' + (err.message || 'Error de servidor'), 'error');
        return false;
      }
    }
  });
}

async function openCreateRoomModal() {
  let areas = [];
  try {
    areas = await hospitalApi.listarAreas();
  } catch (err) {
    ui.showToast('No se pudieron cargar las áreas: ' + (err.message || 'Error'), 'error');
    return;
  }
  if (!Array.isArray(areas) || areas.length === 0) {
    ui.showToast('Primero cree al menos un área hospitalaria.', 'warning');
    return;
  }

  const body = `
    <form id="formCreateRoom" class="flex flex-col gap-3">
      <div class="form-group">
        <label for="selRoomAreaId" class="form-label font-semibold">Área Hospitalaria <span class="text-danger">*</span></label>
        <select id="selRoomAreaId" class="form-select" required autofocus>
          ${areas.map(a => `<option value="${esc(a.publicId)}">${esc(a.nombre)} (${esc(a.codigo)})</option>`).join('')}
        </select>
      </div>
      <div class="form-group">
        <label for="txtRoomCodigo" class="form-label font-semibold">Código de Habitación / Sala <span class="text-danger">*</span></label>
        <input type="text" id="txtRoomCodigo" class="form-input" maxlength="30" placeholder="Ej. HAB-401" required>
      </div>
      <div class="form-group m-0">
        <label for="selRoomTipo" class="form-label font-semibold">Tipo <span class="text-danger">*</span></label>
        <select id="selRoomTipo" class="form-select" required>
          <option value="INDIVIDUAL">Individual</option>
          <option value="COMPARTIDA" selected>Compartida</option>
          <option value="AISLAMIENTO">Aislamiento</option>
          <option value="QUIROFANO">Quirófano</option>
          <option value="SALA_COMUN">Sala Común</option>
        </select>
      </div>
    </form>
  `;

  ui.showModal({
    title: 'Crear Habitación o Sala',
    body,
    confirmText: 'Crear Habitación',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      const areaPublicId = document.getElementById('selRoomAreaId')?.value;
      const codigo = document.getElementById('txtRoomCodigo')?.value.trim();
      const tipo = document.getElementById('selRoomTipo')?.value;
      if (!areaPublicId || !codigo || !tipo) {
        ui.showToast('Debe seleccionar área y especificar código y tipo.', 'warning');
        return false;
      }
      try {
        await hospitalApi.crearHabitacion(areaPublicId, { codigo, tipo });
        ui.showToast(`Habitación "${codigo}" creada con éxito.`, 'success');
        await loadData();
      } catch (err) {
        ui.showToast('Error creando habitación: ' + (err.message || 'Error de servidor'), 'error');
        return false;
      }
    }
  });
}

async function openCreateBedsModal() {
  let areas = [];
  try {
    areas = await hospitalApi.listarAreas();
  } catch (err) {
    ui.showToast('No se pudieron cargar las áreas y habitaciones: ' + (err.message || 'Error'), 'error');
    return;
  }
  const areasConHab = (areas || []).filter(a => Array.isArray(a.habitaciones) && a.habitaciones.length > 0);
  if (areasConHab.length === 0) {
    ui.showToast('Primero cree un área y una habitación para poder registrar camas.', 'warning');
    return;
  }

  const renderRoomOptions = (areaPubId) => {
    const area = areas.find(a => a.publicId === areaPubId);
    const rooms = area?.habitaciones || [];
    if (rooms.length === 0) {
      return '<option value="">Sin habitaciones en esta área</option>';
    }
    return rooms.map(r => `<option value="${esc(r.publicId)}">${esc(r.codigo)} (${esc(r.tipo)})</option>`).join('');
  };

  const firstAreaId = areasConHab[0].publicId;
  const body = `
    <form id="formCreateBeds" class="flex flex-col gap-3">
      <div class="form-group">
        <label for="selBedAreaId" class="form-label font-semibold">Área Hospitalaria <span class="text-danger">*</span></label>
        <select id="selBedAreaId" class="form-select" required autofocus>
          ${areas.map(a => `<option value="${esc(a.publicId)}" ${a.publicId === firstAreaId ? 'selected' : ''}>${esc(a.nombre)} (${esc(a.codigo)})</option>`).join('')}
        </select>
      </div>
      <div class="form-group">
        <label for="selBedRoomId" class="form-label font-semibold">Habitación / Sala <span class="text-danger">*</span></label>
        <select id="selBedRoomId" class="form-select" required>
          ${renderRoomOptions(firstAreaId)}
        </select>
      </div>
      <div class="grid grid-cols-2 gap-3">
        <div class="form-group m-0">
          <label for="txtBedCodigo" class="form-label font-semibold">Código / Prefijo <span class="text-danger">*</span></label>
          <input type="text" id="txtBedCodigo" class="form-input" maxlength="25" placeholder="Ej. CAMA-401" required>
        </div>
        <div class="form-group m-0">
          <label for="txtBedCantidad" class="form-label font-semibold">Cantidad (1-20)</label>
          <input type="number" id="txtBedCantidad" class="form-input" min="1" max="20" value="1" required>
        </div>
      </div>
      <p class="text-xs text-muted m-0">Si ingresa una cantidad mayor a 1, se generarán códigos numerados automáticamente (ej. CAMA-401-01, CAMA-401-02).</p>
    </form>
  `;

  ui.showModal({
    title: 'Registrar Camas Hospitalarias',
    body,
    confirmText: 'Crear Camas',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      const roomPublicId = document.getElementById('selBedRoomId')?.value;
      const codigo = document.getElementById('txtBedCodigo')?.value.trim();
      const cantidad = parseInt(document.getElementById('txtBedCantidad')?.value || '1', 10);
      if (!roomPublicId) {
        ui.showToast('Debe seleccionar una habitación válida.', 'warning');
        return false;
      }
      if (!codigo) {
        ui.showToast('El código o prefijo de la cama es obligatorio.', 'warning');
        return false;
      }
      try {
        const created = await hospitalApi.crearCamas(roomPublicId, { codigo, cantidad });
        const totalCreated = Array.isArray(created) ? created.length : cantidad;
        ui.showToast(`Se registraron ${totalCreated} cama(s) disponibles con éxito.`, 'success');
        await loadData();
      } catch (err) {
        ui.showToast('Error creando camas: ' + (err.message || 'Error de servidor'), 'error');
        return false;
      }
    }
  });

  document.getElementById('selBedAreaId')?.addEventListener('change', (e) => {
    const selRoom = document.getElementById('selBedRoomId');
    if (selRoom) {
      selRoom.innerHTML = renderRoomOptions(e.target.value);
    }
  });
}

async function loadData() {
  try {
    const [census, beds] = await Promise.all([
      hospitalApi.obtenerCenso(),
      hospitalApi.listarCamas()
    ]);

    const hospitalName = document.getElementById('censusHospitalName');
    if (hospitalName) hospitalName.textContent = census?.sedeNombre || '';

    censusState.censusData = census;
    censusState.beds = beds || [];

    updateKpis(census);
    renderAreas(census.areas);
    updateAreaFilter(census.areas);
    renderBeds();
  } catch (err) {
    ui.showToast('Censo hospitalario en sincronización.', 'info');
    const areaGrid = document.getElementById('areaSummaryGrid');
    if (areaGrid) {
      areaGrid.innerHTML = `
        <div class="alert alert--info" style="grid-column: 1 / -1;">
          ${ui.icon('info', 'icon alert-icon')}
          <div class="alert-content">
            <div class="alert-title">Capacidad hospitalaria en sincronización</div>
            <div>El módulo de censo en tiempo real para el hospital se encuentra en inicialización o sin camas activas registradas aún.</div>
          </div>
        </div>
      `;
    }
  }
}

function updateKpis(c) {
  motion.animateCounter(document.getElementById('kpiTotalCamas'), c.totalCamas ?? 0);
  motion.animateCounter(document.getElementById('kpiOcupadas'), c.ocupadas ?? 0);
  motion.animateCounter(document.getElementById('kpiDisponibles'), c.disponibles ?? 0);
  motion.animateCounter(document.getElementById('kpiLimpieza'), c.enLimpieza ?? 0);
  motion.animateCounter(document.getElementById('kpiMantenimiento'), c.enMantenimiento ?? 0);

  const tasaStr = String(c.tasaOcupacionPorcentaje ?? 0);
  const dotIdx = tasaStr.indexOf('.');
  const decimals = dotIdx >= 0 ? Math.min(2, tasaStr.length - dotIdx - 1) : 0;
  motion.animateCounter(document.getElementById('kpiTasaOcupacion'), c.tasaOcupacionPorcentaje ?? 0, {
    suffix: '%',
    decimals
  });
}

function renderAreas(areas) {
  const container = document.getElementById('areaSummaryGrid');
  if (!areas || areas.length === 0) {
    container.innerHTML = `<p class="text-sm text-muted m-0">No hay áreas configuradas en el hospital.</p>`;
    return;
  }

  container.innerHTML = areas.map(a => {
    const pct = a.totalCamas > 0 ? Math.round((a.ocupadas / a.totalCamas) * 100) : 0;
    return `
      <div class="card p-3 bg-slate-50 border">
        <div class="font-bold text-sm truncate">${esc(a.areaNombre)}</div>
        <div class="text-xs text-muted font-mono">${esc(a.areaCodigo)} · ${esc(a.tipoArea)}</div>
        
        <div class="flex items-center justify-between mt-2 text-xs">
          <span>Ocupación: <strong>${a.ocupadas}/${a.totalCamas}</strong></span>
          <span class="font-bold ${pct > 85 ? 'text-danger' : 'text-primary'}">${pct}%</span>
        </div>

        <div style="background-color: var(--border, #e2e8f0); height: 6px; border-radius: 9999px; margin-top: 6px; overflow: hidden;">
          <div style="background-color: ${pct > 85 ? 'var(--danger, #dc2626)' : 'var(--primary, #0d9488)'}; width: ${pct}%; height: 100%;"></div>
        </div>
      </div>
    `;
  }).join('');
}

function updateAreaFilter(areas) {
  const sel = document.getElementById('filterBedArea');
  sel.innerHTML = `<option value="">Todas las áreas</option>` + (areas || []).map(a => `
    <option value="${esc(a.areaNombre)}">${esc(a.areaNombre)}</option>
  `).join('');
}

function renderBeds() {
  const grid = document.getElementById('bedCardsGrid');
  const countLbl = document.getElementById('lblBedCount');

  let list = censusState.beds;
  if (censusState.filterEstado) {
    list = list.filter(b => b.estado === censusState.filterEstado);
  }
  if (censusState.filterArea) {
    list = list.filter(b => b.areaNombre === censusState.filterArea);
  }

  countLbl.textContent = `Mostrando ${list.length} de ${censusState.beds.length} camas`;

  if (list.length === 0) {
    grid.innerHTML = `<div class="p-8 text-center text-muted col-span-full">No se encontraron camas con los filtros seleccionados.</div>`;
    return;
  }

  grid.innerHTML = list.map(b => {
    const isOcupada = b.estado === 'OCUPADA';
    const isLimpieza = b.estado === 'LIMPIEZA';

    let cardBorder = 'border-slate-200';
    if (isOcupada) cardBorder = 'border-danger';
    else if (b.estado === 'DISPONIBLE') cardBorder = 'border-emerald-500';
    else if (isLimpieza) cardBorder = 'border-amber-500';

    return `
      <div class="card p-4 border flex flex-col justify-between ${cardBorder}">
        <div>
          <div class="flex items-center justify-between mb-2">
            <span class="font-bold text-base font-mono">${esc(b.codigo)}</span>
            ${formatBedBadge(b.estado)}
          </div>

          <div class="text-xs text-muted mb-1">
            <span class="font-semibold">Área:</span> ${esc(b.areaNombre)}
          </div>
          <div class="text-xs text-muted mb-3">
            <span class="font-semibold">Habitación:</span> ${esc(b.habitacionCodigo)}
          </div>

          ${isOcupada ? `
            <div class="p-2 rounded bg-red-50 border border-red-200 mb-2">
              <div class="text-xs font-bold text-red-900 truncate">
                ${esc(b.pacienteNombre || 'Paciente Asignado')}
              </div>
              <div class="text-xs text-red-700 font-mono truncate">
                Episodio: ${esc(b.episodioPublicId || '—')}
              </div>
            </div>
          ` : ''}
        </div>

        <div class="mt-3 pt-2 border-t flex flex-wrap items-center justify-end gap-1">
          ${isOcupada && b.episodioPublicId && (auth.isEnfermeria || auth.isProfesional) ? `
            <a href="#/hospital/beds/${esc(b.episodioPublicId)}" class="btn btn--xs btn-secondary" title="Trasladar paciente a otra cama">
              <span>Trasladar</span>
            </a>
          ` : ''}

          ${isOcupada && b.episodioPublicId && auth.isProfesional ? `
            <a href="#/hospital/discharge/${esc(b.episodioPublicId)}" class="btn btn--xs btn-primary" title="Registrar egreso y alta hospitalaria">
              <span>Egreso</span>
            </a>
          ` : ''}

          ${isLimpieza ? `
            <button type="button" class="btn btn--xs btn-primary btn-bed-action" data-id="${esc(b.publicId)}" data-status="DISPONIBLE" title="Liberar cama a Disponible tras desinfección">
              ${ui.icon('check', 'icon icon--xs')}
              <span>Marcar Disponible</span>
            </button>
          ` : ''}

          ${b.estado === 'DISPONIBLE' ? `
            <button type="button" class="btn btn--xs btn-ghost btn-bed-action" data-id="${esc(b.publicId)}" data-status="MANTENIMIENTO" title="Poner en mantenimiento">
              <span>Mantenimiento</span>
            </button>
          ` : ''}

          ${b.estado === 'MANTENIMIENTO' ? `
            <button type="button" class="btn btn--xs btn-ghost btn-bed-action" data-id="${esc(b.publicId)}" data-status="DISPONIBLE" title="Habilitar cama">
              <span>Habilitar</span>
            </button>
          ` : ''}
        </div>
      </div>
    `;
  }).join('');

  // Acciones de cambio de estado de cama
  grid.querySelectorAll('.btn-bed-action').forEach(btn => {
    btn.addEventListener('click', async () => {
      const camaId = btn.dataset.id;
      const status = btn.dataset.status;

      try {
        await hospitalApi.cambiarEstadoCama(camaId, status);
        ui.showToast(`Estado de cama actualizado a ${status}.`, 'success');
        await loadData();
      } catch (err) {
        ui.showToast('Error cambiando estado: ' + err.message, 'error');
      }
    });
  });
}
