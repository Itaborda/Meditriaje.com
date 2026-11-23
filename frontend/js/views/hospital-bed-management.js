/**
 * MediTriaje 2.0 — Asignación y Traslados Intrahospitalarios (hospital-bed-management.js)
 * Asignación de cama inicial y traslados longitudinales entre pabellones y unidades (H01, H02, H03).
 */

import { hospitalApi, emergencyApi } from '../api.js';
import { motion } from '../motion.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

function formatDateTime(iso) {
  if (!iso) return '—';
  try {
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export async function hospitalBedManagementView(container, { params }) {
  const episodePublicId = params?.id || params?.episodioId;

  if (!episodePublicId) {
    ui.renderEmpty(container, {
      title: 'Episodio no especificado',
      description: 'Identificador de episodio hospitalario no encontrado.',
      actionText: 'Volver al Centro de Control',
      onAction: () => router.navigate('/hospital/census')
    });
    return;
  }

  container.innerHTML = `
    <div style="max-width: 850px; margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Navegación -->
      <div class="mb-4">
        <a href="#/hospital/census" class="btn btn-ghost btn--sm flex items-center gap-1" style="width: fit-content;">
          ${ui.icon('arrow-left', 'icon icon--sm')}
          <span>Volver al Centro de Control</span>
        </a>
      </div>

      <!-- Resumen del Paciente y Episodio -->
      <div class="mb-6" id="bedEpSummaryCard"></div>

      <!-- Historial de Movimientos y Traslados Longitudinales -->
      <div class="card p-6 mb-6">
        <div class="flex flex-wrap items-center justify-between gap-2 mb-4">
          <h2 class="text-base font-bold m-0 flex items-center gap-2">
            ${ui.icon('activity', 'icon icon--sm text-primary')}
            <span>Timeline de Movimientos y Traslados Intrahospitalarios</span>
          </h2>
          <span class="badge badge--neutral text-xs">Ledger inmutable (H03)</span>
        </div>
        <div id="movementsTimelineContainer"></div>
      </div>

      <!-- Formulario de Asignación / Traslado -->
      <div class="card p-6">
        <h2 class="text-lg font-bold mb-4" id="formBedTitle">Asignación de Cama Hospitalaria</h2>

        <form id="bedActionForm" novalidate>
          
          <div class="form-group mb-4">
            <label for="selCamaDestino" class="form-label font-semibold">Seleccionar Cama Disponible <span class="required">*</span></label>
            <select id="selCamaDestino" class="form-select" required>
              <option value="">Cargando camas disponibles...</option>
            </select>
          </div>

          <div class="form-group mb-6">
            <label for="txtMotivoCama" class="form-label font-semibold">Motivo Asistencial / Indicación de Ubicación <span class="required">*</span></label>
            <textarea id="txtMotivoCama" class="form-textarea" rows="3" required placeholder="Justificación médica del ingreso a sala o traslado..."></textarea>
          </div>

          <div class="flex flex-wrap items-center justify-end gap-3">
            <a href="#/hospital/census" class="btn btn-ghost">Cancelar</a>
            <button type="submit" id="btnSubmitBedAction" class="btn btn-primary flex items-center gap-2">
              ${ui.icon('check', 'icon icon--sm')}
              <span id="btnSubmitBedActionText">Asignar Cama</span>
            </button>
          </div>

        </form>
      </div>

    </div>
  `;

  const summaryCard = container.querySelector('#bedEpSummaryCard');
  const movementsContainer = container.querySelector('#movementsTimelineContainer');
  ui.renderSkeleton(summaryCard, 'card');
  ui.renderSkeleton(movementsContainer, 'card');

  let episodio = null;
  let camasDisponibles = [];
  let movimientos = [];
  let tieneCamaAsignada = false;

  async function loadInitialData() {
    try {
      episodio = await emergencyApi.obtenerDetalleEpisodio(episodePublicId);
      renderSummary(episodio, container);

      movimientos = await hospitalApi.listarMovimientos(episodePublicId);
      renderMovements(movimientos, container);

      tieneCamaAsignada = movimientos && movimientos.length > 0;
      if (tieneCamaAsignada) {
        document.getElementById('formBedTitle').textContent = 'Traslado Intrahospitalario de Paciente';
        document.getElementById('btnSubmitBedActionText').textContent = 'Confirmar Traslado';
      }

      // Cargar camas disponibles del hospital agrupadas por área
      const todasCamas = await hospitalApi.listarCamas();
      camasDisponibles = (todasCamas || []).filter(c => c.estado === 'DISPONIBLE');

      const selCama = document.getElementById('selCamaDestino');
      if (camasDisponibles.length > 0) {
        const grouped = new Map();
        camasDisponibles.forEach(c => {
          const area = c.areaNombre || 'Otras Áreas';
          if (!grouped.has(area)) grouped.set(area, []);
          grouped.get(area).push(c);
        });

        const optgroupsHtml = Array.from(grouped.entries()).map(([areaNombre, camas]) => `
          <optgroup label="${esc(areaNombre)}">
            ${camas.map(c => `
              <option value="${esc(c.publicId)}">${esc(c.codigo)} — Hab. ${esc(c.habitacionCodigo || '—')}</option>
            `).join('')}
          </optgroup>
        `).join('');

        selCama.innerHTML = `<option value="">Seleccione una cama...</option>${optgroupsHtml}`;
      } else {
        selCama.innerHTML = `<option value="">No hay camas disponibles en el hospital</option>`;
      }
    } catch (err) {
      ui.showToast('Error cargando información: ' + err.message, 'error');
      ui.renderError(summaryCard, {
        title: 'No fue posible cargar los datos del episodio',
        message: err.message || 'Error al comunicarse con el servidor.',
        onRetry: loadInitialData
      });
      ui.renderEmpty(movementsContainer, {
        icon: 'activity',
        title: 'Sin historial disponible',
        description: 'No se pudieron consultar los movimientos en este momento.'
      });
    }
  }

  await loadInitialData();

  // Envío del formulario
  const form = document.getElementById('bedActionForm');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const camaId = document.getElementById('selCamaDestino').value;
    const motivo = document.getElementById('txtMotivoCama').value.trim();

    if (!camaId) {
      ui.showToast('Debe seleccionar una cama disponible.', 'warning');
      return;
    }

    if (!motivo) {
      ui.showToast('El motivo es obligatorio.', 'warning');
      return;
    }

    const btn = document.getElementById('btnSubmitBedAction');
    ui.setButtonLoading(btn, true);

    try {
      if (tieneCamaAsignada) {
        // Traslado
        await hospitalApi.trasladarPaciente(episodePublicId, {
          camaDestinoPublicId: camaId,
          motivoTraslado: motivo
        });
        ui.showToast('Traslado intrahospitalario registrado con éxito.', 'success');
      } else {
        // Asignación inicial
        await hospitalApi.asignarCama(episodePublicId, {
          camaPublicId: camaId,
          motivoAsignacion: motivo
        });
        ui.showToast('Cama asignada exitosamente.', 'success');
      }
      router.navigate('#/hospital/census');
    } catch (err) {
      ui.setButtonLoading(btn, false);
      ui.showToast('Error en asignación/traslado: ' + (err.message || 'Error de servidor'), 'error');
    }
  });
}

function renderSummary(ep, rootContainer) {
  const card = document.getElementById('bedEpSummaryCard');
  if (!card) return;
  const patientName = ep.pacienteNombre || ep.pacienteNombreCompleto || 'Paciente No Identificado';
  const episodeId = ep.episodioPublicId || ep.publicId || '—';
  card.innerHTML = `
    <div class="card p-6">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 class="text-xl font-bold m-0">${esc(patientName)}</h1>
          <div class="text-xs text-muted mt-1 font-mono">
            Episodio: ${esc(episodeId)} · Sede: ${esc(ep.sedeNombre || 'Valledupar')}
          </div>
        </div>
        <span class="badge ${ep.estado === 'HOSPITALIZADO' ? 'badge--in-progress' : 'badge--scheduled'} font-bold">
          ${esc(ep.estado)}
        </span>
      </div>
    </div>
  `;
  motion.stepTransition(card, 'forward');
}

function renderMovements(list, rootContainer) {
  const container = document.getElementById('movementsTimelineContainer');
  if (!container) return;
  if (!list || list.length === 0) {
    container.innerHTML = `<p class="text-sm text-muted m-0">No se registran movimientos ni camas previas en este episodio.</p>`;
    return;
  }

  container.innerHTML = `
    <div class="timeline">
      ${list.map((m, idx) => {
        const fecha = m.fechaMovimiento || m.fechaIngreso || m.createdAt;
        const motivo = m.motivoTraslado || m.motivo || m.motivoAsignacion || '—';
        return `
          <div class="timeline-item">
            <div class="timeline-marker"></div>
            <div class="card p-4 bg-surface-2 border flex flex-wrap items-start justify-between gap-2">
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2 mb-1">
                  <span class="badge badge--dark text-xs">#${idx + 1}</span>
                  <span class="text-xs text-muted">${formatDateTime(fecha)}</span>
                  ${m.esActual ? '<span class="badge badge--confirmed text-xs">Ubicación Actual</span>' : ''}
                </div>
                <div class="font-bold text-sm">
                  ${m.camaOrigenCodigo ? esc(m.camaOrigenCodigo) + ' (' + esc(m.areaOrigenNombre || '—') + ')' : 'Admisión'}
                  &rarr;
                  ${esc(m.camaDestinoCodigo || '—')} (${esc(m.areaDestinoNombre || '—')})
                </div>
                <div class="text-xs text-muted mt-1">
                  <strong>Motivo:</strong> ${esc(motivo)}
                </div>
              </div>
              ${m.registradoPorNombre ? `<span class="text-xs text-muted font-mono">${esc(m.registradoPorNombre)}</span>` : ''}
            </div>
          </div>
        `;
      }).join('')}
    </div>
  `;

  motion.staggerCards(container.querySelectorAll('.timeline-item'), {
    delayStep: 30,
    maxDelay: 180,
    root: rootContainer || container
  });
}
