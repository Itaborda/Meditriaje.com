/**
 * MediTriaje 2.0 — Widget de Accesibilidad (accessibility-widget.js)
 *
 * Botón flotante con un panel de ajustes para personas con discapacidad visual,
 * daltonismo, dislexia o dificultades de lectura y movilidad.
 *
 * - Todo vive en este archivo: marcado, estilos (inyectados) y lógica, igual que el
 *   widget del asistente del sistema.
 * - Las preferencias se guardan en localStorage (no son datos sensibles) y se aplican
 *   al cargar cada página.
 * - Los ajustes se aplican con clases en <html>; los colores usan los tokens de
 *   css/tokens.css, así que funcionan con el tema claro y el oscuro.
 *
 * Uso: import { initAccessibilityWidget } from './components/accessibility-widget.js';
 */

const STORAGE_KEY = 'meditriaje-a11y-v1';
const HOST_ID = 'a11y-host';
const SCOPE = `body > :not(#${HOST_ID})`; // todo el sitio, excepto el propio widget

const DEFAULTS = {
  screenReader: false,
  textSize: 0, // 0 = normal, 1..3
  contrast: false,
  saturation: 0, // 0 = normal, 1 = baja, 2 = alta, 3 = monocromo
  pauseMotion: false,
  dyslexiaFont: false,
  lineHeight: 0, // 0..3
  letterSpacing: 0, // 0..3
  colorBlind: false,
  colorBlindType: 'deuteranopia',
  highlightHeadings: false,
  highlightLinks: false,
  boldText: false,
  readingGuide: false,
  bigCursor: false,
};

const LEVEL_LABELS = {
  textSize: ['Tamaño de texto', 'Texto grande', 'Texto muy grande', 'Texto máximo'],
  saturation: ['Saturación', 'Saturación baja', 'Saturación alta', 'Monocromo'],
  lineHeight: ['Interlineado', 'Interlineado medio', 'Interlineado amplio', 'Interlineado máximo'],
  letterSpacing: ['Interletrado', 'Interletrado medio', 'Interletrado amplio', 'Interletrado máximo'],
};

const COLOR_BLIND_TYPES = {
  protanopia: 'Protanopía (rojo)',
  deuteranopia: 'Deuteranopía (verde)',
  tritanopia: 'Tritanopía (azul)',
};

/* Iconos (trazo, 24x24) */
const ICONS = {
  access: '<circle cx="12" cy="5" r="1"/><path d="m9 20 3-6 3 6"/><path d="m6 8 6 2 6-2"/><path d="M12 10v4"/>',
  close: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
  speaker: '<polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14"/>',
  text: '<polyline points="4 7 4 4 20 4 20 7"/><line x1="9" x2="15" y1="20" y2="20"/><line x1="12" x2="12" y1="4" y2="20"/>',
  contrast: '<circle cx="12" cy="12" r="10"/><path d="M12 18a6 6 0 0 0 0-12v12z"/>',
  saturation: '<path d="M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z"/>',
  pause: '<circle cx="12" cy="12" r="10"/><line x1="10" x2="10" y1="15" y2="9"/><line x1="14" x2="14" y1="15" y2="9"/>',
  dyslexia: '<path d="M4 20 10 4l6 16"/><path d="M6.5 14h7"/><path d="M3 22c3-1.5 5-1.5 8 0s5 1.5 8 0"/>',
  lineHeight: '<path d="M12 4v16"/><path d="m9 7 3-3 3 3"/><path d="m9 17 3 3 3-3"/><path d="M17 8h4"/><path d="M17 12h4"/><path d="M17 16h4"/>',
  letterSpacing: '<path d="M4 4v16"/><path d="M20 4v16"/><path d="M9 12h6"/><path d="m11 10-2 2 2 2"/><path d="m13 10 2 2-2 2"/>',
  eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
  headings: '<path d="M4 6v12"/><path d="M12 6v12"/><path d="M4 12h8"/><path d="M16 9l3 3-3 3"/>',
  link: '<path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>',
  bold: '<path d="M6 12h9a4 4 0 0 1 0 8H7a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h7a4 4 0 0 1 0 8"/>',
  guide: '<line x1="3" x2="21" y1="8" y2="8"/><line x1="3" x2="21" y1="12" y2="12" stroke-width="3.5"/><line x1="3" x2="21" y1="16" y2="16"/>',
  cursor: '<path d="M4.037 4.688a.495.495 0 0 1 .651-.651l16 6.5a.5.5 0 0 1-.063.947l-6.124 1.58a2 2 0 0 0-1.438 1.435l-1.579 6.126a.5.5 0 0 1-.947.063z"/>',
  reset: '<path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/>',
};

const icon = (name, cls = 'a11y-icon') =>
  `<svg class="${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[name]}</svg>`;

/* ---------------------------------------------------------------------------
 * Daltonismo: matrices de corrección (aproximación por "daltonización").
 * No sustituyen una evaluación profesional; ayudan a distinguir colores confusos.
 * ------------------------------------------------------------------------- */
const SIMULATION = {
  protanopia: [[0.567, 0.433, 0], [0.558, 0.442, 0], [0, 0.242, 0.758]],
  deuteranopia: [[0.625, 0.375, 0], [0.7, 0.3, 0], [0, 0.3, 0.7]],
  tritanopia: [[0.95, 0.05, 0], [0, 0.433, 0.567], [0, 0.475, 0.525]],
};
const SHIFT = {
  protanopia: [[0, 0, 0], [0.7, 1, 0], [0.7, 0, 1]],
  deuteranopia: [[0, 0, 0], [0.7, 1, 0], [0.7, 0, 1]],
  tritanopia: [[1, 0, 0.7], [0, 1, 0.7], [0, 0, 0]],
};

function colorBlindMatrix(type) {
  const sim = SIMULATION[type];
  const shift = SHIFT[type];
  const rows = [];
  for (let r = 0; r < 3; r += 1) {
    const row = [];
    for (let c = 0; c < 3; c += 1) {
      // M = I + SHIFT · (I − SIM)
      let value = r === c ? 1 : 0;
      for (let k = 0; k < 3; k += 1) {
        value += shift[r][k] * ((k === c ? 1 : 0) - sim[k][c]);
      }
      row.push(value.toFixed(4));
    }
    rows.push(`${row.join(' ')} 0 0`);
  }
  rows.push('0 0 0 1 0');
  return rows.join(' ');
}

/* ---------------------------------------------------------------------------
 * Estado
 * ------------------------------------------------------------------------- */
let state = { ...DEFAULTS };
let initialized = false;
let host = null;
let panel = null;
let fab = null;
let guideEl = null;
let liveEl = null;

const speechSupported = () => typeof window !== 'undefined' && 'speechSynthesis' in window && 'SpeechSynthesisUtterance' in window;

function loadState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULTS };
    const saved = JSON.parse(raw);
    const next = { ...DEFAULTS };
    Object.keys(DEFAULTS).forEach((key) => {
      if (typeof saved[key] === typeof DEFAULTS[key]) next[key] = saved[key];
    });
    if (!COLOR_BLIND_TYPES[next.colorBlindType]) next.colorBlindType = DEFAULTS.colorBlindType;
    ['textSize', 'saturation', 'lineHeight', 'letterSpacing'].forEach((key) => {
      next[key] = Math.min(3, Math.max(0, Math.round(next[key]) || 0));
    });
    return next;
  } catch {
    return { ...DEFAULTS };
  }
}

function saveState() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    /* almacenamiento no disponible: el ajuste solo dura la sesión */
  }
}

/* ---------------------------------------------------------------------------
 * Aplicación de ajustes sobre <html>
 * ------------------------------------------------------------------------- */
function applyState() {
  const root = document.documentElement;
  const toggle = (cls, on) => root.classList.toggle(cls, Boolean(on));

  for (let i = 1; i <= 3; i += 1) {
    toggle(`a11y-text-${i}`, state.textSize === i);
    toggle(`a11y-lh-${i}`, state.lineHeight === i);
    toggle(`a11y-ls-${i}`, state.letterSpacing === i);
  }
  toggle('a11y-contrast', state.contrast);
  toggle('a11y-no-motion', state.pauseMotion);
  toggle('a11y-dyslexia', state.dyslexiaFont);
  toggle('a11y-hl-headings', state.highlightHeadings);
  toggle('a11y-hl-links', state.highlightLinks);
  toggle('a11y-bold', state.boldText);
  toggle('a11y-big-cursor', state.bigCursor);

  // Saturación y daltonismo comparten la propiedad filter
  const filters = [];
  if (state.saturation === 1) filters.push('saturate(0.5)');
  if (state.saturation === 2) filters.push('saturate(1.8)');
  if (state.saturation === 3) filters.push('grayscale(1)');
  if (state.colorBlind) filters.push(`url(#a11y-cb-${state.colorBlindType})`);
  root.style.setProperty('--a11y-filter', filters.length ? filters.join(' ') : 'none');
  toggle('a11y-filtered', filters.length > 0);

  if (guideEl) guideEl.hidden = !state.readingGuide;
  if (!state.screenReader && speechSupported()) window.speechSynthesis.cancel();
}

/* ---------------------------------------------------------------------------
 * Lector de pantalla (síntesis de voz del navegador)
 * ------------------------------------------------------------------------- */
function speak(text) {
  if (!speechSupported() || !text) return;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(String(text).replace(/\s+/g, ' ').trim().slice(0, 400));
  utterance.lang = 'es-CO';
  window.speechSynthesis.speak(utterance);
}

function onReadEvent(event) {
  if (!state.screenReader) return;
  const target = event.target;
  if (!(target instanceof Element)) return;
  const el = event.type === 'click'
    ? (target.closest('a, button, [role="button"], h1, h2, h3, h4, h5, h6, p, li, label, td, th') || target)
    : target;
  const text = el.getAttribute('aria-label') || el.getAttribute('title') || el.innerText || el.value || '';
  speak(text);
}

/* ---------------------------------------------------------------------------
 * Guía de lectura
 * ------------------------------------------------------------------------- */
function onMouseMove(event) {
  if (!state.readingGuide || !guideEl) return;
  guideEl.style.top = `${event.clientY - 28}px`;
}

/* ---------------------------------------------------------------------------
 * Interfaz del panel
 * ------------------------------------------------------------------------- */
const TILES = [
  { key: 'screenReader', type: 'toggle', icon: 'speaker', label: 'Lector de pantalla' },
  { key: 'textSize', type: 'level', icon: 'text' },
  { key: 'contrast', type: 'toggle', icon: 'contrast', label: 'Alto contraste' },
  { key: 'saturation', type: 'level', icon: 'saturation' },
  { key: 'pauseMotion', type: 'toggle', icon: 'pause', label: 'Pausar animaciones' },
  { key: 'dyslexiaFont', type: 'toggle', icon: 'dyslexia', label: 'Fuente dislexia' },
  { key: 'lineHeight', type: 'level', icon: 'lineHeight' },
  { key: 'letterSpacing', type: 'level', icon: 'letterSpacing' },
];

const SWITCHES = [
  { key: 'colorBlind', icon: 'eye', label: 'Daltonismo' },
  { key: 'highlightHeadings', icon: 'headings', label: 'Resaltar títulos', advanced: true },
  { key: 'highlightLinks', icon: 'link', label: 'Resaltar enlaces', advanced: true },
  { key: 'boldText', icon: 'bold', label: 'Texto en negrita', advanced: true },
  { key: 'readingGuide', icon: 'guide', label: 'Guía de lectura', advanced: true },
  { key: 'bigCursor', icon: 'cursor', label: 'Cursor grande', advanced: true },
];

function tileHtml(tile) {
  if (tile.type === 'level') {
    return `
      <button type="button" class="a11y-tile" data-key="${tile.key}" data-type="level" aria-pressed="false">
        ${icon(tile.icon)}
        <span class="a11y-tile-label"></span>
        <span class="a11y-bars" aria-hidden="true"><i></i><i></i><i></i></span>
      </button>`;
  }
  const disabled = tile.key === 'screenReader' && !speechSupported();
  return `
    <button type="button" class="a11y-tile" data-key="${tile.key}" data-type="toggle" aria-pressed="false"${disabled ? ' disabled title="Tu navegador no admite lectura en voz alta"' : ''}>
      ${icon(tile.icon)}
      <span class="a11y-tile-label">${tile.label}</span>
    </button>`;
}

function switchHtml(item) {
  const extra = item.key === 'colorBlind'
    ? `<div class="a11y-cb-type" id="a11y-cb-type-wrap" hidden>
         <label for="a11y-cb-type">Tipo de daltonismo</label>
         <select id="a11y-cb-type">
           ${Object.entries(COLOR_BLIND_TYPES).map(([value, text]) => `<option value="${value}">${text}</option>`).join('')}
         </select>
       </div>`
    : '';
  return `
    <div class="a11y-row-wrap">
      <div class="a11y-row">
        <span class="a11y-row-icon">${icon(item.icon)}</span>
        <span class="a11y-row-label" id="a11y-label-${item.key}">${item.label}</span>
        <button type="button" role="switch" class="a11y-switch" data-key="${item.key}" data-type="switch" aria-checked="false" aria-labelledby="a11y-label-${item.key}"><span></span></button>
      </div>
      ${extra}
    </div>`;
}

function buildUi() {
  host = document.createElement('aside');
  host.id = HOST_ID;
  host.setAttribute('aria-label', 'Herramientas de accesibilidad');

  const filtersSvg = `
    <svg width="0" height="0" style="position:absolute" aria-hidden="true" focusable="false">
      ${Object.keys(SIMULATION).map((type) => `
        <filter id="a11y-cb-${type}" color-interpolation-filters="linearRGB">
          <feColorMatrix type="matrix" values="${colorBlindMatrix(type)}"/>
        </filter>`).join('')}
    </svg>`;

  host.innerHTML = `
    ${filtersSvg}
    <button type="button" id="a11y-fab" class="a11y-fab" aria-expanded="false" aria-controls="a11y-panel" aria-label="Abrir opciones de accesibilidad">
      ${icon('access', 'a11y-icon a11y-icon--fab')}
    </button>

    <div id="a11y-panel" class="a11y-panel" role="dialog" aria-modal="false" aria-labelledby="a11y-title" hidden>
      <header class="a11y-header">
        <span class="a11y-header-icon">${icon('access')}</span>
        <div class="a11y-header-text">
          <h2 id="a11y-title">Accesibilidad</h2>
          <p>Mejora tu experiencia personalizándola</p>
        </div>
        <button type="button" class="a11y-close" id="a11y-close" aria-label="Cerrar opciones de accesibilidad">${icon('close')}</button>
      </header>

      <div class="a11y-body">
        <div class="a11y-grid">${TILES.map(tileHtml).join('')}</div>
        ${switchHtml(SWITCHES[0])}
        <h3 class="a11y-section">Opciones avanzadas</h3>
        ${SWITCHES.slice(1).map(switchHtml).join('')}
      </div>

      <footer class="a11y-footer">
        <button type="button" class="a11y-reset" id="a11y-reset">${icon('reset')} Restablecer configuración</button>
      </footer>
      <div class="a11y-sr-only" id="a11y-live" aria-live="polite"></div>
    </div>

    <div id="a11y-guide" class="a11y-guide" hidden></div>
  `;

  document.body.appendChild(host);
  panel = host.querySelector('#a11y-panel');
  fab = host.querySelector('#a11y-fab');
  guideEl = host.querySelector('#a11y-guide');
  liveEl = host.querySelector('#a11y-live');
}

/* Refleja el estado en los controles del panel */
function renderControls() {
  host.querySelectorAll('[data-type="level"]').forEach((btn) => {
    const key = btn.dataset.key;
    const level = state[key];
    btn.setAttribute('aria-pressed', String(level > 0));
    btn.classList.toggle('is-active', level > 0);
    btn.querySelector('.a11y-tile-label').textContent = LEVEL_LABELS[key][level];
    btn.querySelectorAll('.a11y-bars i').forEach((bar, index) => bar.classList.toggle('on', index < level));
  });
  host.querySelectorAll('[data-type="toggle"]').forEach((btn) => {
    const on = Boolean(state[btn.dataset.key]);
    btn.setAttribute('aria-pressed', String(on));
    btn.classList.toggle('is-active', on);
  });
  host.querySelectorAll('[data-type="switch"]').forEach((btn) => {
    const on = Boolean(state[btn.dataset.key]);
    btn.setAttribute('aria-checked', String(on));
    btn.classList.toggle('is-on', on);
  });
  const cbWrap = host.querySelector('#a11y-cb-type-wrap');
  if (cbWrap) cbWrap.hidden = !state.colorBlind;
  const cbSelect = host.querySelector('#a11y-cb-type');
  if (cbSelect) cbSelect.value = state.colorBlindType;
}

function announce(message) {
  if (liveEl) liveEl.textContent = message;
}

function describeChange(key) {
  const value = state[key];
  if (LEVEL_LABELS[key]) return value > 0 ? `${LEVEL_LABELS[key][value]} activado` : `${LEVEL_LABELS[key][0]} restablecido`;
  const item = [...TILES, ...SWITCHES].find((entry) => entry.key === key);
  const name = (item && item.label) || key;
  return `${name} ${value ? 'activado' : 'desactivado'}`;
}

function update(changedKey) {
  saveState();
  applyState();
  renderControls();
  if (changedKey) {
    const message = describeChange(changedKey);
    announce(message);
    if (state.screenReader) speak(message);
  }
}

function openPanel() {
  panel.hidden = false;
  fab.setAttribute('aria-expanded', 'true');
  const first = panel.querySelector('.a11y-tile:not([disabled])');
  if (first) first.focus();
}

function closePanel() {
  panel.hidden = true;
  fab.setAttribute('aria-expanded', 'false');
  fab.focus();
}

function bindEvents() {
  fab.addEventListener('click', () => (panel.hidden ? openPanel() : closePanel()));
  host.querySelector('#a11y-close').addEventListener('click', closePanel);

  panel.addEventListener('click', (event) => {
    const control = event.target.closest('[data-key]');
    if (!control || control.disabled) return;
    const key = control.dataset.key;
    const type = control.dataset.type;
    if (type === 'level') state[key] = (state[key] + 1) % 4;
    else state[key] = !state[key];
    update(key);
  });

  host.querySelector('#a11y-cb-type').addEventListener('change', (event) => {
    state.colorBlindType = event.target.value;
    update();
  });

  host.querySelector('#a11y-reset').addEventListener('click', () => {
    state = { ...DEFAULTS };
    update();
    announce('Configuración de accesibilidad restablecida');
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !panel.hidden) closePanel();
  });
  document.addEventListener('focusin', onReadEvent);
  document.addEventListener('click', onReadEvent);
  document.addEventListener('mousemove', onMouseMove, { passive: true });
}

/* ---------------------------------------------------------------------------
 * Estilos (inyectados una sola vez)
 * ------------------------------------------------------------------------- */
function injectStyles() {
  if (document.getElementById('a11y-widget-styles')) return;
  const cursorSvg = encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 24 24"><path d="M4.037 4.688a.495.495 0 0 1 .651-.651l16 6.5a.5.5 0 0 1-.063.947l-6.124 1.58a2 2 0 0 0-1.438 1.435l-1.579 6.126a.5.5 0 0 1-.947.063z" fill="#000" stroke="#fff" stroke-width="1.5"/></svg>'
  );
  const style = document.createElement('style');
  style.id = 'a11y-widget-styles';
  style.textContent = `
    /* ===== Widget ===== */
    #${HOST_ID} { font-family: var(--font-sans, system-ui, sans-serif); }
    .a11y-sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
    .a11y-icon { width: 22px; height: 22px; flex: none; }
    .a11y-icon--fab { width: 28px; height: 28px; }

    .a11y-fab {
      position: fixed; right: 0; top: 45%; transform: translateY(-50%);
      z-index: 1001; width: 50px; height: 58px; padding: 0 0 0 4px;
      border: 2px solid var(--surface, #fff); border-right: 0;
      border-radius: 29px 0 0 29px;
      background: var(--primary, #0f766e); color: var(--on-primary, #fff);
      box-shadow: var(--shadow-lg, 0 12px 32px rgba(15,23,42,.2));
      display: flex; align-items: center; justify-content: center; cursor: pointer;
      transition: width .15s ease, background-color .15s ease;
    }
    .a11y-fab:hover, .a11y-fab:focus-visible { width: 58px; background: var(--primary-hover, #115e59); }
    .a11y-fab:focus-visible, .a11y-tile:focus-visible, .a11y-switch:focus-visible,
    .a11y-close:focus-visible, .a11y-reset:focus-visible, #a11y-cb-type:focus-visible {
      outline: 3px solid var(--primary, #0f766e); outline-offset: 2px;
    }

    .a11y-panel {
      position: fixed; top: 16px; right: 16px; bottom: 16px; z-index: 1100;
      width: min(400px, calc(100vw - 32px));
      display: flex; flex-direction: column;
      background: var(--surface, #fff); color: var(--text, #0f172a);
      border: 1px solid var(--border, #e2e8f0); border-radius: var(--radius-xl, 20px);
      box-shadow: var(--shadow-lg, 0 12px 32px rgba(15,23,42,.2)); overflow: hidden;
    }
    .a11y-panel[hidden], .a11y-guide[hidden], .a11y-cb-type[hidden] { display: none; }

    .a11y-header { display: flex; align-items: center; gap: 12px; padding: 16px; border-bottom: 1px solid var(--border, #e2e8f0); }
    .a11y-header-icon { display: grid; place-items: center; width: 44px; height: 44px; border-radius: 12px;
      background: var(--primary-soft, #f0fdfa); color: var(--primary, #0f766e); }
    .a11y-header-text { flex: 1; min-width: 0; }
    .a11y-header-text h2 { margin: 0; font-size: 1.125rem; font-weight: 700; }
    .a11y-header-text p { margin: 2px 0 0; font-size: .8125rem; color: var(--text-muted, #475569); }
    .a11y-close { display: grid; place-items: center; width: 40px; height: 40px; border: 0; border-radius: 10px;
      background: transparent; color: var(--text, #0f172a); cursor: pointer; }
    .a11y-close:hover { background: var(--surface-2, #f1f5f9); }

    .a11y-body { flex: 1; overflow-y: auto; padding: 16px; }
    .a11y-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 14px; }

    .a11y-tile {
      position: relative; display: flex; flex-direction: column; align-items: center; justify-content: center;
      gap: 10px; min-height: 104px; padding: 14px 10px; text-align: center; cursor: pointer;
      background: var(--surface, #fff); color: var(--text, #0f172a);
      border: 1px solid var(--primary-soft-border, #99f6e4); border-radius: var(--radius-lg, 14px);
      font: inherit; font-size: .8125rem; font-weight: 600;
      transition: background-color .15s ease, border-color .15s ease;
    }
    .a11y-tile .a11y-icon { width: 30px; height: 30px; color: var(--primary, #0f766e); }
    .a11y-tile:hover:not(:disabled) { background: var(--primary-soft, #f0fdfa); }
    .a11y-tile:disabled { opacity: .5; cursor: not-allowed; }
    .a11y-tile.is-active { background: var(--primary-soft, #f0fdfa); border-color: var(--primary, #0f766e); }
    .a11y-tile.is-active::after { content: "\\2713"; position: absolute; top: 6px; right: 10px; color: var(--primary, #0f766e); font-weight: 700; }
    .a11y-bars { display: flex; gap: 6px; width: 86%; }
    .a11y-bars i { flex: 1; height: 5px; border-radius: 3px; background: var(--primary-soft-border, #99f6e4); }
    .a11y-bars i.on { background: var(--primary, #0f766e); }

    .a11y-row-wrap { margin-bottom: 10px; }
    .a11y-row { display: flex; align-items: center; gap: 12px; padding: 10px 12px;
      border: 1px solid var(--primary-soft-border, #99f6e4); border-radius: var(--radius-lg, 14px); }
    .a11y-row-icon { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 8px;
      background: var(--primary-soft, #f0fdfa); color: var(--primary, #0f766e); flex: none; }
    .a11y-row-icon .a11y-icon { width: 18px; height: 18px; }
    .a11y-row-label { flex: 1; font-size: .875rem; font-weight: 600; }
    .a11y-switch { position: relative; width: 46px; height: 26px; flex: none; padding: 0; border: 0; border-radius: 13px;
      background: var(--border-strong, #94a3b8); cursor: pointer; transition: background-color .15s ease; }
    .a11y-switch span { position: absolute; top: 3px; left: 3px; width: 20px; height: 20px; border-radius: 50%;
      background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,.3); transition: transform .15s ease; }
    .a11y-switch.is-on { background: var(--primary, #0f766e); }
    .a11y-switch.is-on span { transform: translateX(20px); }
    .a11y-cb-type { display: flex; flex-direction: column; gap: 6px; margin-top: 8px; font-size: .8125rem; }
    .a11y-cb-type select { padding: 8px 10px; border-radius: 8px; border: 1px solid var(--border-strong, #94a3b8);
      background: var(--surface, #fff); color: var(--text, #0f172a); font: inherit; }
    .a11y-section { margin: 18px 0 10px; font-size: .75rem; font-weight: 700; letter-spacing: .06em; text-transform: uppercase; color: var(--text-muted, #475569); }

    .a11y-footer { padding: 14px 16px; border-top: 1px solid var(--border, #e2e8f0); }
    .a11y-reset { width: 100%; display: flex; align-items: center; justify-content: center; gap: 8px; padding: 12px;
      border: 0; border-radius: 12px; background: var(--primary, #0f766e); color: var(--on-primary, #fff);
      font: inherit; font-weight: 700; cursor: pointer; }
    .a11y-reset:hover { background: var(--primary-hover, #115e59); }
    .a11y-reset .a11y-icon { width: 18px; height: 18px; }

    .a11y-guide { position: fixed; left: 0; right: 0; top: 40%; height: 56px; z-index: 1090; pointer-events: none;
      background: rgba(250, 204, 21, .22); border-top: 3px solid #facc15; border-bottom: 3px solid #facc15; }

    @media (max-width: 480px) {
      .a11y-panel { top: 8px; right: 8px; bottom: 8px; width: calc(100vw - 16px); }
    }

    /* ===== Ajustes aplicados al sitio ===== */
    html.a11y-text-1 { font-size: 115%; }
    html.a11y-text-2 { font-size: 130%; }
    html.a11y-text-3 { font-size: 150%; }

    html.a11y-lh-1 ${SCOPE}, html.a11y-lh-1 ${SCOPE} * { line-height: 1.6 !important; }
    html.a11y-lh-2 ${SCOPE}, html.a11y-lh-2 ${SCOPE} * { line-height: 1.85 !important; }
    html.a11y-lh-3 ${SCOPE}, html.a11y-lh-3 ${SCOPE} * { line-height: 2.1 !important; }

    html.a11y-ls-1 ${SCOPE}, html.a11y-ls-1 ${SCOPE} * { letter-spacing: .04em !important; }
    html.a11y-ls-2 ${SCOPE}, html.a11y-ls-2 ${SCOPE} * { letter-spacing: .08em !important; }
    html.a11y-ls-3 ${SCOPE}, html.a11y-ls-3 ${SCOPE} * { letter-spacing: .12em !important; }

    html.a11y-dyslexia ${SCOPE}, html.a11y-dyslexia ${SCOPE} * {
      font-family: "OpenDyslexic", "Comic Sans MS", "Comic Neue", Verdana, sans-serif !important;
      word-spacing: .12em !important;
    }
    html.a11y-bold ${SCOPE}, html.a11y-bold ${SCOPE} * { font-weight: 700 !important; }

    html.a11y-hl-headings ${SCOPE} :is(h1, h2, h3, h4, h5, h6) {
      outline: 3px solid var(--primary, #0f766e); outline-offset: 4px; background: rgba(250, 204, 21, .28) !important;
    }
    html.a11y-hl-links ${SCOPE} a {
      text-decoration: underline !important; text-decoration-thickness: 2px !important; text-underline-offset: 3px;
      background: rgba(250, 204, 21, .3); outline: 2px solid rgba(250, 204, 21, .9); outline-offset: 1px;
    }

    html.a11y-no-motion *, html.a11y-no-motion *::before, html.a11y-no-motion *::after {
      animation: none !important; transition: none !important; scroll-behavior: auto !important;
    }

    html.a11y-filtered ${SCOPE} { filter: var(--a11y-filter, none); }

    html.a11y-contrast, html.a11y-contrast[data-theme="dark"] {
      --bg: #000; --surface: #000; --surface-2: #0d0d0d; --border: #fff; --border-strong: #fff;
      --text: #fff; --text-muted: #fff; --primary: #ffe600; --primary-hover: #fff27a; --on-primary: #000;
      --primary-soft: #000; --primary-soft-border: #ffe600;
    }
    html.a11y-contrast ${SCOPE} a { color: #ffe600 !important; text-decoration: underline; }

    html.a11y-big-cursor, html.a11y-big-cursor * {
      cursor: url("data:image/svg+xml,${cursorSvg}") 4 2, auto !important;
    }

    @media (prefers-reduced-motion: reduce) {
      .a11y-fab, .a11y-tile, .a11y-switch, .a11y-switch span { transition: none; }
    }
  `;
  document.head.appendChild(style);
}

/* ---------------------------------------------------------------------------
 * Punto de entrada
 * ------------------------------------------------------------------------- */
export function initAccessibilityWidget() {
  if (initialized || document.getElementById(HOST_ID)) return;
  initialized = true;

  state = loadState();
  injectStyles();
  buildUi();
  bindEvents();
  applyState();
  renderControls();
}