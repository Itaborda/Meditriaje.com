/**
 * MediTriaje 2.0 — Gestión de Alergias del Paciente (patient-allergies.js)
 * Declaración de alergias autorreportadas e historial con trazabilidad clínica (D3, T4).
 */

import { api } from '../api.js';
import { motion } from '../motion.js';
import { ui, esc } from '../ui.js';

function formatFecha(iso) {
  if (!iso) return '—';
  try {
    return new Intl.DateTimeFormat('es-CO', {
      timeZone: 'America/Bogota',
      day: 'numeric',
      month: 'short',
      year: 'numeric'
    }).format(new Date(iso));
  } catch {
    return String(iso);
  }
}

function severidadBadge(severidad) {
  switch (severidad) {
    case 'GRAVE':
      return `<span class="badge badge--cancelled font-semibold">Severidad Grave</span>`;
    case 'MODERADA':
      return `<span class="badge badge--rescheduled font-semibold">Severidad Moderada</span>`;
    case 'LEVE':
      return `<span class="badge badge--scheduled">Severidad Leve</span>`;
    default:
      return `<span class="badge badge--neutral">${esc(severidad || '')}</span>`;
  }
}

export async function patientAllergiesView(container) {
  container.innerHTML = `
    <div style="max-width: 52rem; margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      <nav class="breadcrumb mb-3" aria-label="Ruta de navegación">
        <a href="#/patient/dashboard" class="flex items-center gap-1">
          ${ui.icon('home', 'icon icon--sm')}
          <span>Inicio</span>
        </a>
        <span class="breadcrumb-separator" aria-hidden="true">&gt;</span>
        <span class="breadcrumb-current" aria-current="page">Mis Alergias</span>
      </nav>

      <div class="flex flex-wrap items-center justify-between gap-3 mb-6">
        <div class="min-w-0">
          <div class="flex items-center gap-2 mb-1">
            <a href="#/patient/dashboard" class="btn btn-ghost btn--sm btn--icon-only" aria-label="Volver al panel">
              ${ui.icon('arrow-left')}
            </a>
            <h1 class="text-2xl font-bold m-0">Mis Alergias e Hipersensibilidades</h1>
          </div>
          <p class="text-sm text-muted m-0">Informa a tu equipo de salud sobre reacciones a medicamentos o sustancias.</p>
        </div>
        <button type="button" id="btnNuevaAlergia" class="btn btn-primary btn--sm">
          ${ui.icon('plus', 'icon icon--sm')}
          <span>Declarar alergia</span>
        </button>
      </div>

      <div class="alert alert--info mb-6" role="note">
        ${ui.icon('info', 'icon alert-icon')}
        <div class="alert-content">
          <p class="text-xs m-0">
            <strong>Transparencia clínica:</strong> Las alergias que declares quedan marcadas como <em>autorreportadas</em> hasta que sean valoradas en consulta médica. Las alergias registradas por el personal de salud solo pueden ser modificadas por un profesional.
          </p>
        </div>
      </div>

      <div id="allergiesContent"></div>
    </div>
  `;

  container.querySelector('#btnNuevaAlergia')?.addEventListener('click', () => {
    mostrarModalDeclararAlergia(container);
  });

  await refreshAllergies(container, { showLoader: true });
}

async function refreshAllergies(container, { showLoader = false } = {}) {
  const contentEl = container.querySelector('#allergiesContent');
  if (!contentEl) return;

  if (showLoader) {
    ui.renderSkeleton(contentEl, 'card');
  }

  try {
    const alergias = await api.get('/patients/me/allergies');
    renderAllergiesContent(container, Array.isArray(alergias) ? alergias : []);
  } catch (err) {
    ui.renderError(contentEl, {
      title: 'No fue posible cargar tus alergias',
      message: esc(err.message || 'Ocurrió un error al consultar tus alergias.'),
      onRetry: () => refreshAllergies(container, { showLoader: true })
    });
  }
}

function renderAllergiesContent(container, alergias) {
  const contentEl = container.querySelector('#allergiesContent');
  if (!contentEl) return;

  const activas = alergias.filter(a => a.estado === 'ACTIVA');
  const inactivas = alergias.filter(a => a.estado === 'INACTIVA');

  contentEl.innerHTML = `
    <!-- Alergias Activas -->
    <div class="card mb-6">
      <div class="card-header flex flex-wrap items-center justify-between gap-2">
        <h2 class="card-title text-lg flex items-center gap-2">
          ${ui.icon('activity', 'icon icon--sm text-primary')}
          <span>Alergias Activas (${activas.length})</span>
        </h2>
      </div>
      <div class="card-body">
        ${activas.length === 0 ? `
          <div id="activeAllergiesEmpty"></div>
        ` : `
          <div class="flex flex-col gap-3">
            ${activas.map(a => `
              <article class="card p-4 flex flex-wrap items-start justify-between gap-3 allergy-active-card" style="border-left: 4px solid ${a.severidad === 'GRAVE' ? 'var(--danger)' : 'var(--primary)'};">
                <div class="flex-1 min-w-0">
                  <div class="flex flex-wrap items-center gap-2 mb-1">
                    <strong class="text-base font-bold">${esc(a.sustancia)}</strong>
                    ${severidadBadge(a.severidad)}
                    ${a.autorreportada
                      ? '<span class="badge badge--scheduled text-xs">Autorreportada</span>'
                      : '<span class="badge badge--confirmed text-xs">Diagnóstico médico</span>'}
                  </div>
                  ${a.reaccion ? `<p class="text-sm text-muted m-0 mb-1"><strong>Reacción:</strong> ${esc(a.reaccion)}</p>` : ''}
                  <p class="text-xs text-muted m-0">Registrada el ${esc(formatFecha(a.createdAt))}</p>
                </div>
                <div class="shrink-0">
                  ${a.autorreportada ? `
                    <button type="button" class="btn btn-secondary btn--sm btn-inactivar" data-id="${esc(a.publicId)}" data-sustancia="${esc(a.sustancia)}">
                      <span>Inactivar</span>
                    </button>
                  ` : `
                    <span class="badge badge--neutral text-xs" title="Gestionada por personal asistencial">Médica</span>
                  `}
                </div>
              </article>
            `).join('')}
          </div>
        `}
      </div>
    </div>

    <!-- Alergias Inactivas -->
    ${inactivas.length > 0 ? `
      <details class="card details-card mb-6">
        <summary class="details-summary">
          <span class="flex items-center gap-2 text-base font-semibold">
            ${ui.icon('clock', 'icon icon--sm text-muted')}
            <span>Historial de alergias inactivadas (${inactivas.length})</span>
          </span>
          ${ui.icon('chevron-down', 'icon icon--sm details-summary-chevron')}
        </summary>
        <div class="details-content">
          <div class="flex flex-col gap-3">
            ${inactivas.map(a => `
              <div class="card p-3 opacity-75" style="background: var(--surface-2);">
                <div class="flex flex-wrap items-center gap-2 mb-1">
                  <span class="font-bold text-sm line-through">${esc(a.sustancia)}</span>
                  <span class="badge badge--neutral text-xs">Inactiva</span>
                  ${a.autorreportada ? '<span class="badge badge--scheduled text-xs">Autorreportada</span>' : ''}
                </div>
                ${a.motivoInactivacion ? `<p class="text-xs text-muted m-0"><strong>Motivo inactivación:</strong> ${esc(a.motivoInactivacion)}</p>` : ''}
                <p class="text-xs text-muted m-0 mt-1">Inactivada el ${esc(formatFecha(a.fechaInactivacion))}</p>
              </div>
            `).join('')}
          </div>
        </div>
      </details>
    ` : ''}
  `;

  if (activas.length === 0) {
    const emptyEl = contentEl.querySelector('#activeAllergiesEmpty');
    ui.renderEmpty(emptyEl, {
      icon: 'shield',
      title: 'Sin alergias activas registradas',
      description: 'Esto no confirma que no tengas alergias. Si presentas reacciones a medicamentos, látex o alimentos, decláralas para alertar a tu equipo médico.',
      actionText: 'Declarar alergia',
      onAction: () => mostrarModalDeclararAlergia(container)
    });
  } else {
    motion.staggerCards('.allergy-active-card', { root: container });
  }

  contentEl.querySelectorAll('.btn-inactivar').forEach(btn => {
    btn.addEventListener('click', () => {
      const publicId = btn.dataset.id;
      const sustancia = btn.dataset.sustancia;
      mostrarModalInactivarAlergia(container, publicId, sustancia);
    });
  });
}

function mostrarModalDeclararAlergia(container) {
  const modalHtml = `
    <form id="formDeclararAlergia" class="flex flex-col gap-3">
      <div class="form-group m-0">
        <label for="modal-sustancia" class="form-label text-xs">Sustancia o Medicamento *</label>
        <input type="text" id="modal-sustancia" class="form-input" placeholder="Ej. Penicilina, Ibuprofeno, Mariscos" minlength="2" maxlength="100" required autofocus>
      </div>
      <div class="form-group m-0">
        <label for="modal-severidad" class="form-label text-xs">Severidad percibida *</label>
        <select id="modal-severidad" class="form-select" required>
          <option value="LEVE">Leve (erupción leve, molestia digestiva leve)</option>
          <option value="MODERADA" selected>Moderada (urticaria extensa, hinchazón moderada)</option>
          <option value="GRAVE">Grave (dificultad respiratoria, anafilaxia previa)</option>
        </select>
      </div>
      <div class="form-group m-0">
        <label for="modal-reaccion" class="form-label text-xs">Reacción o síntomas experimentados (opcional)</label>
        <textarea id="modal-reaccion" class="form-textarea" rows="2" maxlength="200" placeholder="Ej. Salpullido en la piel, inflamación de labios"></textarea>
      </div>
      <p id="modalError" class="text-xs text-danger m-0" role="alert" style="display:none;"></p>
    </form>
  `;

  ui.showModal({
    title: 'Declarar alergia autorreportada',
    message: modalHtml,
    confirmText: 'Guardar alergia',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      const sustanciaEl = document.getElementById('modal-sustancia');
      const sustancia = sustanciaEl?.value.trim();
      const severidad = document.getElementById('modal-severidad')?.value;
      const reaccion = document.getElementById('modal-reaccion')?.value.trim();
      const errBox = document.getElementById('modalError');
      const confirmBtn = document.querySelector('#modal-container .modal-backdrop.is-open:last-of-type .btn-confirm-modal');

      if (errBox) {
        errBox.style.display = 'none';
        errBox.textContent = '';
      }

      if (!sustancia || sustancia.length < 2) {
        if (errBox) {
          errBox.textContent = 'La sustancia debe tener al menos 2 caracteres.';
          errBox.style.display = 'block';
          motion.pulseSubtle(errBox);
        }
        sustanciaEl?.focus();
        return false;
      }

      try {
        ui.setButtonLoading(confirmBtn, true);
        await api.post('/patients/me/allergies', {
          sustancia,
          severidad,
          reaccion: reaccion || null
        });
        ui.showToast('Alergia declarada correctamente.', 'success');
        await refreshAllergies(container, { showLoader: false });
        return true;
      } catch (err) {
        ui.setButtonLoading(confirmBtn, false);
        if (errBox) {
          errBox.textContent = err.message || 'No fue posible registrar la alergia.';
          errBox.style.display = 'block';
          motion.pulseSubtle(errBox);
        }
        return false;
      }
    }
  });
}

function mostrarModalInactivarAlergia(container, publicId, sustancia) {
  const modalHtml = `
    <form id="formInactivarAlergia" class="flex flex-col gap-3">
      <p class="text-sm text-muted m-0">
        Indica el motivo por el cual deseas marcar como inactiva la alergia a <strong>${esc(sustancia)}</strong>.
        Por trazabilidad clínica, el registro no se borra, sino que se inactiva.
      </p>
      <div class="form-group m-0">
        <label for="modal-motivo" class="form-label text-xs">Motivo de inactivación *</label>
        <textarea id="modal-motivo" class="form-textarea" rows="3" minlength="5" maxlength="500" placeholder="Ej. Prueba de alergia posterior negativa, diagnóstico corregido" required autofocus></textarea>
      </div>
      <p id="modalInactError" class="text-xs text-danger m-0" role="alert" style="display:none;"></p>
    </form>
  `;

  ui.showModal({
    title: 'Inactivar alergia autorreportada',
    message: modalHtml,
    confirmText: 'Inactivar',
    cancelText: 'Cancelar',
    isDanger: true,
    onConfirm: async () => {
      const motivoEl = document.getElementById('modal-motivo');
      const motivo = motivoEl?.value.trim();
      const errBox = document.getElementById('modalInactError');
      const confirmBtn = document.querySelector('#modal-container .modal-backdrop.is-open:last-of-type .btn-confirm-modal');

      if (errBox) {
        errBox.style.display = 'none';
        errBox.textContent = '';
      }

      if (!motivo || motivo.length < 5) {
        if (errBox) {
          errBox.textContent = 'El motivo de inactivación debe tener al menos 5 caracteres.';
          errBox.style.display = 'block';
          motion.pulseSubtle(errBox);
        }
        motivoEl?.focus();
        return false;
      }

      try {
        ui.setButtonLoading(confirmBtn, true);
        await api.patch(`/patients/me/allergies/${encodeURIComponent(publicId)}/deactivate`, { motivo });
        ui.showToast('Alergia inactivada correctamente.', 'info');
        await refreshAllergies(container, { showLoader: false });
        return true;
      } catch (err) {
        ui.setButtonLoading(confirmBtn, false);
        if (errBox) {
          errBox.textContent = err.message || 'No fue posible inactivar la alergia.';
          errBox.style.display = 'block';
          motion.pulseSubtle(errBox);
        }
        return false;
      }
    }
  });
}
