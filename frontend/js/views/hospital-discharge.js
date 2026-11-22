/**
 * MediTriaje 2.0 — Egreso y Alta Hospitalaria Médica (hospital-discharge.js)
 * Cierre asistencial, registro de epicrisis, destino del egreso y liberación automática de cama (H05).
 */

import { hospitalApi, emergencyApi } from '../api.js';
import { motion } from '../motion.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

export async function hospitalDischargeView(container, { params }) {
  const episodePublicId = params?.id || params?.episodioId;

  if (!episodePublicId) {
    ui.renderEmpty(container, {
      title: 'Episodio no especificado',
      description: 'Identificador de episodio de hospitalización no encontrado.',
      actionText: 'Volver al Centro de Control',
      onAction: () => router.navigate('/hospital/census')
    });
    return;
  }

  container.innerHTML = `
    <div style="max-width: 800px; margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Navegación -->
      <div class="mb-4">
        <a href="#/hospital/census" class="btn btn-ghost btn--sm flex items-center gap-1" style="width: fit-content;">
          ${ui.icon('arrow-left', 'icon icon--sm')}
          <span>Volver al Centro de Control</span>
        </a>
      </div>

      <div class="mb-6">
        <div class="badge badge--warning mb-2 font-bold">Acto Médico Responsable</div>
        <h1 class="text-2xl font-bold m-0">Egreso y Alta Hospitalaria</h1>
        <p class="text-sm text-muted m-0">Emisión de epicrisis médica, destino asistencial y liberación inmediata de recursos hospitalarios</p>
      </div>

      <!-- Resumen del Paciente -->
      <div class="mb-6" id="dischargeSummaryCard"></div>

      <!-- Formulario de Egreso -->
      <div class="card p-6">
        <form id="dischargeForm" novalidate>
          
          <div class="form-group mb-4">
            <label for="selTipoDestino" class="form-label font-semibold">Destino del Egreso <span class="required">*</span></label>
            <select id="selTipoDestino" class="form-select" required>
              <option value="ALTA_DOMICILIO">Alta Médica a Domicilio con Manejo Ambulatorio</option>
              <option value="CONTRAREFERENCIA">Contra-referencia a IPS de Menor Complejidad</option>
              <option value="TRASLADO_OTRA_IPS">Remisión / Traslado a IPS de Mayor Complejidad</option>
              <option value="HOSPITALIZACION_DOMICILIARIA">Hospitalización Domiciliaria (PAD)</option>
              <option value="FALLECIMIENTO">Fallecimiento / Registro de Defunción</option>
            </select>
          </div>

          <div class="form-group mb-4">
            <label for="txtDiagEgreso" class="form-label font-semibold">Diagnóstico Principal de Egreso (CIE-10 / Texto) <span class="required">*</span></label>
            <input type="text" id="txtDiagEgreso" class="form-input" required placeholder="Ej. Neumonía bacteriana no especificada (J15.9)">
          </div>

          <div class="form-group mb-4">
            <label for="txtEpicrisis" class="form-label font-semibold">Epicrisis Resumida <span class="required">*</span></label>
            <textarea id="txtEpicrisis" class="form-textarea" rows="5" required placeholder="Resumen de evolución clínica, paraclínicos relevantes, procedimientos efectuados y condición médica actual al momento del alta..."></textarea>
          </div>

          <div class="form-group mb-6">
            <label for="txtPlanManejo" class="form-label font-semibold">Plan de Manejo y Recomendaciones Ambulatorias</label>
            <textarea id="txtPlanManejo" class="form-textarea" rows="3" placeholder="Medicamentos formulados, signos de alarma para reconsultar por urgencias, control por consulta externa..."></textarea>
          </div>

          <div id="dischargeWarningAlert" class="alert alert--warning mb-6 text-xs">
            ${ui.icon('alert-triangle', 'icon alert-icon')}
            <div class="alert-content">
              <strong>Efectos automáticos del egreso:</strong> La cama hospitalaria asignada pasará inmediatamente al estado de <strong>LIMPIEZA</strong> para su respectiva desinfección, el episodio se marcará como cerrado inmutable y quedará registrado en la bitácora de auditoría clínica (H05).
            </div>
          </div>

          <div class="flex flex-wrap items-center justify-end gap-3">
            <a href="#/hospital/census" class="btn btn-ghost">Cancelar</a>
            <button type="submit" id="btnSubmitDischarge" class="btn btn-primary flex items-center gap-2">
              ${ui.icon('check', 'icon icon--sm')}
              <span>Firmar y Emitir Alta Hospitalaria</span>
            </button>
          </div>

        </form>
      </div>

    </div>
  `;

  const summaryCard = container.querySelector('#dischargeSummaryCard');
  const warningAlert = container.querySelector('#dischargeWarningAlert');
  const selDestino = container.querySelector('#selTipoDestino');

  async function loadPatientSummary() {
    ui.renderSkeleton(summaryCard, 'card');
    try {
      const ep = await emergencyApi.obtenerDetalleEpisodio(episodePublicId);
      const patientName = ep.pacienteNombre || ep.pacienteNombreCompleto || 'Paciente';
      const epId = ep.episodioPublicId || ep.publicId || episodePublicId;
      const motivo = ep.motivoConsulta || ep.motivoLlegada || '—';
      summaryCard.innerHTML = `
        <div class="card p-6">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h1 class="text-xl font-bold m-0">${esc(patientName)}</h1>
              <div class="text-xs text-muted mt-1 font-mono">
                Episodio: ${esc(epId)} · Sede: ${esc(ep.sedeNombre || 'Hospital')}
              </div>
              <div class="text-xs text-muted mt-1">
                Motivo Ingreso: <strong>${esc(motivo)}</strong>
              </div>
            </div>
            <span class="badge ${ep.estado === 'HOSPITALIZADO' ? 'badge--in-progress' : 'badge--scheduled'} font-bold">
              ${esc(ep.estado)}
            </span>
          </div>
        </div>
      `;
      motion.stepTransition(summaryCard, 'forward');
    } catch (err) {
      ui.showToast('Error cargando paciente: ' + err.message, 'error');
      ui.renderError(summaryCard, {
        title: 'No fue posible cargar los datos del paciente',
        message: err.message || 'Error de comunicación con el servidor.',
        onRetry: loadPatientSummary
      });
    }
  }

  await loadPatientSummary();

  selDestino?.addEventListener('change', () => {
    if (warningAlert) {
      const isDeath = selDestino.value === 'FALLECIMIENTO';
      warningAlert.className = `alert ${isDeath ? 'alert--danger' : 'alert--warning'} mb-6 text-xs`;
      motion.pulseSubtle(warningAlert);
    }
  });

  // Envío del formulario
  const form = document.getElementById('dischargeForm');
  const btn = document.getElementById('btnSubmitDischarge');

  async function executeDischarge(payload) {
    ui.setButtonLoading(btn, true);
    try {
      await hospitalApi.registrarEgreso(episodePublicId, payload);
      ui.showToast('Egreso hospitalario emitido y cama liberada a limpieza.', 'success');
      router.navigate('#/hospital/census');
    } catch (err) {
      ui.setButtonLoading(btn, false);
      ui.showToast('Error emitiendo egreso: ' + (err.message || 'Error de servidor'), 'error');
      return false;
    }
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const tipoDestino = document.getElementById('selTipoDestino').value;
    const diagEgreso = document.getElementById('txtDiagEgreso').value.trim();
    const epicrisis = document.getElementById('txtEpicrisis').value.trim();
    const planManejo = document.getElementById('txtPlanManejo').value.trim();

    if (!diagEgreso || !epicrisis) {
      ui.showToast('El diagnóstico y la epicrisis son obligatorios.', 'warning');
      return;
    }

    const payload = {
      tipoDestino,
      diagnosticoEgreso: diagEgreso,
      epicrisisResumen: epicrisis,
      planManejo: planManejo || null
    };

    if (tipoDestino === 'FALLECIMIENTO') {
      ui.showModal({
        title: 'Confirmar Egreso por Fallecimiento',
        message: 'Está por registrar el egreso hospitalario con destino <strong>Fallecimiento / Registro de Defunción</strong>. El episodio se cerrará de forma definitiva e irreversible y la cama asignada pasará a limpieza. ¿Desea confirmar?',
        confirmText: 'Confirmar Fallecimiento',
        cancelText: 'Cancelar',
        isDanger: true,
        onConfirm: async () => executeDischarge(payload)
      });
      return;
    }

    await executeDischarge(payload);
  });
}
