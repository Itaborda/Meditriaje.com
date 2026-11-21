/**
 * MediTriaje 2.0 — Modal Reutilizable de Escaneo QR por Cámara o Imagen (qr-scanner-modal.js)
 * Utiliza BarcodeDetector nativo cuando está disponible y jsQR local (Apache-2.0) como fallback universal,
 * cumpliendo estrictamente con CSP (script-src 'self'; img-src 'self' data:).
 */

import { router } from '../router.js';
import { ui, esc } from '../ui.js';
import { parseQrPayload, storePendingOperationalQr } from '../qr-payload.js';

let activeCleanup = null;

async function resolveNativeQrDetector() {
  if (typeof window === 'undefined' || typeof window.BarcodeDetector !== 'function') {
    return null;
  }
  try {
    if (typeof window.BarcodeDetector.getSupportedFormats === 'function') {
      const formats = await window.BarcodeDetector.getSupportedFormats();
      if (!Array.isArray(formats) || !formats.includes('qr_code')) {
        return null;
      }
    }
    return new window.BarcodeDetector({ formats: ['qr_code'] });
  } catch {
    return null;
  }
}

function drawScaledToCanvas(source, width, height, maxEdge) {
  const scale = Math.min(1, maxEdge / Math.max(width, height));
  const targetW = Math.max(1, Math.round(width * scale));
  const targetH = Math.max(1, Math.round(height * scale));
  const canvas = document.createElement('canvas');
  canvas.width = targetW;
  canvas.height = targetH;
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  ctx.drawImage(source, 0, 0, targetW, targetH);
  return { canvas, ctx, width: targetW, height: targetH };
}

function loadImageViaDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = () => reject(new Error('No fue posible leer la imagen seleccionada.'));
      img.src = reader.result;
    };
    reader.onerror = () => reject(new Error('No fue posible cargar el archivo seleccionado.'));
    reader.readAsDataURL(file);
  });
}

async function decodeImageFile(file, nativeDetector) {
  let source = null;
  let width = 0;
  let height = 0;
  let isBitmap = false;

  if (typeof createImageBitmap === 'function') {
    try {
      source = await createImageBitmap(file);
      width = source.width;
      height = source.height;
      isBitmap = true;
    } catch {
      source = null;
    }
  }

  if (!source) {
    source = await loadImageViaDataUrl(file);
    width = source.naturalWidth || source.width;
    height = source.naturalHeight || source.height;
  }

  try {
    if (!width || !height) return null;

    if (nativeDetector) {
      try {
        const detections = await nativeDetector.detect(source);
        const match = detections?.find((d) => typeof d?.rawValue === 'string' && d.rawValue.trim());
        if (match) {
          return match.rawValue.trim();
        }
      } catch {
        // Fallback to jsQR below
      }
    }

    if (typeof window.jsQR === 'function') {
      const { ctx, width: w, height: h } = drawScaledToCanvas(source, width, height, 1200);
      const imageData = ctx.getImageData(0, 0, w, h);
      const result = window.jsQR(imageData.data, w, h, { inversionAttempts: 'attemptBoth' });
      if (result && typeof result.data === 'string' && result.data.trim()) {
        return result.data.trim();
      }
    }

    return null;
  } finally {
    if (isBitmap && typeof source.close === 'function') {
      source.close();
    }
  }
}

function describeCameraError(err) {
  const name = err?.name || '';
  if (name === 'NotAllowedError' || name === 'PermissionDeniedError') {
    return 'Permiso de cámara denegado. Habilita el acceso a la cámara en tu navegador o sube una imagen del código QR.';
  }
  if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
    return 'No se detectó ninguna cámara disponible en este dispositivo. Puedes subir una imagen del código QR.';
  }
  if (name === 'NotReadableError' || name === 'TrackStartError') {
    return 'La cámara está siendo utilizada por otra aplicación. Ciérrala e intenta nuevamente o sube una imagen del QR.';
  }
  return 'No fue posible iniciar la cámara. Intenta nuevamente o sube una imagen del código QR.';
}

/**
 * Abre un diálogo modal accesible para escanear códigos QR con la cámara del navegador
 * o cargando una imagen local.
 *
 * @param {Object} options
 * @param {string} [options.title]
 * @param {string} [options.subtitle]
 * @param {Function} options.onScan Callback que recibe el texto crudo decodificado
 * @returns {Function} Función para cerrar y liberar cámara manualmente
 */
export function openQrScannerModal({
  title = 'Escanear Código QR',
  subtitle = 'Apunta la cámara al código QR o selecciona una imagen desde tu dispositivo.',
  onScan
} = {}) {
  if (typeof activeCleanup === 'function') {
    activeCleanup();
  }

  const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;

  const backdrop = document.createElement('div');
  backdrop.id = 'qrScannerModal';
  backdrop.className = 'qr-scanner-backdrop is-open';
  backdrop.setAttribute('role', 'dialog');
  backdrop.setAttribute('aria-modal', 'true');
  backdrop.setAttribute('aria-labelledby', 'qrScannerModalTitle');

  backdrop.innerHTML = `
    <div class="qr-scanner-dialog">
      <header class="qr-scanner-header">
        <div class="flex items-center gap-3">
          <span class="qr-scanner-header-icon" aria-hidden="true">${ui.icon('camera', 'icon icon--md')}</span>
          <div>
            <h2 id="qrScannerModalTitle" class="qr-scanner-title">${esc(title)}</h2>
            <p class="qr-scanner-subtitle">${esc(subtitle)}</p>
          </div>
        </div>
        <button type="button" id="btnCloseQrScanner" class="btn btn-ghost btn--sm btn--icon-only" aria-label="Cerrar escáner de cámara" title="Cerrar">
          ${ui.icon('x', 'icon icon--sm')}
        </button>
      </header>

      <div class="qr-scanner-viewport">
        <video id="qrScannerVideo" class="qr-scanner-video" autoplay playsinline muted></video>
        <div class="qr-scanner-reticle" aria-hidden="true">
          <span class="qr-scanner-corner qr-scanner-corner--tl"></span>
          <span class="qr-scanner-corner qr-scanner-corner--tr"></span>
          <span class="qr-scanner-corner qr-scanner-corner--bl"></span>
          <span class="qr-scanner-corner qr-scanner-corner--br"></span>
          <span class="qr-scanner-scanline"></span>
        </div>
        <div id="qrScannerOverlayMsg" class="qr-scanner-overlay-msg">
          <p id="qrScannerOverlayText" class="text-sm m-0">Iniciando cámara...</p>
          <button type="button" id="btnRetryQrCamera" class="btn btn-primary btn--sm" style="display: none;">
            ${ui.icon('refresh', 'icon icon--sm')}
            <span>Reintentar cámara</span>
          </button>
        </div>
      </div>

      <p id="qrScannerStatus" class="qr-scanner-status" aria-live="polite">
        Iniciando cámara...
      </p>

      <footer class="qr-scanner-actions">
        <button type="button" id="btnSwitchQrCamera" class="btn btn-secondary btn--sm" style="display: none;">
          ${ui.icon('refresh-cw', 'icon icon--sm')}
          <span>Cambiar cámara</span>
        </button>
        <button type="button" id="btnUploadQrImage" class="btn btn-secondary btn--sm">
          ${ui.icon('upload', 'icon icon--sm')}
          <span>Subir imagen QR</span>
        </button>
        <input type="file" id="inputQrImageFile" accept="image/*" class="sr-only" aria-label="Seleccionar imagen con código QR">
        <button type="button" id="btnCancelQrScanner" class="btn btn-ghost btn--sm" style="margin-left: auto;">
          <span>Cancelar</span>
        </button>
      </footer>
    </div>
  `;

  document.body.appendChild(backdrop);

  const videoEl = backdrop.querySelector('#qrScannerVideo');
  const overlayEl = backdrop.querySelector('#qrScannerOverlayMsg');
  const overlayTextEl = backdrop.querySelector('#qrScannerOverlayText');
  const retryBtn = backdrop.querySelector('#btnRetryQrCamera');
  const statusEl = backdrop.querySelector('#qrScannerStatus');
  const switchBtn = backdrop.querySelector('#btnSwitchQrCamera');
  const uploadBtn = backdrop.querySelector('#btnUploadQrImage');
  const fileInput = backdrop.querySelector('#inputQrImageFile');
  const closeBtn = backdrop.querySelector('#btnCloseQrScanner');
  const cancelBtn = backdrop.querySelector('#btnCancelQrScanner');

  let isActive = true;
  let stream = null;
  let scanTimerId = null;
  let nativeDetector = null;
  let videoDevices = [];
  let currentDeviceIdx = 0;

  const stopStream = () => {
    if (scanTimerId !== null) {
      clearTimeout(scanTimerId);
      scanTimerId = null;
    }
    if (stream) {
      stream.getTracks().forEach((track) => {
        try {
          track.stop();
        } catch {
          // Ignore track stop errors
        }
      });
      stream = null;
    }
    if (videoEl) {
      videoEl.srcObject = null;
    }
  };

  const cleanup = () => {
    if (!isActive) return;
    isActive = false;
    stopStream();
    document.removeEventListener('keydown', handleKeyDown);
    window.removeEventListener('hashchange', cleanup);
    backdrop.remove();
    if (activeCleanup === cleanup) {
      activeCleanup = null;
    }
    if (previouslyFocused && typeof previouslyFocused.focus === 'function' && previouslyFocused.isConnected) {
      previouslyFocused.focus();
    }
  };

  activeCleanup = cleanup;

  const handleKeyDown = (e) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      cleanup();
    }
  };

  const handleDecoded = (decodedText) => {
    const clean = typeof decodedText === 'string' ? decodedText.trim() : '';
    if (!clean || !isActive) return;
    cleanup();
    if (typeof onScan === 'function') {
      onScan(clean);
    }
  };

  const showOverlay = (message, showRetry = false) => {
    if (!isActive) return;
    overlayTextEl.textContent = message;
    retryBtn.style.display = showRetry ? 'inline-flex' : 'none';
    overlayEl.style.display = 'flex';
  };

  const hideOverlay = () => {
    if (!isActive) return;
    overlayEl.style.display = 'none';
  };

  const decodeVideoFrame = async () => {
    if (!isActive || !videoEl || videoEl.readyState < 2 || !videoEl.videoWidth || !videoEl.videoHeight) {
      return null;
    }

    if (nativeDetector) {
      try {
        const detections = await nativeDetector.detect(videoEl);
        const hit = detections?.find((d) => typeof d?.rawValue === 'string' && d.rawValue.trim());
        if (hit) {
          return hit.rawValue.trim();
        }
      } catch {
        // Fallback cleanly to jsQR
      }
    }

    if (typeof window.jsQR === 'function') {
      const { ctx, width, height } = drawScaledToCanvas(videoEl, videoEl.videoWidth, videoEl.videoHeight, 640);
      const imgData = ctx.getImageData(0, 0, width, height);
      const code = window.jsQR(imgData.data, width, height, { inversionAttempts: 'dontInvert' });
      if (code && typeof code.data === 'string' && code.data.trim()) {
        return code.data.trim();
      }
    }

    return null;
  };

  const scheduleNextScan = () => {
    if (!isActive) return;
    scanTimerId = setTimeout(async () => {
      if (!isActive) return;
      try {
        const decoded = await decodeVideoFrame();
        if (decoded) {
          handleDecoded(decoded);
          return;
        }
      } catch {
        // Continue scanning next frame
      }
      scheduleNextScan();
    }, 120);
  };

  const startCamera = async (deviceId = null) => {
    if (!isActive) return;
    stopStream();

    if (!window.isSecureContext) {
      const msg = 'La cámara requiere conexión segura HTTPS (o localhost). Usa "Subir imagen QR" para leer una foto o captura del código.';
      showOverlay(msg, false);
      statusEl.textContent = msg;
      return;
    }

    if (!navigator.mediaDevices || typeof navigator.mediaDevices.getUserMedia !== 'function') {
      const msg = 'Este navegador no permite acceso directo a cámara. Usa "Subir imagen QR" para cargar el código.';
      showOverlay(msg, false);
      statusEl.textContent = msg;
      return;
    }

    showOverlay('Solicitando acceso a la cámara...', false);
    statusEl.textContent = 'Solicitando permiso de cámara...';

    const preferredConstraints = deviceId
      ? { video: { deviceId: { exact: deviceId }, width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false }
      : { video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false };

    try {
      try {
        stream = await navigator.mediaDevices.getUserMedia(preferredConstraints);
      } catch (constraintErr) {
        if (constraintErr?.name === 'OverconstrainedError') {
          stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
        } else {
          throw constraintErr;
        }
      }

      if (!isActive) {
        stopStream();
        return;
      }

      videoEl.srcObject = stream;
      await videoEl.play().catch(() => {});

      if (!nativeDetector) {
        nativeDetector = await resolveNativeQrDetector();
      }

      if (typeof navigator.mediaDevices.enumerateDevices === 'function') {
        try {
          const allDevices = await navigator.mediaDevices.enumerateDevices();
          videoDevices = allDevices.filter((d) => d.kind === 'videoinput');
          if (videoDevices.length > 1) {
            const activeTrack = stream.getVideoTracks()[0];
            const activeDeviceId = activeTrack?.getSettings?.().deviceId;
            const foundIdx = videoDevices.findIndex((d) => d.deviceId === activeDeviceId);
            if (foundIdx >= 0) currentDeviceIdx = foundIdx;
            switchBtn.style.display = 'inline-flex';
          } else {
            switchBtn.style.display = 'none';
          }
        } catch {
          switchBtn.style.display = 'none';
        }
      }

      hideOverlay();
      statusEl.textContent = 'Cámara activa. Centra el código QR dentro del recuadro.';
      scheduleNextScan();
    } catch (err) {
      const msg = describeCameraError(err);
      showOverlay(msg, true);
      statusEl.textContent = msg;
    }
  };

  closeBtn.addEventListener('click', cleanup);
  cancelBtn.addEventListener('click', cleanup);
  backdrop.addEventListener('click', (e) => {
    if (e.target === backdrop) {
      cleanup();
    }
  });
  retryBtn.addEventListener('click', () => startCamera(videoDevices[currentDeviceIdx]?.deviceId || null));
  switchBtn.addEventListener('click', () => {
    if (videoDevices.length < 2) return;
    currentDeviceIdx = (currentDeviceIdx + 1) % videoDevices.length;
    startCamera(videoDevices[currentDeviceIdx].deviceId);
  });

  uploadBtn.addEventListener('click', () => {
    fileInput.click();
  });

  fileInput.addEventListener('change', async () => {
    const file = fileInput.files?.[0];
    fileInput.value = '';
    if (!file || !isActive) return;

    statusEl.textContent = 'Analizando imagen en busca de código QR...';
    try {
      if (!nativeDetector) {
        nativeDetector = await resolveNativeQrDetector();
      }
      const decoded = await decodeImageFile(file, nativeDetector);
      if (decoded) {
        handleDecoded(decoded);
        return;
      }
      statusEl.textContent = 'No se detectó ningún código QR en la imagen seleccionada.';
      ui.showToast('No se detectó ningún código QR en la imagen seleccionada.', 'warning');
    } catch (err) {
      const msg = err?.message || 'Error al procesar la imagen seleccionada.';
      statusEl.textContent = msg;
      ui.showToast(msg, 'danger');
    }
  });

  document.addEventListener('keydown', handleKeyDown);
  window.addEventListener('hashchange', cleanup);

  closeBtn.focus();
  startCamera();

  return cleanup;
}

/**
 * Abre el escáner universal capaz de interpretar QR de emergencia del paciente (#/emergency-summary/...),
 * manillas hospitalarias (QROP-...) o códigos de receta, ejecutando la navegación o acción correspondiente.
 *
 * @param {Object} [options]
 * @param {string} [options.title]
 * @param {string} [options.subtitle]
 * @param {Function} [options.onParsed] Si retorna true, el llamador maneja el resultado directamente
 */
export function openUniversalQrScanner({
  title = 'Escanear Código QR',
  subtitle = 'Escanea una manilla hospitalaria (QROP), un carnet QR de emergencia o sube una imagen.',
  onParsed = null
} = {}) {
  return openQrScannerModal({
    title,
    subtitle,
    onScan: (rawText) => {
      const parsed = parseQrPayload(rawText);
      if (!parsed) {
        ui.showToast('El código QR escaneado está vacío o no es válido.', 'warning');
        return;
      }

      if (typeof onParsed === 'function' && onParsed(parsed) === true) {
        return;
      }

      if (parsed.kind === 'emergency-summary') {
        ui.showToast('Código QR de emergencia detectado. Abriendo resumen clínico...', 'success', 2500);
        router.navigate(parsed.route);
        return;
      }

      if (parsed.kind === 'tracking-qr' || parsed.kind === 'raw-token') {
        storePendingOperationalQr(parsed.token);
        ui.showToast('Código QR de seguimiento detectado. Consultando ubicación...', 'success', 2500);
        router.navigate('/operational/dashboard');
        return;
      }

      if (parsed.kind === 'prescription-code') {
        ui.showToast(`Código de receta detectado: ${parsed.token}`, 'info', 3500);
      }
    }
  });
}
