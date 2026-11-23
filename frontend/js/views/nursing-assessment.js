/**
 * MediTriaje 2.0 — Valoración Clínica de Triaje y Reevaluación (nursing-assessment.js)
 * Clasificación presencial I a V (Resolución 5596/2015 MinSalud Colombia) calculada
 * automáticamente a partir de los síntomas clínicos del paciente, registro de signos vitales,
 * escala de Glasgow y registro inmutable append-only (U04, ADR-027).
 */

import { emergencyApi, api } from '../api.js';
import { motion } from '../motion.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

const CATEGORY_LABELS = {
  TODOS: 'Todos',
  CARDIOVASCULAR: 'Cardiovascular',
  RESPIRATORIO: 'Respiratorio',
  NEUROLOGICO: 'Neurológico',
  DIGESTIVO: 'Digestivo',
  TRAUMA: 'Trauma',
  MUSCULOESQUELETICO: 'Musculoesquelético',
  DERMATOLOGICO: 'Dermatológico',
  GENERAL: 'General'
};

const LEVEL_META = {
  I: {
    badgeClass: 'badge--triage-i',
    title: 'Nivel I — Reanimación Inmediata',
    waitText: 'Atención inmediata',
    borderColor: 'var(--triage-1-bd)'
  },
  II: {
    badgeClass: 'badge--triage-ii',
    title: 'Nivel II — Emergencia',
    waitText: '≤ 30 min',
    borderColor: 'var(--triage-2-bd)'
  },
  III: {
    badgeClass: 'badge--triage-iii',
    title: 'Nivel III — Urgencia',
    waitText: '≤ 120 min',
    borderColor: 'var(--triage-3-bd)'
  },
  IV: {
    badgeClass: 'badge--triage-iv',
    title: 'Nivel IV — Prioritaria',
    waitText: '≤ 180 min',
    borderColor: 'var(--triage-4-bd)'
  },
  V: {
    badgeClass: 'badge--triage-v',
    title: 'Nivel V — No Urgente',
    waitText: '≤ 240 min',
    borderColor: 'var(--triage-5-bd)'
  }
};

function formatCategoryLabel(cat) {
  if (!cat) return 'General';
  if (CATEGORY_LABELS[cat]) return CATEGORY_LABELS[cat];
  return cat.replace(/_/g, ' ').toLowerCase().replace(/(^|\s)\S/g, l => l.toUpperCase());
}

function getIntensityDescription(val) {
  if (val <= 3) return 'Leve';
  if (val <= 6) return 'Moderada';
  if (val <= 8) return 'Severa';
  return 'Muy intensa';
}

function formatTriageBadge(nivel) {
  switch (nivel) {
    case 'I':
      return `<span class="badge badge--triage-i font-bold">${ui.icon('alert-triangle', 'icon icon--xs')} Nivel I — Reanimación Inmediata</span>`;
    case 'II':
      return `<span class="badge badge--triage-ii font-bold">${ui.icon('alert-circle', 'icon icon--xs')} Nivel II — Emergencia (&lt; 30 min)</span>`;
    case 'III':
      return `<span class="badge badge--triage-iii font-bold">${ui.icon('clock', 'icon icon--xs')} Nivel III — Urgencia (&lt; 120 min)</span>`;
    case 'IV':
      return `<span class="badge badge--triage-iv font-semibold">${ui.icon('activity', 'icon icon--xs')} Nivel IV — Prioritaria (&lt; 180 min)</span>`;
    case 'V':
      return `<span class="badge badge--triage-v font-semibold">${ui.icon('calendar', 'icon icon--xs')} Nivel V — No Urgente (&lt; 240 min)</span>`;
    default:
      return `<span class="badge badge--scheduled">Pendiente</span>`;
  }
}

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

function computeLocalPreview(symptoms) {
  if (!symptoms || symptoms.length === 0) return null;
  const alarms = symptoms.filter(s => s.esAlarma).map(s => s.codigo);
  if (alarms.length > 0) {
    return {
      nivel: 'I',
      esEmergencia: true,
      sintomasAlarma: alarms,
      mensaje: 'Signos de alarma clínica detectados. Atención inmediata requerida.'
    };
  }
  const maxIntensity = Math.max(...symptoms.map(s => Number(s.intensidad) || 0));
  let nivel = 'IV';
  if (maxIntensity >= 7) nivel = 'II';
  else if (maxIntensity >= 4) nivel = 'III';
  return {
    nivel,
    esEmergencia: false,
    sintomasAlarma: [],
    mensaje: null
  };
}

export async function nursingAssessmentView(container, { params }) {
  const episodePublicId = params?.id || params?.episodioId;

  if (!episodePublicId) {
    ui.renderEmpty(container, {
      title: 'Episodio no especificado',
      description: 'No se suministró un identificador de episodio de urgencias válido.',
      actionText: 'Volver a la cola',
      onAction: () => router.navigate('/nursing/dashboard')
    });
    return;
  }

  container.innerHTML = `
    <div style="max-width: 900px; margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Navegación -->
      <div class="mb-4">
        <a href="#/nursing/dashboard" class="btn btn-ghost btn--sm flex items-center gap-1" style="width: fit-content;">
          ${ui.icon('arrow-left', 'icon icon--sm')}
          <span>Volver a la Cola de Urgencias</span>
        </a>
      </div>

      <!-- Resumen del Paciente y Episodio -->
      <div class="card p-6 mb-6" id="episodeSummaryCard">
        <div class="skeleton skeleton-title" style="width: 45%;"></div>
        <div class="skeleton skeleton-text mb-0" style="width: 70%;"></div>
      </div>

      <!-- Historial de Valoraciones Previas (Inmutable) -->
      <div class="card p-6 mb-6">
        <div class="flex flex-wrap items-center justify-between gap-2 mb-4">
          <h2 class="text-lg font-bold m-0 flex items-center gap-2">
            ${ui.icon('clock', 'icon icon--sm')}
            <span>Historial Clínico de Triaje (Inmutable)</span>
          </h2>
          <span class="text-xs text-muted">Resolución 5596 MinSalud · Registro append-only</span>
        </div>
        <div id="triageHistoryContainer">
          <div class="skeleton skeleton-text" style="width: 85%;"></div>
          <div class="skeleton skeleton-text mb-0" style="width: 60%;"></div>
        </div>
      </div>

      <!-- Formulario de Nueva Valoración o Reevaluación -->
      <div class="card p-6">
        <div class="mb-4">
          <h2 class="text-xl font-bold m-0" id="formAssessmentTitle">Registro de Valoración de Triaje</h2>
          <p class="text-sm text-muted m-0">El nivel de prioridad se calcula automáticamente según los síntomas ingresados</p>
        </div>

        <form id="triageAssessmentForm" novalidate>
          
          <!-- Selección de Síntomas del Paciente -->
          <div class="card p-4 mb-6" style="background-color: var(--surface-2); border: 1px solid var(--border);">
            <div class="flex flex-wrap items-center justify-between gap-2 mb-3">
              <div>
                <label for="nursingSymptomSearch" class="form-label font-bold text-base m-0">
                  Síntomas del Paciente <span class="text-danger">*</span>
                </label>
                <p class="text-xs text-muted m-0">Seleccione uno o varios síntomas del catálogo e indique intensidad y duración</p>
              </div>
              <span id="nursingSelectedCounter" class="text-xs text-muted font-medium tabular-nums">0 síntoma(s) seleccionados</span>
            </div>

            <div class="form-group mb-3">
              <input
                type="search"
                id="nursingSymptomSearch"
                class="form-input"
                placeholder="Buscar síntoma (ej. fiebre, dolor torácico, mareo...)"
                aria-label="Buscar síntoma clínico"
              >
            </div>

            <div class="chip-group mb-3" id="nursingCategoryChips" role="group" aria-label="Filtrar síntomas por categoría"></div>

            <div class="chip-group" id="nursingSymptomsList" style="max-height: 240px; overflow-y: auto; padding: var(--space-1);">
              <div class="skeleton skeleton-text mb-0" style="width: 100%; height: 2.25rem;"></div>
            </div>

            <div id="selectedSymptomsContainer" class="flex flex-col gap-3 mt-4"></div>
          </div>

          <!-- Nivel de Prioridad Calculado Automáticamente (Solo Lectura) -->
          <div
            id="calculatedPriorityPanel"
            class="card p-4 mb-6"
            aria-live="polite"
            data-calculated-level=""
            style="border: 1px solid var(--border); background-color: var(--surface);"
          >
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div>
                <div class="text-xs text-muted font-bold uppercase tracking-wider">Nivel de Triaje Calculado Automáticamente</div>
                <p class="text-sm text-muted m-0 mt-1">
                  Seleccione al menos un síntoma para calcular automáticamente el nivel de prioridad clínica.
                </p>
              </div>
              <span class="badge badge--neutral text-xs">Calculado por síntomas (no editable)</span>
            </div>
          </div>

          <!-- Signos Vitales -->
          <div class="card p-4 mb-6" style="background-color: var(--surface-2); border: 1px solid var(--border);">
            <div class="flex flex-wrap items-center justify-between gap-2 mb-3">
              <div class="font-bold text-sm">Signos Vitales y Parámetros Clínicos</div>
              <div id="vitalsAlertBar" class="flex flex-wrap items-center gap-2" aria-live="polite"></div>
            </div>
            <div class="grid grid-cols-2 md:grid-cols-3 gap-4 items-end">
              
              <div class="form-group m-0">
                <label for="txtPA" class="form-label text-xs font-semibold">Presión Arterial (PA)</label>
                <input type="text" id="txtPA" class="form-input" placeholder="Ej. 120/80">
              </div>

              <div class="form-group m-0">
                <label for="txtFC" class="form-label text-xs font-semibold">Frecuencia Cardíaca (FC)</label>
                <input type="number" id="txtFC" class="form-input" min="20" max="250" placeholder="lpm (ej. 78)">
              </div>

              <div class="form-group m-0">
                <label for="txtFR" class="form-label text-xs font-semibold">Frecuencia Respiratoria (FR)</label>
                <input type="number" id="txtFR" class="form-input" min="5" max="80" placeholder="rpm (ej. 18)">
              </div>

              <div class="form-group m-0">
                <label for="txtSO2" class="form-label text-xs font-semibold">Saturación de O2 (SpO2 %)</label>
                <input type="number" id="txtSO2" class="form-input" min="40" max="100" placeholder="% (ej. 98)">
              </div>

              <div class="form-group m-0">
                <label for="txtTemp" class="form-label text-xs font-semibold">Temperatura (°C)</label>
                <input type="number" step="0.1" id="txtTemp" class="form-input" min="30" max="45" placeholder="°C (ej. 36.8)">
              </div>

              <div class="form-group m-0">
                <label for="txtGlasgow" class="form-label text-xs font-semibold">Escala de Glasgow (3-15)</label>
                <input type="number" id="txtGlasgow" class="form-input" min="3" max="15" placeholder="Puntos (3-15)">
              </div>

            </div>
          </div>

          <!-- Motivo y Hallazgos -->
          <div class="form-group mb-4">
            <label for="txtMotivo" class="form-label font-semibold">Motivo de Consulta Clínico <span class="text-danger">*</span></label>
            <textarea id="txtMotivo" class="form-textarea" rows="2" required placeholder="Motivo relatado por el paciente o acompañante..."></textarea>
          </div>

          <div class="form-group mb-4">
            <label for="txtHallazgos" class="form-label font-semibold">Hallazgos Clínicos y Exploración Inmediata</label>
            <textarea id="txtHallazgos" class="form-textarea" rows="3" placeholder="Estado general, vía aérea, ventilación, signos de shock o focalización..."></textarea>
          </div>

          <!-- Reevaluación (Si ya existe valoración previa) -->
          <div id="bloqueReevaluacion" style="display: none;" class="card p-4 mb-4 border-warning bg-amber-50">
            <div class="flex items-center gap-2 text-amber-800 font-bold text-sm mb-2">
              ${ui.icon('alert-circle', 'icon icon--sm')}
              <span>Esta valoración constituye una REEVALUACIÓN clínica</span>
            </div>
            <div class="form-group m-0">
              <label for="txtMotivoReevaluacion" class="form-label text-xs font-bold text-amber-900">Motivo del Cambio o Reevaluación <span class="text-danger">*</span></label>
              <textarea id="txtMotivoReevaluacion" class="form-textarea" rows="2" placeholder="Describa el cambio clínico (ej. agravamiento de dolor torácico, disminución de saturación, etc.)"></textarea>
            </div>
          </div>

          <!-- Acciones -->
          <div class="flex flex-wrap items-center justify-end gap-2 mt-6">
            <a href="#/nursing/dashboard" class="btn btn-ghost">Cancelar</a>
            <button type="submit" id="btnSubmitAssessment" class="btn btn-primary flex items-center gap-2">
              ${ui.icon('check', 'icon icon--sm')}
              <span>Guardar Clasificación de Triaje</span>
            </button>
          </div>

        </form>
      </div>

    </div>
  `;

  const state = {
    catalog: [],
    selectedSymptoms: new Map(),
    searchTerm: '',
    activeCategory: 'TODOS',
    calculatedPreview: null,
    lastAnimatedLevel: '',
    calcRequestSeq: 0
  };

  let episodioActual = null;
  let valoracionesPrevias = [];

  const searchInput = document.getElementById('nursingSymptomSearch');
  const categoryChipsEl = document.getElementById('nursingCategoryChips');
  const symptomsListEl = document.getElementById('nursingSymptomsList');
  const selectedContainerEl = document.getElementById('selectedSymptomsContainer');
  const selectedCounterEl = document.getElementById('nursingSelectedCounter');
  const priorityPanelEl = document.getElementById('calculatedPriorityPanel');
  const vitalsAlertBarEl = document.getElementById('vitalsAlertBar');

  function updateVitalsAlerts() {
    if (!vitalsAlertBarEl) return;
    const so2Raw = document.getElementById('txtSO2')?.value;
    const glasgowRaw = document.getElementById('txtGlasgow')?.value;
    const tempRaw = document.getElementById('txtTemp')?.value;
    const so2 = so2Raw !== '' ? Number(so2Raw) : null;
    const glasgow = glasgowRaw !== '' ? Number(glasgowRaw) : null;
    const temp = tempRaw !== '' ? Number(tempRaw) : null;

    const pills = [];
    if (so2 !== null && Number.isFinite(so2) && so2 < 90) {
      pills.push(`
        <span class="badge badge--danger text-xs">
          ${ui.icon('alert-triangle', 'icon icon--xs')}
          <span>Alerta SpO₂ &lt; 90% (${esc(so2)}%)</span>
        </span>
      `);
    }
    if (glasgow !== null && Number.isFinite(glasgow) && glasgow <= 8) {
      pills.push(`
        <span class="badge badge--danger text-xs">
          ${ui.icon('alert-triangle', 'icon icon--xs')}
          <span>Alerta Glasgow ≤ 8 (${esc(glasgow)}/15)</span>
        </span>
      `);
    }
    if (temp !== null && Number.isFinite(temp) && (temp >= 39.5 || temp < 35)) {
      pills.push(`
        <span class="badge badge--warning text-xs">
          ${ui.icon('alert-circle', 'icon icon--xs')}
          <span>Temperatura crítica (${esc(temp)} °C)</span>
        </span>
      `);
    }
    vitalsAlertBarEl.innerHTML = pills.join('');
  }

  ['txtPA', 'txtFC', 'txtFR', 'txtSO2', 'txtTemp', 'txtGlasgow'].forEach(id => {
    document.getElementById(id)?.addEventListener('input', updateVitalsAlerts);
  });

  ['txtMotivo', 'txtMotivoReevaluacion'].forEach(id => {
    const el = document.getElementById(id);
    el?.addEventListener('input', () => el.classList.remove('form-textarea--error'));
  });

  function renderCalculatedPriority() {
    const preview = state.calculatedPreview;
    if (!preview || !preview.nivel) {
      state.lastAnimatedLevel = '';
      priorityPanelEl.setAttribute('data-calculated-level', '');
      priorityPanelEl.style.borderLeft = '1px solid var(--border)';
      priorityPanelEl.innerHTML = `
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <div class="text-xs text-muted font-bold uppercase tracking-wider">Nivel de Triaje Calculado Automáticamente</div>
            <p class="text-sm text-muted m-0 mt-1">
              Seleccione al menos un síntoma para calcular automáticamente el nivel de prioridad clínica.
            </p>
          </div>
          <span class="badge badge--neutral text-xs">Calculado por síntomas (no editable)</span>
        </div>
      `;
      return;
    }

    const meta = LEVEL_META[preview.nivel] || {
      badgeClass: 'badge--scheduled',
      title: `Nivel ${preview.nivel}`,
      waitText: '',
      borderColor: 'var(--primary)'
    };

    priorityPanelEl.setAttribute('data-calculated-level', preview.nivel);
    priorityPanelEl.style.borderLeft = `4px solid ${meta.borderColor}`;
    priorityPanelEl.innerHTML = `
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div class="text-xs text-muted font-bold uppercase tracking-wider mb-1">
            Nivel de Triaje Calculado Automáticamente
          </div>
          <div class="flex flex-wrap items-center gap-2">
            ${formatTriageBadge(preview.nivel)}
            ${preview.esEmergencia ? `<span class="badge badge--triage-1 text-xs font-bold">Emergencia Clínica</span>` : ''}
            ${meta.waitText ? `<span class="text-xs text-muted font-medium">Tiempo objetivo: ${esc(meta.waitText)}</span>` : ''}
          </div>
          ${preview.mensaje ? `<p class="text-xs text-muted m-0 mt-2">${esc(preview.mensaje)}</p>` : ''}
        </div>
        <span class="badge badge--neutral text-xs">Calculado por síntomas (no editable)</span>
      </div>
    `;

    if (preview.nivel !== state.lastAnimatedLevel) {
      state.lastAnimatedLevel = preview.nivel;
      if (preview.nivel === 'I' || preview.nivel === 'II') {
        motion.pulseUrgency(priorityPanelEl, { root: container });
      } else {
        motion.pulseSubtle(priorityPanelEl);
      }
    }
  }

  async function recalculatePriority() {
    const selected = Array.from(state.selectedSymptoms.values());
    if (selected.length === 0) {
      state.calcRequestSeq += 1;
      state.calculatedPreview = null;
      renderCalculatedPriority();
      return;
    }

    state.calculatedPreview = computeLocalPreview(selected);
    renderCalculatedPriority();

    const seq = ++state.calcRequestSeq;
    const payload = {
      sintomas: selected.map(s => ({
        codigo: s.codigo,
        duracionHoras: s.duracionHoras,
        intensidad: s.intensidad
      }))
    };

    try {
      const serverCalc = await emergencyApi.calcularNivelTriaje(payload);
      if (seq !== state.calcRequestSeq) return;
      if (serverCalc && serverCalc.nivel) {
        state.calculatedPreview = serverCalc;
        renderCalculatedPriority();
      }
    } catch {
      // Mantiene la vista previa determinista local ante fallos transitorios de red
    }
  }

  function renderCategoryChips() {
    const categories = ['TODOS', ...new Set(state.catalog.map(s => s.categoria).filter(Boolean))];
    categoryChipsEl.innerHTML = categories.map(cat => `
      <button
        type="button"
        class="chip ${state.activeCategory === cat ? 'is-selected' : ''}"
        data-category="${esc(cat)}"
        style="padding: var(--space-1) var(--space-3); min-height: 2rem; font-size: var(--text-xs);"
      >
        ${esc(formatCategoryLabel(cat))}
      </button>
    `).join('');

    categoryChipsEl.querySelectorAll('button[data-category]').forEach(btn => {
      btn.addEventListener('click', () => {
        const cat = btn.getAttribute('data-category') || 'TODOS';
        state.activeCategory = cat;
        renderCategoryChips();
        const activeBtn = Array.from(categoryChipsEl.querySelectorAll('button[data-category]'))
          .find(el => el.getAttribute('data-category') === cat);
        if (activeBtn) {
          motion.springPop(activeBtn, { scale: 0.92, y: 0, duration: 220, root: container });
        }
        renderSymptomCatalog();
      });
    });
  }

  function renderSelectedSymptoms(newlyAddedCode = null) {
    const selected = Array.from(state.selectedSymptoms.values());
    selectedCounterEl.textContent = `${selected.length} síntoma(s) seleccionados`;

    if (selected.length === 0) {
      selectedContainerEl.innerHTML = '';
      return;
    }

    selectedContainerEl.innerHTML = selected.map((item, idx) => `
      <div class="card p-3" data-selected-symptom="${esc(item.codigo)}" style="border-left: 4px solid ${item.esAlarma ? 'var(--danger)' : 'var(--primary)'}; background-color: var(--surface);">
        <div class="flex flex-wrap items-center justify-between gap-2 mb-3">
          <div class="flex flex-wrap items-center gap-2">
            <span class="badge ${item.esAlarma ? 'badge--triage-1' : 'badge--scheduled'}">${idx + 1}</span>
            <span class="font-bold text-sm">${esc(item.nombre)}</span>
            ${item.esAlarma ? '<span class="badge badge--triage-1 text-xs">Alarma</span>' : ''}
          </div>
          <button
            type="button"
            class="btn btn-ghost btn--xs btn-remove-symptom"
            data-code="${esc(item.codigo)}"
            aria-label="Quitar ${esc(item.nombre)}"
          >
            ${ui.icon('x', 'icon icon--xs')}
            <span>Quitar</span>
          </button>
        </div>

        <div class="form-group mb-3">
          <div class="flex flex-wrap items-center justify-between gap-2 mb-1">
            <label class="form-label text-xs font-semibold m-0">Intensidad (0 - 10):</label>
            <span class="badge badge--neutral text-xs font-bold" id="nursing-intensity-label-${esc(item.codigo)}">
              ${item.intensidad} / 10 · ${getIntensityDescription(item.intensidad)}
            </span>
          </div>
          <div class="scale-selector" role="group" aria-label="Intensidad para ${esc(item.nombre)}">
            ${[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(val => `
              <button
                type="button"
                class="scale-btn ${item.intensidad === val ? 'is-selected' : ''}"
                data-code="${esc(item.codigo)}"
                data-val="${val}"
                aria-pressed="${item.intensidad === val}"
              >
                ${val}
              </button>
            `).join('')}
          </div>
        </div>

        <div class="form-group m-0">
          <label for="duration-${esc(item.codigo)}" class="form-label text-xs font-semibold">Duración aproximada (horas):</label>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="flex items-center gap-2">
              <input
                type="number"
                id="duration-${esc(item.codigo)}"
                class="form-input duration-input"
                style="max-width: 110px;"
                min="0"
                step="1"
                value="${item.duracionHoras}"
                data-code="${esc(item.codigo)}"
                aria-label="Duración en horas para ${esc(item.nombre)}"
              >
              <span class="text-xs text-muted">horas</span>
            </div>
            <div class="flex flex-wrap gap-1">
              ${[
                { label: '1 h', val: 1 },
                { label: '6 h', val: 6 },
                { label: '12 h', val: 12 },
                { label: '24 h', val: 24 },
                { label: '48 h', val: 48 }
              ].map(preset => `
                <button
                  type="button"
                  class="btn btn-secondary btn--sm duration-preset"
                  data-code="${esc(item.codigo)}"
                  data-hours="${preset.val}"
                  style="font-size: var(--text-xs); padding: 2px 8px; min-height: 28px;"
                >
                  ${preset.label}
                </button>
              `).join('')}
            </div>
          </div>
        </div>
      </div>
    `).join('');

    if (newlyAddedCode) {
      const addedCard = Array.from(selectedContainerEl.querySelectorAll('[data-selected-symptom]'))
        .find(el => el.getAttribute('data-selected-symptom') === newlyAddedCode);
      if (addedCard) {
        motion.revealPanel(addedCard, { root: container });
      }
    }

    selectedContainerEl.querySelectorAll('.btn-remove-symptom').forEach(btn => {
      btn.addEventListener('click', () => {
        const code = btn.getAttribute('data-code');
        state.selectedSymptoms.delete(code);
        renderSymptomCatalog();
        renderSelectedSymptoms();
        recalculatePriority();
      });
    });

    selectedContainerEl.querySelectorAll('.scale-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const code = btn.getAttribute('data-code');
        const val = parseInt(btn.getAttribute('data-val'), 10);
        const item = state.selectedSymptoms.get(code);
        if (!item) return;

        item.intensidad = val;
        selectedContainerEl.querySelectorAll('.scale-btn').forEach(b => {
          if (b.getAttribute('data-code') !== code) return;
          const isCur = parseInt(b.getAttribute('data-val'), 10) === val;
          b.classList.toggle('is-selected', isCur);
          b.setAttribute('aria-pressed', isCur ? 'true' : 'false');
        });
        motion.springPop(btn, { scale: 0.9, y: 0, duration: 220, root: container });
        const label = Array.from(selectedContainerEl.querySelectorAll('[id]'))
          .find(el => el.id === `nursing-intensity-label-${code}`);
        if (label) {
          label.textContent = `${val} / 10 · ${getIntensityDescription(val)}`;
        }
        recalculatePriority();
      });
    });

    selectedContainerEl.querySelectorAll('.duration-input').forEach(input => {
      input.addEventListener('input', (e) => {
        const code = input.getAttribute('data-code');
        const item = state.selectedSymptoms.get(code);
        if (!item) return;
        item.duracionHoras = Math.max(0, parseFloat(e.target.value) || 0);
        recalculatePriority();
      });
    });

    selectedContainerEl.querySelectorAll('.duration-preset').forEach(btn => {
      btn.addEventListener('click', () => {
        const code = btn.getAttribute('data-code');
        const hours = parseInt(btn.getAttribute('data-hours'), 10);
        const item = state.selectedSymptoms.get(code);
        if (!item) return;
        item.duracionHoras = hours;
        const input = Array.from(selectedContainerEl.querySelectorAll('.duration-input'))
          .find(el => el.getAttribute('data-code') === code);
        if (input) input.value = String(hours);
        recalculatePriority();
      });
    });
  }

  function renderSymptomCatalog() {
    if (state.catalog.length === 0) {
      symptomsListEl.innerHTML = `<span class="text-xs text-muted">No se encontraron síntomas activos en el catálogo.</span>`;
      return;
    }

    const term = state.searchTerm.toLowerCase().trim();
    const cat = state.activeCategory;
    const filtered = state.catalog.filter(s => {
      const matchesSearch = !term
        || (s.nombre || '').toLowerCase().includes(term)
        || (s.categoria || '').toLowerCase().includes(term);
      const matchesCategory = cat === 'TODOS' || s.categoria === cat;
      return matchesSearch && matchesCategory;
    });

    if (filtered.length === 0) {
      symptomsListEl.innerHTML = `
        <div class="p-3 text-center text-muted text-xs w-full">
          No hay síntomas coincidentes con "${esc(state.searchTerm)}".
        </div>
      `;
      return;
    }

    symptomsListEl.innerHTML = filtered.map(s => {
      const isSelected = state.selectedSymptoms.has(s.codigo);
      const isAlarm = Boolean(s.esAlarma);
      const chipClass = `chip ${isSelected ? 'is-selected' : ''} ${isAlarm ? 'chip--alarm' : ''}`;
      return `
        <button
          type="button"
          class="${chipClass}"
          data-code="${esc(s.codigo)}"
          role="checkbox"
          aria-checked="${isSelected}"
          aria-label="${esc(s.nombre)}${isAlarm ? ' (Síntoma de alarma)' : ''}"
          style="padding: var(--space-1) var(--space-3); min-height: 2.25rem; font-size: var(--text-xs);"
        >
          ${isAlarm ? ui.icon('alert-triangle', 'icon icon--xs') : (isSelected ? ui.icon('check', 'icon icon--xs') : '')}
          <span>${esc(s.nombre)}</span>
          ${isAlarm ? '<span class="badge badge--triage-1" style="font-size: 10px; padding: 1px 4px; margin-left: 2px;">Alarma</span>' : ''}
        </button>
      `;
    }).join('');

    symptomsListEl.querySelectorAll('button[data-code]').forEach(btn => {
      btn.addEventListener('click', () => {
        const code = btn.getAttribute('data-code');
        const symptom = state.catalog.find(s => s.codigo === code);
        if (!symptom) return;

        let addedCode = null;
        if (state.selectedSymptoms.has(code)) {
          state.selectedSymptoms.delete(code);
        } else {
          if (state.selectedSymptoms.size >= 20) {
            ui.showToast('No se admiten más de 20 síntomas por valoración.', 'warning');
            return;
          }
          state.selectedSymptoms.set(code, {
            codigo: symptom.codigo,
            nombre: symptom.nombre,
            categoria: symptom.categoria,
            esAlarma: Boolean(symptom.esAlarma),
            intensidad: 5,
            duracionHoras: 1
          });
          addedCode = code;
        }

        renderSymptomCatalog();
        const activeChip = Array.from(symptomsListEl.querySelectorAll('button[data-code]'))
          .find(el => el.getAttribute('data-code') === code);
        if (activeChip) {
          motion.springPop(activeChip, { scale: 0.92, y: 0, duration: 220, root: container });
        }
        renderSelectedSymptoms(addedCode);
        recalculatePriority();
      });
    });
  }

  searchInput.addEventListener('input', (e) => {
    state.searchTerm = e.target.value;
    renderSymptomCatalog();
  });

  // Cargar detalle, historial y catálogo de síntomas
  try {
    const [ep, historial, catalogo] = await Promise.all([
      emergencyApi.obtenerDetalleEpisodio(episodePublicId),
      emergencyApi.listarHistorialTriaje(episodePublicId),
      api.get('/triage/symptoms')
    ]);

    episodioActual = ep;
    renderEpisodeSummary(episodioActual, episodePublicId);
    document.getElementById('txtMotivo').value = episodioActual.motivoConsulta || episodioActual.motivoLlegada || '';

    valoracionesPrevias = Array.isArray(historial) ? historial : [];
    renderHistory(valoracionesPrevias, container);

    if (valoracionesPrevias.length > 0) {
      const bloqueReeval = document.getElementById('bloqueReevaluacion');
      bloqueReeval.style.display = 'block';
      document.getElementById('formAssessmentTitle').textContent = `Reevaluación Clínica de Urgencias (Versión ${valoracionesPrevias.length + 1})`;
      motion.revealPanel(bloqueReeval, { root: container });
    }

    state.catalog = Array.isArray(catalogo) ? catalogo : [];
    renderCategoryChips();
    renderSymptomCatalog();
  } catch (err) {
    ui.showToast('Error cargando información: ' + err.message, 'error');
  }

  // Envío del formulario
  const form = document.getElementById('triageAssessmentForm');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    if (state.selectedSymptoms.size === 0) {
      ui.showToast('Debe seleccionar al menos un síntoma para calcular el nivel de triaje.', 'warning');
      searchInput.focus();
      return;
    }

    const txtMotivo = document.getElementById('txtMotivo');
    const motivoConsulta = txtMotivo.value.trim();
    if (!motivoConsulta) {
      txtMotivo.classList.add('form-textarea--error');
      txtMotivo.focus();
      ui.showToast('El motivo de consulta clínica es obligatorio.', 'warning');
      return;
    }

    const hallazgosClinicos = document.getElementById('txtHallazgos').value.trim();
    const presionArterial = document.getElementById('txtPA').value.trim();
    const fc = document.getElementById('txtFC').value ? parseInt(document.getElementById('txtFC').value, 10) : null;
    const fr = document.getElementById('txtFR').value ? parseInt(document.getElementById('txtFR').value, 10) : null;
    const so2 = document.getElementById('txtSO2').value ? parseInt(document.getElementById('txtSO2').value, 10) : null;
    const temp = document.getElementById('txtTemp').value ? parseFloat(document.getElementById('txtTemp').value) : null;
    const glasgow = document.getElementById('txtGlasgow').value ? parseInt(document.getElementById('txtGlasgow').value, 10) : null;

    const esReevaluacion = valoracionesPrevias.length > 0;
    const txtReeval = document.getElementById('txtMotivoReevaluacion');
    const motivoReevaluacion = esReevaluacion ? txtReeval.value.trim() : null;

    if (esReevaluacion && !motivoReevaluacion) {
      txtReeval.classList.add('form-textarea--error');
      txtReeval.focus();
      ui.showToast('El motivo de la reevaluación es obligatorio.', 'warning');
      return;
    }

    const sintomas = Array.from(state.selectedSymptoms.values()).map(s => ({
      codigo: s.codigo,
      duracionHoras: s.duracionHoras,
      intensidad: s.intensidad
    }));

    const payload = {
      sintomas,
      motivoConsulta,
      hallazgosClinicos: hallazgosClinicos || null,
      presionArterial: presionArterial || null,
      frecuenciaCardiaca: fc,
      frecuenciaRespiratoria: fr,
      saturacionOxigeno: so2,
      temperatura: temp,
      escalaGlasgow: glasgow,
      esReevaluacion,
      motivoReevaluacion
    };

    const btnSubmit = document.getElementById('btnSubmitAssessment');
    ui.setButtonLoading(btnSubmit, true);

    try {
      const saved = await emergencyApi.registrarValoracionTriaje(episodePublicId, payload);
      const nivelCalculado = saved?.nivel || saved?.nivelAsignado || state.calculatedPreview?.nivel || '';
      ui.showToast(
        nivelCalculado
          ? `Clasificación Triaje Nivel ${nivelCalculado} calculada y registrada con éxito.`
          : 'Clasificación de triaje calculada y registrada con éxito.',
        'success'
      );
      router.navigate('/nursing/dashboard');
    } catch (err) {
      ui.setButtonLoading(btnSubmit, false);
      ui.showToast('Error al registrar triaje: ' + (err.message || 'Error de servidor'), 'error');
    }
  });
}

function renderEpisodeSummary(ep, fallbackEpisodeId) {
  const container = document.getElementById('episodeSummaryCard');
  const isNN = Boolean(ep.esIdentidadProvisional);
  const patientName = ep.pacienteNombre || ep.pacienteNombreCompleto || 'Paciente No Identificado';
  const episodioId = ep.episodioPublicId || ep.publicId || fallbackEpisodeId;

  container.innerHTML = `
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <div class="flex flex-wrap items-center gap-2">
          ${isNN ? `<span class="badge badge--warning font-bold">PROVISIONAL NN</span>` : ''}
          <h1 class="text-xl font-bold m-0">${esc(patientName)}</h1>
        </div>
        <div class="text-xs text-muted mt-1 font-mono break-all">
          Episodio: ${esc(episodioId)} · Sede: ${esc(ep.sedeNombre || 'Valledupar')}
        </div>
        ${isNN && ep.codigoProvisional ? `<div class="text-xs text-indigo-700 font-bold mt-1">Código Identificador: ${esc(ep.codigoProvisional)}</div>` : ''}
      </div>

      <div class="text-right">
        <div class="text-xs text-muted">Estado Actual</div>
        <div class="badge ${ep.estado === 'EN_ATENCION' ? 'badge--in-progress' : 'badge--scheduled'} text-sm mt-1">
          ${esc(ep.estado)}
        </div>
      </div>
    </div>
  `;
}

function renderHistory(lista, rootContainer) {
  const historyEl = document.getElementById('triageHistoryContainer');
  if (!lista || lista.length === 0) {
    historyEl.innerHTML = `<p class="text-sm text-muted m-0">No se registran valoraciones clínicas previas para este episodio.</p>`;
    return;
  }

  historyEl.innerHTML = `
    <div class="flex flex-col gap-3">
      ${lista.map((v, index) => `
        <div class="card p-3 border triage-history-item" style="background-color: var(--surface-2);">
          <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
            <div class="flex flex-wrap items-center gap-2">
              <span class="badge badge--dark text-xs font-bold">Versión ${esc(v.version ?? (index + 1))}</span>
              ${formatTriageBadge(v.nivel)}
              ${v.esReevaluacion ? `<span class="badge badge--warning text-xs">Reevaluación</span>` : ''}
            </div>
            <div class="text-xs text-muted font-mono">
              ${formatDateTime(v.creadoAt || v.fechaRegistro)} · Por: ${esc(v.evaluadorNombre || 'Profesional')}
            </div>
          </div>

          ${v.motivoConsulta ? `
            <div class="text-sm mb-1">
              <span class="font-semibold">Motivo:</span> ${esc(v.motivoConsulta)}
            </div>
          ` : ''}

          ${Array.isArray(v.sintomas) && v.sintomas.length > 0 ? `
            <div class="flex flex-wrap gap-1 mb-2">
              ${v.sintomas.map(s => `
                <span class="badge ${s.esAlarma ? 'badge--triage-1' : 'badge--neutral'} text-xs">
                  ${esc(s.nombre || s.codigo)} (${esc(s.intensidad)}/10 · ${esc(s.duracionHoras)}h)
                </span>
              `).join('')}
            </div>
          ` : ''}

          ${v.hallazgosClinicos ? `
            <div class="text-xs text-muted mb-2">
              <span class="font-semibold">Hallazgos:</span> ${esc(v.hallazgosClinicos)}
            </div>
          ` : ''}

          <div class="grid grid-cols-2 md:grid-cols-6 gap-2 text-xs font-mono p-2 rounded border" style="background-color: var(--surface);">
            <div>PA: <strong>${esc(v.presionArterial || '—')}</strong></div>
            <div>FC: <strong>${v.frecuenciaCardiaca ? esc(v.frecuenciaCardiaca) + ' lpm' : '—'}</strong></div>
            <div>FR: <strong>${v.frecuenciaRespiratoria ? esc(v.frecuenciaRespiratoria) + ' rpm' : '—'}</strong></div>
            <div>SpO2: <strong>${v.saturacionOxigeno ? esc(v.saturacionOxigeno) + '%' : '—'}</strong></div>
            <div>Temp: <strong>${v.temperatura ? esc(v.temperatura) + '°C' : '—'}</strong></div>
            <div>Glasgow: <strong>${v.escalaGlasgow ? esc(v.escalaGlasgow) + '/15' : '—'}</strong></div>
          </div>

          ${v.motivoReevaluacion ? `
            <div class="text-xs text-amber-800 mt-2 font-medium">
              Motivo reevaluación: ${esc(v.motivoReevaluacion)}
            </div>
          ` : ''}
        </div>
      `).join('')}
    </div>
  `;

  motion.staggerCards(historyEl.querySelectorAll('.triage-history-item'), { root: rootContainer });
}
