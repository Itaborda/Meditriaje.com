/**
 * MediTriaje 2.0 — Importación Masiva de Afiliados EPS (affiliation-import.js)
 * Carga segura de XLSX, previsualización en staging y commit atómico/parcial (A02, A03, A04).
 */

import { api, affiliationApi } from '../api.js';
import { motion } from '../motion.js';
import { ui, esc } from '../ui.js';

const MAX_EXCEL_BYTES = 10 * 1024 * 1024;

function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

export async function renderAffiliationImport(container) {
  container.innerHTML = `
    <div style="max-width: var(--container); margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      <div class="view-header">
        <nav class="breadcrumb" aria-label="Ruta de navegación">
          <a href="#/admin/dashboard">Administración</a>
          <span class="breadcrumb-separator" aria-hidden="true">&gt;</span>
          <span>Aseguramiento EPS</span>
          <span class="breadcrumb-separator" aria-hidden="true">&gt;</span>
          <span class="breadcrumb-current" aria-current="page">Importación Masiva</span>
        </nav>
        <h1 class="view-title">Importación Masiva de Afiliados EPS</h1>
        <p class="view-subtitle">Cargue archivos XLSX oficiales de aseguradoras para actualizar el censo de afiliados en Valledupar.</p>
      </div>

      <!-- Tarjeta de Descarga de Plantilla Oficial -->
      <div class="card" style="margin-top: 1rem; background: var(--surface-2); border-left: 4px solid var(--primary); padding: 1.25rem;">
        <div style="display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 1rem;">
          <div>
            <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.25rem;">
              ${ui.icon('download', 'icon icon--md text-primary')}
              <h2 style="font-size: 1.1rem; font-weight: 700; margin: 0; color: var(--text);">Plantilla Oficial de Afiliados (.xlsx)</h2>
            </div>
            <p style="margin: 0; font-size: 0.875rem; color: var(--text-muted);">
              Descargue el formato estándar preconfigurado con columnas requeridas (TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, REGIMEN, TIPO_AFILIADO), ejemplos reales y guía técnica MinSalud.
            </p>
          </div>
          <button type="button" id="btn-descargar-plantilla" class="btn btn-secondary" style="font-weight: 600; white-space: nowrap;" aria-label="Descargar plantilla oficial de afiliados en Excel">
            ${ui.icon('download', 'icon icon--sm')}
            <span>Descargar Plantilla Excel</span>
          </button>
        </div>
      </div>

      <div class="grid grid-cols-1 grid-cols-2-md gap-6 mt-4">
        <!-- Tarjeta de Carga -->
        <div class="card">
          <h2 style="font-size: 1.25rem; font-weight: 600; margin-bottom: 1rem;">1. Selección y Carga de Archivo</h2>
          <form id="form-upload-excel" class="form-group m-0" style="display: flex; flex-direction: column; gap: 1rem;">
            <div>
              <label for="select-eps" class="label-required">Entidad Promotora de Salud (EPS)</label>
              <select id="select-eps" class="form-select" required aria-label="Seleccionar Entidad Promotora de Salud">
                <option value="">-- Seleccione una EPS --</option>
              </select>
            </div>

            <div>
              <label for="file-excel" class="label-required">Archivo Excel (.xlsx, máx. 10 MB)</label>
              <div id="excel-dropzone" class="dropzone">
                <div class="dropzone-icon" aria-hidden="true">
                  ${ui.icon('upload', 'icon icon--md')}
                </div>
                <div class="text-sm font-semibold">Arrastra tu archivo .xlsx aquí o selecciónalo</div>
                <input type="file" id="file-excel" class="form-control" accept=".xlsx" required aria-label="Cargar archivo Excel de afiliados" />
                <small id="file-excel-meta" class="text-xs text-muted">
                  Columnas requeridas: TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, REGIMEN, TIPO_AFILIADO.
                </small>
              </div>
            </div>

            <button type="submit" id="btn-previsualizar" class="btn btn-primary" style="margin-top: 0.5rem;" aria-label="Analizar y previsualizar archivo de afiliados">
              ${ui.icon('search', 'icon icon--sm')}
              <span>Analizar y Previsualizar Archivo</span>
            </button>
          </form>
        </div>

        <!-- Tarjeta de Métricas de Validación -->
        <div class="card" id="card-metricas" style="display: none;">
          <h2 style="font-size: 1.25rem; font-weight: 600; margin-bottom: 1rem;">2. Resultados del Pre-análisis</h2>
          <div class="grid grid-cols-1 grid-cols-3-md gap-3 text-center mb-6">
            <div style="background: var(--surface-2); padding: 1rem; border-radius: var(--radius-md); border: 1px solid var(--border);">
              <div style="font-size: 0.85rem; color: var(--text-muted);">Total Filas</div>
              <div id="stat-total" class="tabular-nums" style="font-size: 1.75rem; font-weight: 700;">0</div>
            </div>
            <div style="background: var(--success-bg); color: var(--on-success-bg); padding: 1rem; border-radius: var(--radius-md);">
              <div style="font-size: 0.85rem;">Válidas</div>
              <div id="stat-validas" class="tabular-nums" style="font-size: 1.75rem; font-weight: 700;">0</div>
            </div>
            <div style="background: var(--danger-bg); color: var(--on-danger-bg); padding: 1rem; border-radius: var(--radius-md);">
              <div style="font-size: 0.85rem;">Con Errores</div>
              <div id="stat-fallidas" class="tabular-nums" style="font-size: 1.75rem; font-weight: 700;">0</div>
            </div>
          </div>

          <div style="border-top: 1px solid var(--border); padding-top: 1rem;">
            <label for="select-modo-commit" style="font-weight: 600; display: block; margin-bottom: 0.5rem;">Modo de Aplicación:</label>
            <select id="select-modo-commit" class="form-select" style="margin-bottom: 1rem;" aria-label="Modo de aplicación del lote">
              <option value="VALID_ROWS">Importar solo filas válidas (ignorar filas con error)</option>
              <option value="ATOMIC_ALL">Atómico estricto (rechazar todo si hay un solo error)</option>
            </select>
            <button type="button" id="btn-confirmar-commit" class="btn btn-success" style="width: 100%;" aria-label="Confirmar y aplicar importación definitiva">
              ${ui.icon('check', 'icon icon--sm')}
              <span>Confirmar y Aplicar Importación Definitiva</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Muestra de Filas Analizadas -->
      <div class="card" id="card-muestra" style="margin-top: 1.5rem; display: none;">
        <h2 style="font-size: 1.25rem; font-weight: 600; margin-bottom: 1rem;">Muestra de Registros Analizados (Staging)</h2>
        <div class="table-container">
          <table class="table table--stacked" style="width: 100%; text-align: left;">
            <thead>
              <tr>
                <th scope="col">Fila</th>
                <th scope="col">Documento</th>
                <th scope="col">Nombres y Apellidos</th>
                <th scope="col">Régimen</th>
                <th scope="col">Tipo Afiliado</th>
                <th scope="col">Estado</th>
                <th scope="col">Observación / Error</th>
              </tr>
            </thead>
            <tbody id="tbody-muestra"></tbody>
          </table>
        </div>
      </div>
    </div>
  `;

  const selectEps = container.querySelector('#select-eps');
  const formUpload = container.querySelector('#form-upload-excel');
  const fileInput = container.querySelector('#file-excel');
  const dropzone = container.querySelector('#excel-dropzone');
  const fileMeta = container.querySelector('#file-excel-meta');
  const cardMetricas = container.querySelector('#card-metricas');
  const cardMuestra = container.querySelector('#card-muestra');
  const tbodyMuestra = container.querySelector('#tbody-muestra');
  const statTotal = container.querySelector('#stat-total');
  const statValidas = container.querySelector('#stat-validas');
  const statFallidas = container.querySelector('#stat-fallidas');
  const btnCommit = container.querySelector('#btn-confirmar-commit');
  const btnPrev = container.querySelector('#btn-previsualizar');
  const selectModo = container.querySelector('#select-modo-commit');
  const defaultMetaText = 'Columnas requeridas: TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, REGIMEN, TIPO_AFILIADO.';

  let currentLoteId = null;

  function validateSelectedFile(file) {
    if (!file) {
      if (fileMeta) fileMeta.textContent = defaultMetaText;
      return false;
    }
    if (!file.name.toLowerCase().endsWith('.xlsx')) {
      ui.showToast('Solo se admiten archivos en formato Excel (.xlsx).', 'warning');
      fileInput.value = '';
      if (fileMeta) fileMeta.textContent = defaultMetaText;
      return false;
    }
    if (file.size > MAX_EXCEL_BYTES) {
      ui.showToast('El archivo excede el tamaño máximo permitido de 10 MB.', 'error');
      fileInput.value = '';
      if (fileMeta) fileMeta.textContent = defaultMetaText;
      return false;
    }
    if (fileMeta) {
      fileMeta.textContent = `Archivo seleccionado: ${file.name} (${formatBytes(file.size)})`;
    }
    return true;
  }

  fileInput?.addEventListener('change', () => {
    validateSelectedFile(fileInput.files?.[0]);
  });

  if (dropzone && fileInput) {
    ['dragenter', 'dragover'].forEach(evtName => {
      dropzone.addEventListener(evtName, (e) => {
        e.preventDefault();
        e.stopPropagation();
        dropzone.classList.add('is-dragover');
      });
    });

    ['dragleave', 'drop'].forEach(evtName => {
      dropzone.addEventListener(evtName, (e) => {
        e.preventDefault();
        e.stopPropagation();
        dropzone.classList.remove('is-dragover');
      });
    });

    dropzone.addEventListener('drop', (e) => {
      const droppedFiles = e.dataTransfer?.files;
      if (droppedFiles && droppedFiles.length > 0) {
        const dt = new DataTransfer();
        dt.items.add(droppedFiles[0]);
        fileInput.files = dt.files;
        validateSelectedFile(droppedFiles[0]);
      }
    });
  }

  // Cargar lista de EPS
  try {
    const epsList = await affiliationApi.listarEps();
    if (epsList && epsList.length > 0) {
      epsList.forEach(eps => {
        const opt = document.createElement('option');
        opt.value = eps.publicId;
        opt.textContent = `${eps.codigoMinSalud} — ${eps.nombre}`;
        selectEps.appendChild(opt);
      });
    }
  } catch (err) {
    console.warn('Lista de EPS en sincronización o no disponible:', err);
    const opt = document.createElement('option');
    opt.value = '';
    opt.disabled = true;
    opt.textContent = '-- En sincronización con el servidor --';
    selectEps.appendChild(opt);
  }

  // Descarga de plantilla oficial Excel (.xlsx)
  const btnDescargarPlantilla = container.querySelector('#btn-descargar-plantilla');
  if (btnDescargarPlantilla) {
    btnDescargarPlantilla.addEventListener('click', async () => {
      ui.setButtonLoading(btnDescargarPlantilla, true);
      try {
        const templateUrl = api.buildUrl('/affiliations/template');
        const res = await fetch(templateUrl, { credentials: 'include' });
        if (res.ok) {
          const blob = await res.blob();
          const url = window.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = 'plantilla_afiliados_eps.xlsx';
          document.body.appendChild(a);
          a.click();
          a.remove();
          window.URL.revokeObjectURL(url);
        } else {
          const a = document.createElement('a');
          a.href = '/assets/plantilla_afiliados_eps.xlsx';
          a.download = 'plantilla_afiliados_eps.xlsx';
          document.body.appendChild(a);
          a.click();
          a.remove();
        }
      } catch (err) {
        const a = document.createElement('a');
        a.href = '/assets/plantilla_afiliados_eps.xlsx';
        a.download = 'plantilla_afiliados_eps.xlsx';
        document.body.appendChild(a);
        a.click();
        a.remove();
      } finally {
        ui.setButtonLoading(btnDescargarPlantilla, false);
      }
    });
  }

  // Carga de archivo y preview
  formUpload.addEventListener('submit', async (e) => {
    e.preventDefault();
    const epsPubId = selectEps.value;
    const selectedFile = fileInput.files?.[0];
    if (!epsPubId || !selectedFile) {
      ui.showToast('Por favor seleccione la EPS y el archivo Excel (.xlsx).', 'warning');
      return;
    }
    if (!validateSelectedFile(selectedFile)) {
      return;
    }

    const formData = new FormData();
    formData.append('file', selectedFile);
    formData.append('epsPublicId', epsPubId);

    ui.setButtonLoading(btnPrev, true);

    try {
      const res = await affiliationApi.cargarPreviewExcel(formData);
      currentLoteId = res.lotePublicId;

      tbodyMuestra.innerHTML = '';
      if (res.muestraFilas && res.muestraFilas.length > 0) {
        res.muestraFilas.forEach(f => {
          const tr = document.createElement('tr');
          const esValida = f.estadoFila === 'VALIDO';
          const nombreCompleto = `${f.nombres || ''} ${f.apellidos || ''}`.trim() || '—';
          tr.innerHTML = `
            <td data-label="Fila">#${esc(f.numeroFila)}</td>
            <td data-label="Documento"><strong>${esc(f.tipoDocumento || '')}</strong> ${esc(f.numeroDocumento || '')}</td>
            <td data-label="Nombres y Apellidos">${esc(nombreCompleto)}</td>
            <td data-label="Régimen">${esc(f.regimen || '—')}</td>
            <td data-label="Tipo Afiliado">${esc(f.tipoAfiliado || '—')}</td>
            <td data-label="Estado">
              <span class="badge ${esValida ? 'badge--confirmed' : 'badge--danger'}">
                ${esValida ? 'Válida' : 'Error'}
              </span>
            </td>
            <td data-label="Observación" style="color: ${esValida ? 'inherit' : 'var(--danger)'}; font-size: 0.85rem;">
              ${esc(f.errorMotivo || 'OK para importación')}
            </td>
          `;
          tbodyMuestra.appendChild(tr);
        });
      } else {
        tbodyMuestra.innerHTML = `
          <tr>
            <td colspan="7" class="text-center text-muted p-6">No hay filas de muestra disponibles en este lote.</td>
          </tr>
        `;
      }

      cardMetricas.style.display = 'block';
      cardMuestra.style.display = 'block';
      motion.stepTransition(cardMetricas, 'forward');
      motion.stepTransition(cardMuestra, 'forward');
      motion.animateCounter(statTotal, res.totalFilas ?? 0);
      motion.animateCounter(statValidas, res.filasValidas ?? 0);
      motion.animateCounter(statFallidas, res.filasFallidas ?? 0);
      motion.staggerCards(tbodyMuestra.querySelectorAll('tr'), { delayStep: 18, maxDelay: 140, root: container });
    } catch (err) {
      ui.showToast(err.message || 'Error analizando archivo Excel.', 'error');
    } finally {
      ui.setButtonLoading(btnPrev, false);
    }
  });

  // Confirmar commit definitivo
  btnCommit.addEventListener('click', () => {
    if (!currentLoteId) return;
    const modo = selectModo.value;
    const modoLabel = modo === 'ATOMIC_ALL'
      ? 'Atómico estricto (ATOMIC_ALL)'
      : 'Solo filas válidas (VALID_ROWS)';

    ui.showModal({
      title: 'Confirmar Importación Definitiva',
      message: `¿Está seguro de aplicar los registros del lote a la base de afiliados en modo <strong>${esc(modoLabel)}</strong>?`,
      confirmText: 'Aplicar Importación',
      cancelText: 'Cancelar',
      onConfirm: async () => {
        ui.setButtonLoading(btnCommit, true);
        try {
          await affiliationApi.confirmarLote(currentLoteId, modo);
          ui.showToast('¡Importación completada con éxito! La base de afiliados ha sido actualizada.', 'success');
          cardMetricas.style.display = 'none';
          cardMuestra.style.display = 'none';
          formUpload.reset();
          if (fileMeta) fileMeta.textContent = defaultMetaText;
          currentLoteId = null;
        } catch (err) {
          ui.showToast(err.message || 'Error al aplicar el lote de importación.', 'error');
          return false;
        } finally {
          ui.setButtonLoading(btnCommit, false);
        }
      }
    });
  });
}
