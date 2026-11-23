/**
 * MediTriaje 2.0 — Capa de Movimiento Clínico (motion.js)
 * Integración sobria de Anime.js v4.5.0 vendoreado localmente.
 * Respeta prefers-reduced-motion y el widget de accesibilidad (html.a11y-no-motion).
 */

import {
  animate,
  createScope,
  createTimeline,
  onScroll,
  spring,
  stagger,
  svg,
  utils
} from './lib/anime.esm.min.js';

const counterAnims = new WeakMap();
const cleanupFns = new Set();
let activeScope = null;
let activeScopeRoot = null;
let motionWatcherInitialized = false;

/**
 * Indica si las animaciones deben omitirse por preferencia del sistema,
 * ajuste de accesibilidad en UI o entorno sin DOM (Node tests).
 * @returns {boolean}
 */
export function isMotionReduced() {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return true;
  }
  const sysReduced = Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);
  const a11yPaused = Boolean(document.documentElement?.classList.contains('a11y-no-motion'));
  return sysReduced || a11yPaused;
}

function cleanStyles(anim) {
  try {
    utils.cleanInlineStyles(anim);
  } catch {
    // El nodo puede haberse desmontado antes de finalizar la animación
  }
}

function resolveElements(targets, root = typeof document !== 'undefined' ? document : null) {
  if (!targets) return [];
  if (typeof targets === 'string') {
    if (!root || typeof root.querySelectorAll !== 'function') return [];
    return Array.from(root.querySelectorAll(targets));
  }
  if (typeof Element !== 'undefined' && targets instanceof Element) {
    return [targets];
  }
  if (Array.isArray(targets) || (typeof targets.length === 'number' && typeof targets !== 'function')) {
    return Array.from(targets).filter(Boolean);
  }
  return [];
}

function ensureMotionWatchers() {
  if (motionWatcherInitialized || typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }
  motionWatcherInitialized = true;

  const handleMotionChange = () => {
    if (isMotionReduced()) {
      resetViewScope();
    }
  };

  try {
    const mq = window.matchMedia?.('(prefers-reduced-motion: reduce)');
    mq?.addEventListener?.('change', handleMotionChange);
  } catch {
    // Ignorar en entornos sin soporte completo de matchMedia
  }

  if (typeof MutationObserver !== 'undefined' && document.documentElement) {
    const observer = new MutationObserver(handleMotionChange);
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class']
    });
  }
}

/**
 * Revierte y limpia el Scope de Anime.js de la vista anterior al cambiar de ruta.
 */
export function resetViewScope() {
  for (const fn of cleanupFns) {
    try {
      fn();
    } catch {
      // No-op al limpiar listeners de la vista previa
    }
  }
  cleanupFns.clear();

  if (activeScope) {
    try {
      activeScope.revert();
    } catch {
      // No-op si el árbol DOM ya fue reemplazado
    }
    activeScope = null;
    activeScopeRoot = null;
  }
}

function runInScope(root, callback) {
  if (isMotionReduced() || typeof callback !== 'function') return;
  ensureMotionWatchers();

  const appRoot = typeof document !== 'undefined' ? document.getElementById('app') : null;
  const candidate = root || (typeof document !== 'undefined' ? document.body : null);
  const scopeRoot = appRoot && candidate && (candidate === appRoot || appRoot.contains(candidate))
    ? appRoot
    : candidate;
  if (!scopeRoot) return;

  if (!activeScope || (activeScopeRoot !== scopeRoot && !activeScopeRoot?.contains?.(scopeRoot))) {
    resetViewScope();
    try {
      activeScope = createScope({ root: scopeRoot });
      activeScopeRoot = scopeRoot;
    } catch {
      callback();
      return;
    }
  }

  activeScope.add(callback);
}

/**
 * Transición suave de entrada al renderizar una vista de ruta.
 * Se omite si la vista declara data-motion-custom="true".
 * @param {HTMLElement|null} container
 */
export function enterView(container) {
  if (!container || isMotionReduced()) return;
  if (container.querySelector?.('[data-motion-custom="true"]')) return;

  const target = container.firstElementChild || container;
  if (!target) return;

  runInScope(container, () => {
    animate(target, {
      opacity: [0, 1],
      translateY: [8, 0],
      duration: 220,
      ease: 'outQuad',
      onComplete: cleanStyles
    });
  });
}

/**
 * Aparición escalonada discreta para grupos de tarjetas o KPIs.
 * @param {NodeList|Element[]|Element|string} targets
 * @param {Object} options
 */
export function staggerCards(targets, { delayStep = 35, maxDelay = 240, duration = 260, y = 10, root } = {}) {
  if (isMotionReduced()) return;
  const els = resolveElements(targets, root);
  if (els.length === 0) return;

  const scopeRoot = root || els[0].parentElement || document.body;
  runInScope(scopeRoot, () => {
    animate(els, {
      opacity: [0, 1],
      translateY: [y, 0],
      duration,
      delay: (_, i) => Math.min(i * delayStep, maxDelay),
      ease: 'outQuart',
      onComplete: cleanStyles
    });
  });
}

/**
 * Conteo numérico suave para métricas KPI con deduplicación en recargas.
 * @param {HTMLElement|Object|null} el
 * @param {number|string} targetValue
 * @param {Object} options
 */
export function animateCounter(el, targetValue, { prefix = '', suffix = '', duration = 420, decimals = 0, fromZero = false } = {}) {
  if (!el) return;
  if (!el.dataset) el.dataset = {};

  const num = typeof targetValue === 'number' ? targetValue : Number(targetValue);
  const existing = counterAnims.get(el);

  if (!Number.isFinite(num)) {
    if (existing) {
      try { existing.pause(); } catch {}
      counterAnims.delete(el);
    }
    delete el.dataset.motionVal;
    el.textContent = String(targetValue ?? '—');
    return;
  }

  const formatValue = (val) => `${prefix}${decimals > 0 ? Number(val).toFixed(decimals) : Math.round(val)}${suffix}`;
  const prevRaw = el.dataset.motionVal;
  const hasPrev = prevRaw !== undefined && Number.isFinite(Number(prevRaw));
  const prev = fromZero ? 0 : (hasPrev ? Number(prevRaw) : 0);

  // Evita re-animar si el valor no cambió (útil en polling cada 30s) o si hay motion reducido
  if (isMotionReduced() || (!fromZero && hasPrev && prev === num)) {
    if (existing) {
      try { existing.pause(); } catch {}
      counterAnims.delete(el);
    }
    el.dataset.motionVal = String(num);
    el.textContent = formatValue(num);
    return;
  }

  if (existing) {
    try { existing.pause(); } catch {}
    counterAnims.delete(el);
  }

  el.dataset.motionVal = String(num);
  const counterState = { val: prev };

  const anim = animate(counterState, {
    val: num,
    duration,
    ease: 'outExpo',
    onUpdate: () => {
      el.textContent = formatValue(counterState.val);
    },
    onComplete: () => {
      el.textContent = formatValue(num);
      counterAnims.delete(el);
    }
  });
  counterAnims.set(el, anim);
}

/**
 * Micro-animación de trazo SVG con svg.createDrawable.
 * @param {NodeList|Element[]|Element|string} targets
 * @param {Object} options
 */
export function drawSvg(targets, { duration = 650, delay = 0, root } = {}) {
  if (isMotionReduced() || !targets) return;
  const baseEls = resolveElements(targets, root);
  if (baseEls.length === 0) return;

  const shapes = [];
  for (const el of baseEls) {
    const tag = String(el.tagName || '').toLowerCase();
    if (tag === 'svg') {
      shapes.push(...el.querySelectorAll('path, polyline, line, circle, rect'));
    } else {
      shapes.push(el);
    }
  }
  if (shapes.length === 0) return;

  const scopeRoot = root || baseEls[0].closest?.('#main-content') || document.body;
  runInScope(scopeRoot, () => {
    try {
      const drawables = svg.createDrawable(shapes);
      animate(drawables, {
        draw: ['0 0', '0 1'],
        duration,
        delay,
        ease: 'inOutQuad',
        onComplete: cleanStyles
      });
    } catch {
      // Silencioso si el navegador/SVG no expone getTotalLength en algún shape
    }
  });
}

/**
 * Revela elementos una sola vez al entrar en viewport sin ocultar el CSS base.
 * @param {Element|null} triggerEl
 * @param {NodeList|Element[]|Element|string} targets
 * @param {Object} options
 */
export function revealOnScroll(triggerEl, targets, { y = 14, duration = 360, delayStep = 45, onReveal = null, root } = {}) {
  const scopeRoot = root || triggerEl?.parentElement || (typeof document !== 'undefined' ? document.body : null);
  const els = resolveElements(targets, scopeRoot);

  if (isMotionReduced()) {
    onReveal?.();
    return;
  }

  if (!triggerEl || els.length === 0) return;

  // Si ya está visible al cargar (pantallas altas o scroll restaurado), revela de inmediato
  const rect = triggerEl.getBoundingClientRect();
  if (rect.top < window.innerHeight * 0.92) {
    staggerCards(els, { y, duration, delayStep, root: scopeRoot });
    onReveal?.();
    return;
  }

  let revealed = false;
  let detachScrollGuard = () => {};
  const markRevealed = () => {
    if (revealed) return;
    revealed = true;
    detachScrollGuard();
    onReveal?.();
  };

  runInScope(scopeRoot, () => {
    const anim = animate(els, {
      opacity: [0, 1],
      translateY: [y, 0],
      duration,
      delay: stagger(delayStep),
      ease: 'outQuart',
      autoplay: onScroll({
        target: triggerEl,
        enter: 'bottom-=40 top',
        sync: 'play',
        repeat: false,
        onEnter: markRevealed
      }),
      onComplete: (instance) => {
        markRevealed();
        cleanStyles(instance);
      }
    });

    const onFastJump = () => {
      if (revealed) return;
      if (triggerEl.getBoundingClientRect().top < window.innerHeight * 0.92) {
        markRevealed();
        try { anim.play(); } catch {}
      }
    };

    detachScrollGuard = () => {
      window.removeEventListener('scroll', onFastJump);
      cleanupFns.delete(detachScrollGuard);
    };

    window.addEventListener('scroll', onFastJump, { passive: true });
    cleanupFns.add(detachScrollGuard);
  });
}

/**
 * Transición horizontal suave entre pasos de asistente o resultado de triaje.
 * @param {Element|null} el
 * @param {'forward'|'back'} direction
 */
export function stepTransition(el, direction = 'forward') {
  if (!el || isMotionReduced()) return;
  const startX = direction === 'back' ? -14 : 14;
  const scopeRoot = el.closest?.('#main-content') || el.parentElement || document.body;

  runInScope(scopeRoot, () => {
    animate(el, {
      opacity: [0, 1],
      translateX: [startX, 0],
      duration: 220,
      ease: 'outQuart',
      onComplete: cleanStyles
    });
  });
}

/**
 * Pulso táctil mínimo al actualizar el resultado interactivo del simulador.
 * @param {Element|null} el
 */
export function pulseSubtle(el) {
  if (!el || isMotionReduced()) return;
  const scopeRoot = el.closest?.('#main-content') || el.parentElement || document.body;

  runInScope(scopeRoot, () => {
    animate(el, {
      scale: [0.992, 1],
      duration: 180,
      ease: 'outQuad',
      onComplete: cleanStyles
    });
  });
}

/**
 * Entrada elástica discreta con física de resorte (Anime.js v4 spring).
 * @param {NodeList|Element[]|Element|string} targets
 * @param {Object} options
 */
export function springPop(targets, { scale = 0.94, y = 6, bounce = 0.24, duration = 340, delayStep = 30, root } = {}) {
  if (isMotionReduced()) return;
  const els = resolveElements(targets, root);
  if (els.length === 0) return;

  const scopeRoot = root || els[0].closest?.('#main-content') || els[0].parentElement || document.body;
  runInScope(scopeRoot, () => {
    animate(els, {
      opacity: [0, 1],
      scale: [scale, 1],
      translateY: [y, 0],
      delay: els.length > 1 ? stagger(delayStep) : 0,
      ease: spring({ bounce, duration }),
      onComplete: cleanStyles
    });
  });
}

/**
 * Anima barras de progreso u ocupación de 0% hasta su porcentaje objetivo.
 * @param {NodeList|Element[]|Element|string} targets
 * @param {Object} options
 */
export function animateProgress(targets, { delayStep = 45, duration = 560, root } = {}) {
  if (isMotionReduced()) return;
  const els = resolveElements(targets, root);
  if (els.length === 0) return;

  const scopeRoot = root || els[0].closest?.('#main-content') || els[0].parentElement || document.body;
  runInScope(scopeRoot, () => {
    els.forEach((bar, index) => {
      const targetWidth = bar.dataset?.progress || bar.style?.width || '0%';
      animate(bar, {
        width: ['0%', targetWidth],
        duration,
        delay: Math.min(index * delayStep, 220),
        ease: 'outQuart'
      });
    });
  });
}

/**
 * Revela con suavidad un panel, formulario colapsable o detalle inline recién abierto.
 * @param {Element|null} el
 * @param {Object} options
 */
export function revealPanel(el, { duration = 240, y = 8, root } = {}) {
  if (!el || isMotionReduced()) return;
  const scopeRoot = root || el.closest?.('#main-content') || el.parentElement || document.body;

  runInScope(scopeRoot, () => {
    animate(el, {
      opacity: [0, 1],
      translateY: [y, 0],
      duration,
      ease: 'outQuart',
      onComplete: cleanStyles
    });
  });
}

/**
 * Pulso clínico sobrio para alertas críticas, alergias graves o Triaje I/II.
 * @param {NodeList|Element[]|Element|string} targets
 * @param {Object} options
 */
export function pulseUrgency(targets, { root } = {}) {
  if (isMotionReduced()) return;
  const els = resolveElements(targets, root);
  if (els.length === 0) return;

  const scopeRoot = root || els[0].closest?.('#main-content') || els[0].parentElement || document.body;
  runInScope(scopeRoot, () => {
    animate(els, {
      scale: [0.988, 1.012, 1],
      duration: 360,
      delay: els.length > 1 ? stagger(40) : 0,
      ease: 'inOutQuad',
      onComplete: cleanStyles
    });
  });
}

/**
 * Rotación única de 360 grados para iconos de botones de recarga manual.
 * @param {Element|null} el
 * @param {Object} options
 */
export function spinOnce(el, { duration = 520 } = {}) {
  if (!el || isMotionReduced()) return;
  const svgIcon = el.matches?.('svg') ? el : el.querySelector?.('svg');
  const target = svgIcon || el;
  const scopeRoot = target.closest?.('#main-content') || target.parentElement || document.body;

  runInScope(scopeRoot, () => {
    animate(target, {
      rotate: [0, 360],
      duration,
      ease: 'outCubic',
      onComplete: cleanStyles
    });
  });
}

/**
 * Coreografía rápida para apertura de modales (independiente del scope de ruta).
 * @param {Element|null} backdropEl
 */
export function animateModalOpen(backdropEl) {
  if (!backdropEl || isMotionReduced()) return;
  const dialogEl = backdropEl.querySelector?.('.modal, .qr-scanner-dialog');
  animate(backdropEl, {
    opacity: [0, 1],
    duration: 160,
    ease: 'outQuad'
  });
  if (dialogEl) {
    animate(dialogEl, {
      opacity: [0, 1],
      scale: [0.95, 1],
      translateY: [10, 0],
      ease: spring({ bounce: 0.22, duration: 280 }),
      onComplete: cleanStyles
    });
  }
}

/**
 * Entrada suave para notificaciones Toast sin interferir con el scope de la vista activa.
 * @param {Element|null} toastEl
 */
export function animateToastIn(toastEl) {
  if (!toastEl || isMotionReduced()) return;
  animate(toastEl, {
    opacity: [0, 1],
    translateX: [18, 0],
    ease: spring({ bounce: 0.2, duration: 260 }),
    onComplete: cleanStyles
  });
}

/**
 * Coreografía de entrada del hero de la landing pública.
 * @param {HTMLElement|null} container
 */
export function heroEntrance(container) {
  if (!container || isMotionReduced()) return;

  const pill = container.querySelector('.landing-pill');
  const title = container.querySelector('.landing-headline');
  const lead = container.querySelector('.landing-lead');
  const ctaRow = container.querySelector('.landing-cta-row');
  const trustRow = container.querySelector('.landing-trust-row');
  const laptop = container.querySelector('.hero-laptop');
  const phone = container.querySelector('.hero-phone');
  const doctor = container.querySelector('.hero-doctor-wrap');
  const floatingPill = container.querySelector('.hero-floating-pill');
  const featureCards = Array.from(container.querySelectorAll('.hero-feature-strip .hero-feature-card'));
  const ecgSvgs = container.querySelectorAll('.hero-ecg-wave, .landing-cta-primary svg');

  runInScope(container, () => {
    const tl = createTimeline({
      defaults: { ease: 'outQuart', duration: 280 },
      onComplete: cleanStyles
    });

    if (pill) {
      tl.add(pill, { opacity: [0, 1], translateY: [8, 0], duration: 220 }, 0);
    }
    if (title) {
      tl.add(title, { opacity: [0, 1], translateY: [12, 0], duration: 280 }, 35);
    }
    if (lead) {
      tl.add(lead, { opacity: [0, 1], translateY: [10, 0], duration: 260 }, 85);
    }
    if (ctaRow) {
      tl.add(ctaRow, { opacity: [0, 1], translateY: [8, 0], duration: 240 }, 125);
    }
    if (trustRow) {
      tl.add(trustRow, { opacity: [0, 1], translateY: [6, 0], duration: 220 }, 160);
    }
    if (laptop) {
      tl.add(laptop, { opacity: [0, 1], translateY: [14, 0], duration: 320 }, 110);
    }
    if (phone) {
      tl.add(phone, { opacity: [0, 1], translateY: [16, 0], duration: 300 }, 175);
    }
    if (doctor) {
      tl.add(doctor, { opacity: [0, 1], translateY: [12, 0], duration: 320 }, 140);
    }
    if (floatingPill) {
      tl.add(floatingPill, { opacity: [0, 1], translateY: [10, 0], duration: 240 }, 240);
    }
    if (featureCards.length > 0) {
      tl.add(featureCards, {
        opacity: [0, 1],
        translateY: [12, 0],
        duration: 260,
        delay: stagger(40)
      }, 220);
    }

    if (ecgSvgs.length > 0) {
      drawSvg(ecgSvgs, { duration: 750, delay: 160, root: container });
    }
  });
}

export const motion = {
  isMotionReduced,
  resetViewScope,
  enterView,
  staggerCards,
  animateCounter,
  drawSvg,
  revealOnScroll,
  stepTransition,
  pulseSubtle,
  springPop,
  animateProgress,
  revealPanel,
  pulseUrgency,
  spinOnce,
  animateModalOpen,
  animateToastIn,
  heroEntrance
};
