/**
 * MediTriaje 2.0 — Generador y Gestión de Código QR de Emergencia (patient-emergency-qr.js)
 * Permite al paciente generar un token temporal criptográfico (15 min, max 3 lecturas),
 * con PIN opcional y selección de alcance clínico (ADR-010, §5.17, §5.18).
 */

import { api } from '../api.js';
import { motion } from '../motion.js';
import { ui, esc } from '../ui.js';

let activeCountdownInterval = null;
let activeHashChangeCleanup = null;

/**
 * Limpia el intervalo de cuenta regresiva y el listener de navegación asociado.
 */
function clearActiveCountdown() {
  if (activeCountdownInterval) {
    clearInterval(activeCountdownInterval);
    activeCountdownInterval = null;
  }
  if (activeHashChangeCleanup) {
    window.removeEventListener('hashchange', activeHashChangeCleanup);
    activeHashChangeCleanup = null;
  }
}

/**
 * Formatea una fecha ISO a hora y fecha de Bogotá
 * @param {string} isoString
 * @param {boolean} includeTime
 * @returns {string}
 */
function formatDate(isoString, includeTime = true) {
  if (!isoString) return '—';
  try {
    const d = new Date(isoString);
    const options = {
      timeZone: 'America/Bogota',
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    };
    if (includeTime) {
      options.hour = '2-digit';
      options.minute = '2-digit';
      options.second = '2-digit';
      options.hour12 = true;
    }
    return new Intl.DateTimeFormat('es-CO', options).format(d);
  } catch {
    return String(isoString);
  }
}

/**
 * Vista principal de Código QR de Emergencia para el Paciente
 * @param {HTMLElement} container
 */
export async function patientEmergencyQrView(container) {
  clearActiveCountdown();

  container.innerHTML = `
    <div style="max-width: var(--container); margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      <nav class="breadcrumb mb-3" aria-label="Ruta de navegación">
        <a href="#/patient/dashboard" class="flex items-center gap-1">
          ${ui.icon('home', 'icon icon--sm')}
          <span>Inicio</span>
        </a>
        <span class="breadcrumb-separator" aria-hidden="true">&gt;</span>
        <span class="breadcrumb-current" aria-current="page">QR de Emergencia</span>
      </nav>

      <!-- Navegación y encabezado -->
      <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
        <div class="min-w-0">
          <div class="flex items-center gap-2 mb-1">
            <a href="#/patient/dashboard" class="btn btn-ghost btn--sm btn--icon-only" aria-label="Volver al panel del paciente" title="Volver al panel">
              ${ui.icon('arrow-left')}
            </a>
            <h1 class="text-2xl font-bold m-0 flex items-center gap-2">
              <span class="shrink-0" style="color: var(--danger); display: inline-flex;">${ui.icon('shield', 'icon icon--md')}</span>
              <span>Código QR y Resumen de Emergencia</span>
            </h1>
          </div>
          <p class="text-muted text-sm m-0" style="max-width: 70ch;">
            Genera un acceso temporal seguro para paramédicos y personal de urgencias en caso de emergencia médica o traslado.
          </p>
        </div>
      </div>

      <!-- Alerta informativa de seguridad (ADR-010) -->
      <div class="card mb-6" style="border-left: 4px solid var(--primary); background-color: var(--surface);">
        <div class="card-body flex gap-4 items-start">
          <div class="shrink-0" style="color: var(--primary); padding-top: 2px;">
            ${ui.icon('shield', 'icon icon--md')}
          </div>
          <div class="text-sm min-w-0">
            <h2 class="font-semibold text-base mb-1" style="color: var(--text);">Privacidad y Seguridad de tu Resumen Clínico</h2>
            <ul style="margin: 0; padding-left: var(--space-4); color: var(--text-muted); line-height: 1.6;">
              <li><strong>Cero datos en el código:</strong> El código QR jamás contiene datos clínicos ni de identificación; únicamente transporta una URL web con un token cifrado de 256 bits.</li>
              <li><strong>Vigencia estricta:</strong> Cada código es válido durante exactamente <strong>15 minutos</strong> desde su generación.</li>
              <li><strong>Límite de lecturas:</strong> Permite un máximo de <strong>3 consultas</strong>; tras la tercera lectura o al vencer el tiempo, queda completamente inactivo.</li>
              <li><strong>PIN opcional:</strong> Puedes asignar un PIN de 4 dígitos para impedir lecturas no autorizadas en caso de escaneos accidentales.</li>
            </ul>
          </div>
        </div>
      </div>

      <!-- Contenedor del QR Activo (se oculta si no hay ninguno recién generado o activo) -->
      <div id="activeQrSection" class="mb-8" style="display: none;"></div>

      <div class="grid grid-cols-1 grid-cols-2-lg gap-6 mb-8">
        <!-- Formulario Generador -->
        <div class="card qr-feature-card">
          <div class="card-header">
            <h2 class="card-title text-xl flex items-center gap-2">
              ${ui.icon('plus', 'icon icon--sm text-primary')}
              <span>Generar Nuevo Código QR</span>
            </h2>
            <p class="card-subtitle text-sm text-muted">Configura el alcance de datos clínicos que deseas compartir</p>
          </div>
          <div class="card-body">
            <form id="formGenerarQr" novalidate>
              <fieldset style="border: none; padding: 0; margin: 0 0 var(--space-4) 0;">
                <legend class="font-semibold text-sm mb-2" style="color: var(--text);">Alcance de información clínica incluida:</legend>
                
                <div class="flex flex-col">
                  <label class="form-check">
                    <input type="checkbox" id="chkAlergias" name="incluirAlergias" checked />
                    <div class="form-check-label">
                      <span class="font-medium text-sm block">Alergias e hipersensibilidades</span>
                      <p class="text-xs text-muted m-0">Sustancias registradas, severidad y tipo de reacción adversa.</p>
                    </div>
                  </label>

                  <label class="form-check">
                    <input type="checkbox" id="chkMedicamentos" name="incluirMedicamentos" checked />
                    <div class="form-check-label">
                      <span class="font-medium text-sm block">Medicamentos activos prescritos</span>
                      <p class="text-xs text-muted m-0">Prescripciones médicas con vigencia activa en tus recetas.</p>
                    </div>
                  </label>

                  <label class="form-check">
                    <input type="checkbox" id="chkAtenciones" name="incluirAtenciones" checked />
                    <div class="form-check-label">
                      <span class="font-medium text-sm block">Atenciones recientes y diagnósticos CIE-10</span>
                      <p class="text-xs text-muted m-0">Últimas 5 consultas clínicas con diagnósticos y especialidad.</p>
                    </div>
                  </label>

                  <label class="form-check mb-0">
                    <input type="checkbox" id="chkContacto" name="incluirContacto" checked />
                    <div class="form-check-label">
                      <span class="font-medium text-sm block">Datos de contacto de emergencia</span>
                      <p class="text-xs text-muted m-0">Teléfono y correo electrónico para comunicación inmediata.</p>
                    </div>
                  </label>
                </div>

                <div id="qrScopeWarning" class="alert alert--warning mt-3 mb-0 text-xs" role="alert" style="display: none;">
                  ${ui.icon('alert-triangle', 'icon icon--sm alert-icon')}
                  <div class="alert-content">
                    <strong>Alcance vacío:</strong> Debes seleccionar al menos una categoría de información clínica para generar el código QR.
                  </div>
                </div>
              </fieldset>

              <!-- PIN opcional de 4 dígitos -->
              <div class="mb-6">
                <label for="qrPinInput" class="form-label font-semibold text-sm mb-1 block">
                  PIN numérico de seguridad de 4 dígitos (opcional):
                </label>
                <div style="max-width: 200px;">
                  <input
                    type="password"
                    id="qrPinInput"
                    name="pin"
                    class="form-input text-center font-mono text-lg tracking-widest"
                    maxlength="4"
                    pattern="[0-9]{4}"
                    placeholder="••••"
                    inputmode="numeric"
                    autocomplete="off"
                  />
                </div>
                <p class="text-xs text-muted mt-1 m-0">
                  Si defines un PIN, los paramédicos deberán digitarlo al escanear. Déjalo en blanco si prefieres acceso inmediato sin clave (ideal ante riesgo de pérdida de conciencia).
                </p>
              </div>

              <button type="submit" id="btnSubmitGenerar" class="btn btn-primary btn--lg w-full">
                ${ui.icon('shield', 'icon icon--md')}
                <span>Generar Código QR de Emergencia</span>
              </button>
            </form>
          </div>
        </div>

        <!-- Instrucciones y Recomendaciones -->
        <div class="card qr-feature-card" style="background-color: var(--surface-2);">
          <div class="card-header">
            <h2 class="card-title text-xl flex items-center gap-2">
              ${ui.icon('info', 'icon icon--sm text-primary')}
              <span>¿Cómo utilizar este código?</span>
            </h2>
          </div>
          <div class="card-body text-sm flex flex-col gap-4 text-muted">
            <div class="flex items-start gap-3">
              <span class="badge badge--scheduled font-bold shrink-0" style="border-radius: var(--radius-full); width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; padding: 0;">1</span>
              <div class="min-w-0">
                <strong style="color: var(--text);">Genera el código antes de salir o trasladarte:</strong>
                <p style="margin: 2px 0 0 0;">Configura el alcance y presiona generar. Tendrás 15 minutos de disponibilidad activa.</p>
              </div>
            </div>

            <div class="flex items-start gap-3">
              <span class="badge badge--scheduled font-bold shrink-0" style="border-radius: var(--radius-full); width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; padding: 0;">2</span>
              <div class="min-w-0">
                <strong style="color: var(--text);">Muéstralo en la pantalla de tu móvil o imprímelo:</strong>
                <p style="margin: 2px 0 0 0;">Cualquier cámara de smartphone o lector de ambulancia podrá escanearlo directamente sin instalar ninguna app.</p>
              </div>
            </div>

            <div class="flex items-start gap-3">
              <span class="badge badge--scheduled font-bold shrink-0" style="border-radius: var(--radius-full); width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; padding: 0;">3</span>
              <div class="min-w-0">
                <strong style="color: var(--text);">Auditoría inmutable de cada consulta:</strong>
                <p style="margin: 2px 0 0 0;">Cada vez que un médico o paramédico abre tu resumen, el sistema descuenta una lectura y registra la hora e IP de origen para tu tranquilidad.</p>
              </div>
            </div>

            <div class="flex items-start gap-3">
              <span class="badge badge--danger font-bold shrink-0" style="border-radius: var(--radius-full); width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; padding: 0;">4</span>
              <div class="min-w-0">
                <strong style="color: var(--text);">Revocación instantánea:</strong>
                <p style="margin: 2px 0 0 0;">Si la emergencia fue superada o el código ya no se requiere, puedes revocarlo con un solo clic y quedará invalidado de inmediato.</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Tabla Historial de Accesos Generados -->
      <div class="card">
        <div class="card-header flex flex-wrap items-center justify-between gap-2">
          <h2 class="card-title text-xl flex items-center gap-2">
            ${ui.icon('clock', 'icon icon--sm text-primary')}
            <span>Historial de Códigos QR Generados</span>
          </h2>
          <button type="button" id="btnRecargarHistorial" class="btn btn-ghost btn--sm" title="Refrescar lista">
            ${ui.icon('refresh-cw', 'icon icon--sm')}
            <span>Actualizar</span>
          </button>
        </div>
        <div class="card-body" id="historialQrContainer"></div>
      </div>
    </div>
  `;

  motion.staggerCards('.qr-feature-card', { root: container });

  // Cargar el historial de tokens
  await cargarHistorial(container);

  const form = container.querySelector('#formGenerarQr');
  const scopeWarning = container.querySelector('#qrScopeWarning');
  const scopeCheckboxes = [
    form?.chkAlergias,
    form?.chkMedicamentos,
    form?.chkAtenciones,
    form?.chkContacto
  ].filter(Boolean);

  const syncScopeWarning = () => {
    const anyChecked = scopeCheckboxes.some(chk => chk.checked);
    if (scopeWarning) {
      scopeWarning.style.display = anyChecked ? 'none' : 'flex';
    }
    return anyChecked;
  };

  scopeCheckboxes.forEach(chk => {
    chk.addEventListener('change', syncScopeWarning);
  });

  // Manejar el envío del formulario
  form?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const btnSubmit = form.querySelector('#btnSubmitGenerar');

    if (!syncScopeWarning()) {
      ui.showToast('Debes seleccionar al menos un alcance clínico para generar el código QR.', 'warning');
      if (scopeWarning) motion.pulseSubtle(scopeWarning);
      form.chkAlergias?.focus();
      return;
    }

    const pinVal = form.qrPinInput.value.trim();
    if (pinVal && !/^[0-9]{4}$/.test(pinVal)) {
      ui.showToast('El PIN debe contener exactamente 4 dígitos numéricos.', 'danger');
      form.qrPinInput.focus();
      return;
    }

    const payload = {
      pin: pinVal || null,
      incluirAlergias: form.chkAlergias.checked,
      incluirMedicamentos: form.chkMedicamentos.checked,
      incluirAtenciones: form.chkAtenciones.checked,
      incluirContacto: form.chkContacto.checked
    };

    try {
      ui.setButtonLoading(btnSubmit, true);

      const response = await api.post('/patients/me/emergency-qr', payload);
      ui.showToast('Código QR de emergencia generado exitosamente.', 'success');

      // Limpiar input PIN por seguridad
      form.qrPinInput.value = '';

      // Renderizar el QR Activo
      renderQrActivo(container, response);

      // Recargar el historial
      await cargarHistorial(container);

      // Scroll suave a la sección del QR
      document.getElementById('activeQrSection')?.scrollIntoView({ behavior: 'smooth' });
    } catch (err) {
      ui.showToast(esc(err.message || 'Error al generar código QR de emergencia.'), 'danger');
    } finally {
      ui.setButtonLoading(btnSubmit, false);
    }
  });

  // Botón recargar historial
  const btnRecargar = container.querySelector('#btnRecargarHistorial');
  btnRecargar?.addEventListener('click', () => {
    motion.spinOnce(btnRecargar);
    cargarHistorial(container);
  });
}

/**
 * Renderiza el código QR activo recién generado con cuenta regresiva y botón de revocación
 * @param {HTMLElement} rootContainer
 * @param {Object} qrData
 */
function renderQrActivo(rootContainer, qrData) {
  const activeSection = rootContainer.querySelector('#activeQrSection');
  if (!activeSection) return;

  clearActiveCountdown();

  activeSection.style.display = 'block';

  // Construir URL completa accesible desde el navegador
  const fullUrl = qrData.qrUrl?.startsWith('http')
    ? qrData.qrUrl
    : `${window.location.origin}${window.location.pathname}#/emergency-summary/${qrData.token}`;

  const pinNotice = qrData.requierePin
    ? `<span class="badge badge--scheduled">Protegido con PIN de 4 dígitos</span>`
    : `<span class="badge badge--confirmed">Acceso directo (sin PIN)</span>`;

  const scopeLabels = [];
  if (qrData.incluirAlergias) scopeLabels.push('Alergias');
  if (qrData.incluirMedicamentos) scopeLabels.push('Medicamentos');
  if (qrData.incluirAtenciones) scopeLabels.push('Atenciones');
  if (qrData.incluirContacto) scopeLabels.push('Contacto');

  activeSection.innerHTML = `
    <div class="card" style="border: 2px solid var(--primary); background: linear-gradient(180deg, var(--surface) 0%, var(--surface-2) 100%);">
      <div class="card-header flex flex-wrap items-center justify-between gap-4">
        <div class="flex items-center gap-3 min-w-0">
          <div class="shrink-0" style="background-color: var(--primary-soft); border: 1px solid var(--primary-soft-border); color: var(--primary); padding: 8px; border-radius: var(--radius-md); display: flex;">
            ${ui.icon('shield', 'icon icon--md')}
          </div>
          <div class="min-w-0">
            <h3 class="card-title text-xl font-bold">Código QR de Emergencia Activo</h3>
            <p class="card-subtitle text-xs text-muted">Válido para paramédicos y personal de urgencias</p>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          ${pinNotice}
          <div id="countdownBadge" class="badge badge--danger text-sm font-bold flex items-center gap-1">
            ${ui.icon('clock', 'icon icon--sm')}
            <span id="countdownText">15:00 restante(s)</span>
          </div>
        </div>
      </div>

      <div class="card-body">
        <div class="grid grid-cols-1 grid-cols-2-md gap-8 items-center">
          <!-- Contenedor del QR Visual (siempre fondo #FFFFFF para máxima legibilidad en cámara) -->
          <div class="flex flex-col items-center justify-center p-4 rounded-lg shadow-sm" style="background-color: #FFFFFF; color: #0F172A; border: 1px solid var(--border); max-width: 280px; margin: 0 auto; width: 100%;">
            <div id="qrCanvasTarget" style="display: flex; justify-content: center; align-items: center; width: 220px; height: 220px; background-color: #FFFFFF;"></div>
            <p class="text-xs text-center mt-3 mb-0 font-mono font-medium" style="color: #0F172A;">
              Escanear con cámara de smartphone
            </p>
          </div>

          <!-- Detalles del acceso y acciones -->
          <div class="flex flex-col gap-4 min-w-0">
            <div>
              <label for="directUrlInput" class="text-xs font-semibold text-muted uppercase tracking-wider block mb-1">Dirección Web de Emergencia:</label>
              <div class="flex flex-wrap items-center gap-2">
                <input
                  type="text"
                  id="directUrlInput"
                  class="form-input text-xs font-mono flex-1 min-w-0"
                  readonly
                  value="${esc(fullUrl)}"
                  style="background-color: var(--surface);"
                />
                <button type="button" id="btnCopyUrl" class="btn btn-secondary btn--sm shrink-0" title="Copiar enlace">
                  <span data-copy-icon style="display: inline-flex;">${ui.icon('copy', 'icon icon--sm')}</span>
                  <span id="copyBtnText">Copiar</span>
                </button>
              </div>
            </div>

            <div class="text-sm text-muted">
              <div><strong>Lecturas disponibles:</strong> Máximo ${esc(qrData.maxAccesos)} consultas.</div>
              <div><strong>Expira a las:</strong> ${esc(formatDate(qrData.expiraAt))}</div>
              <div><strong>Alcance:</strong> ${esc(scopeLabels.join(' · ') || 'Básico')}</div>
            </div>

            <div class="pt-2 flex flex-wrap gap-3">
              <button type="button" id="btnDownloadQrPng" class="btn btn-primary btn--sm">
                ${ui.icon('download', 'icon icon--sm')}
                <span>Descargar QR</span>
              </button>
              <a href="${esc(fullUrl)}" target="_blank" rel="noopener" class="btn btn-secondary btn--sm">
                <span>Abrir vista previa del resumen</span>
              </a>
              <button type="button" id="btnRevocarActivo" class="btn btn-danger btn--sm" data-id="${esc(qrData.publicId)}">
                ${ui.icon('x', 'icon icon--sm')}
                <span>Revocar este código de inmediato</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `;

  motion.stepTransition(activeSection, 'forward');

  // Renderizar el QR con la librería local
  const canvasTarget = activeSection.querySelector('#qrCanvasTarget');
  if (canvasTarget && typeof window.QRCode !== 'undefined') {
    canvasTarget.innerHTML = '';
    try {
      new window.QRCode(canvasTarget, {
        text: fullUrl,
        width: 220,
        height: 220,
        colorDark: '#0f172a',
        colorLight: '#ffffff',
        correctLevel: window.QRCode.CorrectLevel.M
      });
    } catch (err) {
      console.error('Error renderizando QRCode:', err);
      canvasTarget.innerHTML = `<p class="text-xs text-danger text-center m-0">No fue posible renderizar el gráfico QR.</p>`;
    }
  } else if (canvasTarget) {
    canvasTarget.innerHTML = `<p class="text-xs text-danger text-center m-0">La librería gráfica de QR no está disponible en este momento.</p>`;
  }

  // Descargar gráfico QR como imagen PNG
  const btnDownloadQr = activeSection.querySelector('#btnDownloadQrPng');
  btnDownloadQr?.addEventListener('click', () => {
    const imgEl = canvasTarget?.querySelector('img');
    const canvasEl = canvasTarget?.querySelector('canvas');
    let dataUrl = '';
    if (canvasEl && typeof canvasEl.toDataURL === 'function') {
      dataUrl = canvasEl.toDataURL('image/png');
    } else if (imgEl?.src) {
      dataUrl = imgEl.src;
    }

    if (!dataUrl) {
      ui.showToast('El código QR aún no está listo para descargar.', 'warning');
      return;
    }

    const link = document.createElement('a');
    link.href = dataUrl;
    link.download = 'meditriaje-qr-emergencia.png';
    document.body.appendChild(link);
    link.click();
    link.remove();
  });

  // Copiar enlace al portapapeles
  const btnCopy = activeSection.querySelector('#btnCopyUrl');
  const copyBtnText = activeSection.querySelector('#copyBtnText');
  const copyIconWrap = activeSection.querySelector('[data-copy-icon]');
  let copyResetTimeout = null;

  btnCopy?.addEventListener('click', async () => {
    motion.pulseSubtle(btnCopy);
    let copied = false;
    try {
      await navigator.clipboard.writeText(fullUrl);
      copied = true;
    } catch {
      const urlInput = activeSection.querySelector('#directUrlInput');
      if (urlInput) {
        urlInput.select();
        copied = document.execCommand('copy');
      }
    }

    if (copied) {
      if (copyBtnText) copyBtnText.textContent = '¡Copiado!';
      if (copyIconWrap) copyIconWrap.innerHTML = ui.icon('check', 'icon icon--sm');
      ui.showToast('Enlace copiado al portapapeles.', 'info');
      if (copyResetTimeout) clearTimeout(copyResetTimeout);
      copyResetTimeout = setTimeout(() => {
        if (copyBtnText) copyBtnText.textContent = 'Copiar';
        if (copyIconWrap) copyIconWrap.innerHTML = ui.icon('copy', 'icon icon--sm');
      }, 2500);
    } else {
      ui.showToast('Selecciona y copia manualmente la URL.', 'info');
    }
  });

  // Temporizador regresivo dinámico con limpieza en hashchange
  const countdownText = activeSection.querySelector('#countdownText');
  const countdownBadge = activeSection.querySelector('#countdownBadge');
  const expiraMs = new Date(qrData.expiraAt).getTime();

  const updateCountdown = () => {
    const ahora = Date.now();
    const difSec = Math.max(0, Math.floor((expiraMs - ahora) / 1000));
    if (difSec <= 0) {
      if (countdownText) countdownText.textContent = 'EXPIRADO (00:00)';
      if (countdownBadge) {
        countdownBadge.className = 'badge badge--rescheduled text-sm font-bold flex items-center gap-1';
      }
      clearActiveCountdown();
      return;
    }
    const mins = Math.floor(difSec / 60);
    const secs = difSec % 60;
    const formatted = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
    if (countdownText) {
      countdownText.textContent = `${formatted} restante(s)`;
    }
  };

  updateCountdown();
  activeCountdownInterval = setInterval(updateCountdown, 1000);
  activeHashChangeCleanup = () => clearActiveCountdown();
  window.addEventListener('hashchange', activeHashChangeCleanup, { once: true });

  // Botón Revocar Código
  const btnRevocar = activeSection.querySelector('#btnRevocarActivo');
  btnRevocar?.addEventListener('click', () => {
    const publicId = btnRevocar.getAttribute('data-id');
    confirmarRevocacion(rootContainer, publicId);
  });
}

/**
 * Consulta y carga el historial de accesos QR del paciente
 * @param {HTMLElement} rootContainer
 */
async function cargarHistorial(rootContainer) {
  const container = rootContainer.querySelector('#historialQrContainer');
  if (!container) return;

  ui.renderSkeleton(container, 'table');

  try {
    const accesos = await api.get('/patients/me/emergency-qr');
    if (!accesos || accesos.length === 0) {
      ui.renderEmpty(container, {
        icon: 'qr-code',
        title: 'Sin códigos QR generados',
        description: 'Aún no has generado ningún código QR de emergencia. Configura un alcance arriba cuando necesites compartir tu resumen clínico.'
      });
      return;
    }

    container.innerHTML = `
      <div class="table-container" style="overflow-x: auto;">
        <table class="table table--stacked" style="width: 100%;">
          <thead>
            <tr>
              <th scope="col">Fecha de Creación</th>
              <th scope="col">Estado</th>
              <th scope="col">Lecturas</th>
              <th scope="col">PIN</th>
              <th scope="col">Alcance Autorizado</th>
              <th scope="col">Vencimiento</th>
              <th scope="col" style="text-align: right;">Acciones</th>
            </tr>
          </thead>
          <tbody>
            ${accesos.map(a => {
              let badgeClass = 'badge--confirmed';
              if (a.estado === 'EXPIRADO') badgeClass = 'badge--scheduled';
              if (a.estado === 'AGOTADO') badgeClass = 'badge--rescheduled';
              if (a.estado === 'REVOCADO') badgeClass = 'badge--danger';

              const alcanceTags = [];
              if (a.incluirAlergias) alcanceTags.push('Alergias');
              if (a.incluirMedicamentos) alcanceTags.push('Medicamentos');
              if (a.incluirAtenciones) alcanceTags.push('Atenciones');
              if (a.incluirContacto) alcanceTags.push('Contacto');

              const puedeRevocar = a.estado === 'ACTIVO';

              return `
                <tr>
                  <td data-label="Creación" class="font-medium text-xs">${esc(formatDate(a.createdAt))}</td>
                  <td data-label="Estado"><span class="badge ${badgeClass}">${esc(a.estado)}</span></td>
                  <td data-label="Lecturas" class="text-xs font-mono">${esc(a.accesosRealizados)} / ${esc(a.maxAccesos)}</td>
                  <td data-label="PIN" class="text-xs">${a.requierePin ? 'Sí (4 dígitos)' : 'No (Libre)'}</td>
                  <td data-label="Alcance" class="text-xs text-muted">${esc(alcanceTags.join(', ') || 'Básico')}</td>
                  <td data-label="Vencimiento" class="text-xs text-muted">${esc(formatDate(a.expiraAt))}</td>
                  <td data-label="Acciones" style="text-align: right;">
                    ${puedeRevocar ? `
                      <button type="button" class="btn btn-ghost btn--sm text-danger btnRevocarFila" data-id="${esc(a.publicId)}" title="Revocar acceso">
                        ${ui.icon('x', 'icon icon--sm')}
                        <span>Revocar</span>
                      </button>
                    ` : `<span class="text-xs text-muted">—</span>`}
                  </td>
                </tr>
              `;
            }).join('')}
          </tbody>
        </table>
      </div>
    `;

    // Asignar listeners a botones de revocación en la tabla
    container.querySelectorAll('.btnRevocarFila').forEach(btn => {
      btn.addEventListener('click', () => {
        const publicId = btn.getAttribute('data-id');
        confirmarRevocacion(rootContainer, publicId);
      });
    });
  } catch (err) {
    ui.renderError(container, {
      title: 'No se pudo cargar el historial de códigos QR',
      message: esc(err.message || 'Ocurrió un problema al consultar el historial.'),
      onRetry: () => cargarHistorial(rootContainer)
    });
  }
}

/**
 * Solicita confirmación antes de revocar un token temporal
 * @param {HTMLElement} rootContainer
 * @param {string} publicId
 */
function confirmarRevocacion(rootContainer, publicId) {
  ui.showModal({
    title: 'Revocar Código de Emergencia',
    message: '¿Estás seguro de que deseas revocar este código QR? Cualquier paramédico o médico que intente escanearlo recibirá un aviso de acceso inválido y no podrá consultar tus datos.',
    confirmText: 'Sí, revocar ahora',
    cancelText: 'Cancelar',
    isDanger: true,
    onConfirm: async () => {
      try {
        await api.patch(`/patients/me/emergency-qr/${encodeURIComponent(publicId)}/revoke`);
        ui.showToast('Acceso QR revocado exitosamente.', 'info');

        const activeSection = rootContainer.querySelector('#activeQrSection');
        if (activeSection) {
          activeSection.style.display = 'none';
        }
        clearActiveCountdown();

        await cargarHistorial(rootContainer);
        return true;
      } catch (err) {
        ui.showToast(esc(err.message || 'Error al revocar acceso QR.'), 'danger');
        return false;
      }
    }
  });
}
