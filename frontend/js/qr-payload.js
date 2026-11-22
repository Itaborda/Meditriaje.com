/**
 * Pure QR payload parser and pending tracking-token helpers.
 * Kept free of DOM dependencies so both browser modules and Node unit tests can import it.
 */

export const PENDING_OPERATIONAL_QR_KEY = 'meditriaje_pending_operational_qr';

function safeDecodeToken(value) {
  const trimmed = String(value || '').trim();
  if (!trimmed) return '';
  try {
    return decodeURIComponent(trimmed).trim();
  } catch {
    return trimmed;
  }
}

/**
 * Parses raw decoded QR text into a normalized MediTriaje payload descriptor.
 *
 * @param {string} rawText
 * @returns {{ kind: 'emergency-summary'|'tracking-qr'|'prescription-code'|'raw-token', token: string, route?: string, raw: string } | null}
 */
export function parseQrPayload(rawText) {
  if (typeof rawText !== 'string') {
    return null;
  }

  const trimmed = rawText.trim();
  if (!trimmed) {
    return null;
  }

  const emergencyMatch = trimmed.match(/(?:#\/|\/)emergency-summary\/([^/?#]+)/i);
  if (emergencyMatch) {
    const token = safeDecodeToken(emergencyMatch[1]);
    if (token) {
      return {
        kind: 'emergency-summary',
        token,
        route: `/emergency-summary/${encodeURIComponent(token)}`,
        raw: trimmed
      };
    }
  }

  const trackingUrlMatch = trimmed.match(/(?:\/track\/|\/tracking-qr\/)([^/?#]+)/i);
  if (trackingUrlMatch) {
    const token = safeDecodeToken(trackingUrlMatch[1]);
    if (token) {
      return {
        kind: 'tracking-qr',
        token,
        raw: trimmed
      };
    }
  }

  const trackingDirectMatch = trimmed.match(/^(QROP-[A-Z0-9_-]+)$/i);
  if (trackingDirectMatch) {
    return {
      kind: 'tracking-qr',
      token: trackingDirectMatch[1].toUpperCase(),
      raw: trimmed
    };
  }

  const prescriptionMatch = trimmed.match(/^(REC-[A-Z0-9_-]+)$/i);
  if (prescriptionMatch) {
    return {
      kind: 'prescription-code',
      token: prescriptionMatch[1].toUpperCase(),
      raw: trimmed
    };
  }

  return {
    kind: 'raw-token',
    token: trimmed,
    raw: trimmed
  };
}

/**
 * Stores a scanned tracking token across route transitions to the operational dashboard.
 *
 * @param {string} token
 */
export function storePendingOperationalQr(token) {
  if (typeof sessionStorage === 'undefined' || !token) return;
  try {
    sessionStorage.setItem(PENDING_OPERATIONAL_QR_KEY, String(token).trim());
  } catch {
    // Ignore storage quota/privacy mode errors
  }
}

/**
 * Reads and clears any pending tracking token scheduled for operational lookup.
 *
 * @returns {string | null}
 */
export function consumePendingOperationalQr() {
  if (typeof sessionStorage === 'undefined') return null;
  try {
    const value = sessionStorage.getItem(PENDING_OPERATIONAL_QR_KEY);
    if (value) {
      sessionStorage.removeItem(PENDING_OPERATIONAL_QR_KEY);
      return value.trim();
    }
  } catch {
    // Ignore storage errors
  }
  return null;
}
