/**
 * MediTriaje 2.0 — Reconciliación de Identidad Provisional (nursing-identity.js)
 * Proceso seguro y auditado para vincular un paciente admitido como no identificado (NN)
 * con su registro civil verificado una vez establecida su identidad (U03, ADR-027).
 */

import { emergencyApi } from '../api.js';
import { motion } from '../motion.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

export async function nursingIdentityView(container, { params }) {
  const episodePublicId = params?.id || params?.episodioId;

  if (!episodePublicId) {
    ui.renderEmpty(container, {
      title: 'Episodio no especificado',
      description: 'No se suministró un identificador de episodio de urgencias.',
      actionText: 'Volver a la cola',
      onAction: () => router.navigate('/nursing/dashboard')
    });
    return;
  }

  container.innerHTML = `
    <div style="max-width: 750px; margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Navegación -->
      <div class="mb-4">
        <a href="#/nursing/dashboard" class="btn btn-ghost btn--sm flex items-center gap-1" style="width: fit-content;">
          ${ui.icon('arrow-left', 'icon icon--sm')}
          <span>Volver a la Cola de Urgencias</span>
        </a>
      </div>

      <!-- Encabezado -->
      <div class="mb-6">
        <div class="badge badge--warning mb-2 font-bold">Procedimiento Asistencial Seguro</div>
        <h1 class="text-2xl font-bold m-0">Reconciliación de Identidad Provisional (NN)</h1>
        <p class="text-sm text-muted m-0">Vinculación auditada de un episodio de atención de urgencias con el paciente civil correspondiente</p>
      </div>

      <!-- Resumen de Identidad Provisional -->
      <div class="card p-6 mb-6" id="summaryProvisionalCard">
        <div class="skeleton skeleton-title" style="width: 45%;"></div>
        <div class="skeleton skeleton-text mb-0" style="width: 75%;"></div>
      </div>

      <!-- Formulario de Reconciliación -->
      <div class="card p-6">
        <h2 class="text-lg font-bold mb-4">Vincular con Paciente Registrado</h2>

        <form id="reconcileForm" novalidate>
          
          <div class="form-group mb-4">
            <label for="txtDocPaciente" class="form-label font-semibold">Identificación Civil del Paciente (Cédula / TI / CE / Pasaporte) <span class="text-danger">*</span></label>
            <div class="flex flex-wrap gap-2">
              <input type="text" id="txtDocPaciente" class="form-input" style="flex: 1; min-width: 200px;" placeholder="Número de documento..." required>
              <button type="button" id="btnBuscarPacienteCivil" class="btn btn-secondary btn--sm">Buscar</button>
            </div>
            <div id="resultadoBusquedaCivil" class="mt-2 text-sm"></div>
            <input type="hidden" id="hdnPacienteCivilPublicId" value="">
          </div>

          <div class="form-group mb-6">
            <label for="txtMotivoReconciliacion" class="form-label font-semibold">Motivo y Evidencia de Reconciliación <span class="text-danger">*</span></label>
            <textarea id="txtMotivoReconciliacion" class="form-textarea" rows="3" required placeholder="Especifique cómo se verificó la identidad (ej. presentación de documento original por familiar, cotejo dactilar, verificación EPS)..."></textarea>
          </div>

          <div class="card p-4 mb-6 border text-xs text-muted" style="background-color: var(--surface-2);">
            <span class="font-bold text-text">Nota de Trazabilidad y Seguridad:</span>
            Esta operación no destruye el historial asistencial previo ni el código provisional, el cual permanecerá vinculado como referencia inmutable en la bitácora de auditoría clínica (Fase U, ADR-027).
          </div>

          <div class="flex flex-wrap items-center justify-end gap-2">
            <a href="#/nursing/dashboard" class="btn btn-ghost">Cancelar</a>
            <button type="submit" id="btnSubmitReconcile" class="btn btn-primary flex items-center gap-2">
              ${ui.icon('link', 'icon icon--sm')}
              <span>Confirmar y Reconciliar Identidad</span>
            </button>
          </div>

        </form>
      </div>

    </div>
  `;

  // Cargar Detalle del Episodio
  try {
    const episodio = await emergencyApi.obtenerDetalleEpisodio(episodePublicId);
    renderSummary(episodio);
    motion.pulseSubtle(document.getElementById('summaryProvisionalCard'));
  } catch (err) {
    ui.renderError(document.getElementById('summaryProvisionalCard'), {
      title: 'No se pudo cargar el episodio provisional',
      message: err.message,
      actionText: 'Volver a la cola',
      onAction: () => router.navigate('/nursing/dashboard')
    });
  }

  // Búsqueda de Paciente Civil
  const btnBuscar = document.getElementById('btnBuscarPacienteCivil');
  const txtDoc = document.getElementById('txtDocPaciente');
  const txtMotivo = document.getElementById('txtMotivoReconciliacion');
  const divRes = document.getElementById('resultadoBusquedaCivil');
  const hdnId = document.getElementById('hdnPacienteCivilPublicId');

  txtDoc.addEventListener('input', () => txtDoc.classList.remove('form-input--error'));
  txtMotivo.addEventListener('input', () => txtMotivo.classList.remove('form-textarea--error'));

  txtDoc.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      btnBuscar.click();
    }
  });

  function showSelectedCivilPatient(label) {
    divRes.innerHTML = `
      <div class="p-3 border rounded selected-patient-card flex flex-wrap items-center justify-between gap-2" style="background-color: var(--surface);">
        <div class="text-success font-semibold">✓ Paciente civil seleccionado: ${esc(label)}</div>
        <button type="button" id="btnCambiarPacienteCivil" class="btn btn-ghost btn--xs">Cambiar</button>
      </div>
    `;
    motion.pulseSubtle(divRes.querySelector('.selected-patient-card'));
    document.getElementById('btnCambiarPacienteCivil')?.addEventListener('click', () => {
      hdnId.value = '';
      divRes.innerHTML = '';
      txtDoc.focus();
    });
  }

  btnBuscar.addEventListener('click', async () => {
    const doc = txtDoc.value.trim();
    if (!doc) {
      txtDoc.classList.add('form-input--error');
      txtDoc.focus();
      divRes.innerHTML = `<span class="text-warning">Ingrese el número de documento a buscar.</span>`;
      return;
    }

    txtDoc.classList.remove('form-input--error');
    hdnId.value = '';
    ui.setButtonLoading(btnBuscar, true);
    divRes.innerHTML = `
      <div class="inline-flex items-center gap-2 text-muted text-sm py-1">
        ${ui.icon('refresh-cw', 'icon icon--sm icon--spin')}
        <span>Buscando paciente civil...</span>
      </div>
    `;
    try {
      const pacientes = await emergencyApi.buscarPacientesPorDocumento(doc);
      if (!Array.isArray(pacientes) || pacientes.length === 0) {
        hdnId.value = '';
        divRes.innerHTML = `<span class="text-warning">No se encontró un paciente registrado con el documento ${esc(doc)}.</span>`;
        return;
      }

      divRes.innerHTML = `
        <div class="flex flex-col gap-2 mt-2">
          ${pacientes.map(p => {
            const nombre = p.nombreCompleto || `${p.nombres || ''} ${p.apellidos || ''}`.trim();
            const docLabel = `${p.tipoDocumento || ''} ${p.numeroDocumento || ''}`.trim();
            return `
              <div class="p-3 border rounded candidate-card flex flex-wrap items-center justify-between gap-2" style="background-color: var(--surface);">
                <div>
                  <div class="font-bold">${esc(nombre)}</div>
                  <div class="text-xs text-muted font-mono">${esc(docLabel)}</div>
                </div>
                <button
                  type="button"
                  class="btn btn-xs btn-primary btn-confirmar-seleccion"
                  data-public-id="${esc(p.publicId)}"
                  data-label="${esc(`${nombre}${docLabel ? ` (${docLabel})` : ''}`)}"
                >
                  Usar este paciente
                </button>
              </div>
            `;
          }).join('')}
        </div>
      `;

      motion.staggerCards(divRes.querySelectorAll('.candidate-card'), { root: container });

      divRes.querySelectorAll('.btn-confirmar-seleccion').forEach(btn => {
        btn.addEventListener('click', () => {
          const pubId = btn.getAttribute('data-public-id') || '';
          const label = btn.getAttribute('data-label') || pubId;
          hdnId.value = pubId;
          txtDoc.classList.remove('form-input--error');
          showSelectedCivilPatient(label);
        });
      });
    } catch (err) {
      divRes.innerHTML = `<span class="text-danger">Error buscando paciente: ${esc(err.message)}</span>`;
    } finally {
      ui.setButtonLoading(btnBuscar, false);
    }
  });

  // Envío del Formulario
  const form = document.getElementById('reconcileForm');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const pacienteId = hdnId.value.trim();
    const motivo = txtMotivo.value.trim();

    if (!pacienteId) {
      txtDoc.classList.add('form-input--error');
      txtDoc.focus();
      ui.showToast('Debe buscar y seleccionar un paciente civil verificado de la lista.', 'warning');
      return;
    }

    if (!motivo) {
      txtMotivo.classList.add('form-textarea--error');
      txtMotivo.focus();
      ui.showToast('Debe suministrar el motivo de reconciliación.', 'warning');
      return;
    }

    const btnSubmit = document.getElementById('btnSubmitReconcile');
    ui.setButtonLoading(btnSubmit, true);

    try {
      await emergencyApi.reconciliarIdentidad(episodePublicId, {
        pacientePublicId: pacienteId,
        motivoReconciliacion: motivo
      });
      ui.showToast('Identidad provisional reconciliada exitosamente.', 'success');
      router.navigate('/nursing/dashboard');
    } catch (err) {
      ui.setButtonLoading(btnSubmit, false);
      ui.showToast('Error en reconciliación: ' + (err.message || 'Error de servidor'), 'error');
    }
  });
}

function renderSummary(ep) {
  const card = document.getElementById('summaryProvisionalCard');
  card.innerHTML = `
    <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
      <span class="text-xs font-bold uppercase tracking-wider text-muted">Registro Provisional Activo</span>
      <span class="badge badge--warning font-bold">${esc(ep.identidadEstado || 'PROVISIONAL')}</span>
    </div>
    <div class="text-xl font-mono font-bold text-indigo-700">${esc(ep.codigoProvisional || 'NN-SIN-CODIGO')}</div>
    <div class="text-sm mt-2 text-muted">
      Motivo de Ingreso: <strong>${esc(ep.motivoConsulta || ep.motivoLlegada || '—')}</strong>
    </div>
    <div class="text-xs text-muted mt-1">
      Vía de Ingreso: ${esc(ep.viaIngreso || ep.medioLlegada || 'ESPONTANEO')} · Sede: ${esc(ep.sedeNombre || 'Hospital')}
    </div>
  `;
}
