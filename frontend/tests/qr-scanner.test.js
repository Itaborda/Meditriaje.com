/**
 * Unit tests for QR payload normalization and vendored jsQR browser bundle loading.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

import {
  parseQrPayload,
  PENDING_OPERATIONAL_QR_KEY,
  storePendingOperationalQr,
  consumePendingOperationalQr
} from '../js/qr-payload.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test('parseQrPayload extracts emergency summary token from full HTTPS URL, hash route, and relative path', () => {
  const full = parseQrPayload('  https://meditriajecom-frontend.vercel.app/#/emergency-summary/demo_001-aa  ');
  assert.deepEqual(full, {
    kind: 'emergency-summary',
    token: 'demo_001-aa',
    route: '/emergency-summary/demo_001-aa',
    raw: 'https://meditriajecom-frontend.vercel.app/#/emergency-summary/demo_001-aa'
  });

  const hashOnly = parseQrPayload('#/emergency-summary/token123?foo=bar');
  assert.deepEqual(hashOnly, {
    kind: 'emergency-summary',
    token: 'token123',
    route: '/emergency-summary/token123',
    raw: '#/emergency-summary/token123?foo=bar'
  });

  const relPath = parseQrPayload('/emergency-summary/tok%20encoded#sec');
  assert.deepEqual(relPath, {
    kind: 'emergency-summary',
    token: 'tok encoded',
    route: '/emergency-summary/tok%20encoded',
    raw: '/emergency-summary/tok%20encoded#sec'
  });
});

test('parseQrPayload normalizes QROP wristband tokens and tracking URLs', () => {
  assert.deepEqual(parseQrPayload('QROP-000111'), {
    kind: 'tracking-qr',
    token: 'QROP-000111',
    raw: 'QROP-000111'
  });

  assert.deepEqual(parseQrPayload('qrop-000222'), {
    kind: 'tracking-qr',
    token: 'QROP-000222',
    raw: 'qrop-000222'
  });

  assert.deepEqual(parseQrPayload('https://meditriaje.com/track/QROP-9988'), {
    kind: 'tracking-qr',
    token: 'QROP-9988',
    raw: 'https://meditriaje.com/track/QROP-9988'
  });

  assert.deepEqual(parseQrPayload('/api/v1/operational/tracking-qr/QROP-9988?src=cam'), {
    kind: 'tracking-qr',
    token: 'QROP-9988',
    raw: '/api/v1/operational/tracking-qr/QROP-9988?src=cam'
  });
});

test('parseQrPayload identifies prescription claim codes and plain tokens, rejecting blanks', () => {
  assert.deepEqual(parseQrPayload('REC-000111'), {
    kind: 'prescription-code',
    token: 'REC-000111',
    raw: 'REC-000111'
  });

  assert.deepEqual(parseQrPayload('plain-0001'), {
    kind: 'raw-token',
    token: 'plain-0001',
    raw: 'plain-0001'
  });

  assert.equal(parseQrPayload(''), null);
  assert.equal(parseQrPayload('   \n\t '), null);
  assert.equal(parseQrPayload(null), null);
  assert.equal(parseQrPayload(undefined), null);
});

test('pending operational QR storage helpers handle missing sessionStorage safely and round-trip when present', () => {
  assert.equal(consumePendingOperationalQr(), null);
  storePendingOperationalQr('QROP-SAFE');

  const store = new Map();
  globalThis.sessionStorage = {
    setItem: (k, v) => store.set(k, v),
    getItem: (k) => (store.has(k) ? store.get(k) : null),
    removeItem: (k) => store.delete(k)
  };
  try {
    storePendingOperationalQr('  QROP-777  ');
    assert.equal(store.get(PENDING_OPERATIONAL_QR_KEY), 'QROP-777');
    assert.equal(consumePendingOperationalQr(), 'QROP-777');
    assert.equal(consumePendingOperationalQr(), null);
  } finally {
    delete globalThis.sessionStorage;
  }
});

test('vendored frontend/js/lib/jsQR.js loads cleanly in browser-like global context', () => {
  const jsQrPath = path.resolve(__dirname, '../js/lib/jsQR.js');
  const code = fs.readFileSync(jsQrPath, 'utf8');
  const sandbox = {};
  sandbox.self = sandbox;
  sandbox.window = sandbox;

  vm.runInNewContext(code, sandbox, { filename: 'jsQR.js' });
  assert.equal(typeof sandbox.jsQR, 'function', 'jsQR debe exponerse en el contexto global del navegador');
});
