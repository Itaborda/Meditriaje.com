/**
 * Pruebas unitarias para la capa de movimiento clínico (motion.js) y su comportamiento
 * seguro en Node/SSR y bajo prefers-reduced-motion.
 */
import test from 'node:test';
import assert from 'node:assert/strict';

test('motion.js se importa en Node sin errores y reporta reduced motion', async () => {
  const { motion, isMotionReduced } = await import('../js/motion.js');
  assert.ok(motion, 'motion debe estar definido y exportado');
  assert.equal(typeof isMotionReduced, 'function');
  assert.equal(isMotionReduced(), true, 'En entorno Node sin DOM debe reportar movimiento reducido');
});

test('animateCounter asigna valor entero, sufijo % y fallback no numérico sin lanzar', async () => {
  const { motion } = await import('../js/motion.js');

  const countEl = { textContent: '', dataset: {} };
  motion.animateCounter(countEl, 42);
  assert.equal(countEl.textContent, '42');
  assert.equal(countEl.dataset.motionVal, '42');

  const pctEl = { textContent: '', dataset: {} };
  motion.animateCounter(pctEl, 84, { suffix: '%' });
  assert.equal(pctEl.textContent, '84%');
  assert.equal(pctEl.dataset.motionVal, '84');

  const dashEl = { textContent: '', dataset: { motionVal: '12' } };
  motion.animateCounter(dashEl, '—');
  assert.equal(dashEl.textContent, '—');
  assert.equal(dashEl.dataset.motionVal, undefined);
});

test('Helpers de movimiento toleran entradas nulas o vacías de forma segura', async () => {
  const { motion } = await import('../js/motion.js');

  assert.doesNotThrow(() => {
    motion.enterView(null);
    motion.staggerCards([]);
    motion.resetViewScope();
    motion.stepTransition(null);
    motion.drawSvg(null);
    motion.pulseSubtle(null);
    motion.springPop([]);
    motion.animateProgress([]);
    motion.revealPanel(null);
    motion.pulseUrgency([]);
    motion.spinOnce(null);
    motion.animateModalOpen(null);
    motion.animateToastIn(null);
  });
});

test('animateProgress, springPop y spinOnce están exportados y son seguros sin DOM', async () => {
  const { motion, springPop, animateProgress, revealPanel, pulseUrgency, spinOnce } = await import('../js/motion.js');
  for (const fn of [springPop, animateProgress, revealPanel, pulseUrgency, spinOnce]) {
    assert.equal(typeof fn, 'function');
  }
  assert.equal(typeof motion.animateModalOpen, 'function');
  assert.equal(typeof motion.animateToastIn, 'function');
});
