/**
 * MediTriaje 2.0 — Consulta de Afiliación y Aseguramiento EPS (affiliation-search.js)
 * Consulta en tiempo real de EPS y régimen. No bloqueante para triage/urgencias (A05, ADR-025).
 */

import { affiliationApi } from '../api.js';
import { motion } from '../motion.js';
import { ui } from '../ui.js';

const formatEnumLabel = (value, fallback) => String(value || fallback).replace(/_/g, ' ');

export async function renderAffiliationSearch(container) {
  container.innerHTML = `
    <div class="view-header">
      <nav class="breadcrumb" aria-label="Ruta de navegación">Admisiones <span aria-hidden="true">/</span> Aseguramiento EPS <span aria-hidden="true">/</span> Verificación de Derechos</nav>
      <h1 class="view-title">Consulta de Aseguramiento EPS</h1>
      <p class="view-subtitle">Verifique los derechos de aseguramiento en salud del paciente en Valledupar.</p>
    </div>

    <!-- Banner normativo de no bloqueo asistencial -->
    <div class="alert alert--info alert-info mb-4" role="note" style="max-width: 720px;">
      <span class="alert-icon" aria-hidden="true">${ui.icon('info', 'icon icon--sm')}</span>
      <div class="alert-content">
        <strong class="alert-title">Aviso de Cumplimiento Legal (Ley 1751 de 2015)</strong>
        La comprobación de derechos de aseguramiento es estrictamente administrativa.
        Bajo ninguna circunstancia la falta de carné, afiliación inactiva o condición de no asegurado puede suspender o demorar la valoración médica inicial de urgencias.
      </div>
    </div>

    <div class="card mb-4" style="max-width: 720px;">
      <form id="form-consulta-eps" class="grid grid-cols-1 grid-cols-2-md gap-4">
        <div class="form-group mb-0">
          <label for="tipo-doc-eps" class="form-label label-required">Tipo Documento</label>
          <select id="tipo-doc-eps" class="form-select" required aria-label="Tipo de documento">
            <option value="CC">CC - Cédula Ciudadanía</option>
            <option value="TI">TI - Tarjeta Identidad</option>
            <option value="RC">RC - Registro Civil</option>
            <option value="CE">CE - Extranjería</option>
            <option value="PA">PA - Pasaporte</option>
            <option value="PPT">PPT - Permiso Protección</option>
          </select>
        </div>
        <div class="form-group mb-0">
          <label for="num-doc-eps" class="form-label label-required">Número Documento</label>
          <input type="text" id="num-doc-eps" class="form-input" placeholder="Ej: 1065123456" required autocomplete="off" aria-label="Número de documento de identificación" />
        </div>
        <div class="col-span-full">
          <button type="submit" id="btn-consultar" class="btn btn-primary w-full" aria-label="Consultar estado de afiliación en EPS">
            ${ui.icon('search', 'icon icon--sm')} Consultar Estado de Afiliación
          </button>
        </div>
      </form>
    </div>

    <!-- Tarjeta con el Resultado -->
    <div class="card" id="card-resultado-eps" aria-live="polite" style="display: none; max-width: 720px;">
      <div class="flex flex-wrap items-center justify-between gap-2 mb-4">
        <h2 class="card-title m-0">Estado de Aseguramiento</h2>
        <span id="badge-estado-eps" class="badge badge--neutral">-</span>
      </div>

      <div class="flex flex-col gap-3">
        <div>
          <span class="text-xs text-muted block">Paciente</span>
          <strong id="res-paciente-nombre" class="text-base">-</strong>
          <span id="res-paciente-doc" class="text-sm text-muted block">-</span>
        </div>

        <div class="grid grid-cols-1 grid-cols-2-md gap-3 pt-3 border-t">
          <div>
            <span class="text-xs text-muted block">Aseguradora (EPS)</span>
            <strong id="res-eps-nombre">-</strong>
            <div id="res-eps-codigo" class="text-xs text-muted">-</div>
          </div>
          <div>
            <span class="text-xs text-muted block">Régimen</span>
            <strong id="res-regimen">-</strong>
          </div>
        </div>

        <div class="grid grid-cols-1 grid-cols-2-md gap-3 pt-3 border-t">
          <div>
            <span class="text-xs text-muted block">Tipo Afiliado</span>
            <span id="res-tipo-afiliado" class="font-medium">-</span>
          </div>
          <div>
            <span class="text-xs text-muted block">Fuente Verificación</span>
            <span id="res-fuente-verif" class="text-xs text-muted">-</span>
          </div>
        </div>
      </div>
    </div>
  `;

  motion.staggerCards(container.querySelectorAll('.card, .alert'), { root: container });

  const form = container.querySelector('#form-consulta-eps');
  const cardRes = container.querySelector('#card-resultado-eps');
  const badgeEstado = container.querySelector('#badge-estado-eps');
  const resNombre = container.querySelector('#res-paciente-nombre');
  const resDoc = container.querySelector('#res-paciente-doc');
  const resEpsNombre = container.querySelector('#res-eps-nombre');
  const resEpsCodigo = container.querySelector('#res-eps-codigo');
  const resRegimen = container.querySelector('#res-regimen');
  const resTipoAfiliado = container.querySelector('#res-tipo-afiliado');
  const resFuente = container.querySelector('#res-fuente-verif');
  const btnConsultar = container.querySelector('#btn-consultar');

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const tipoDoc = container.querySelector('#tipo-doc-eps').value;
    const numDoc = container.querySelector('#num-doc-eps').value.trim();

    if (!tipoDoc || !numDoc) return;

    cardRes.style.display = 'none';
    ui.setButtonLoading(btnConsultar, true, 'Consultando...');

    try {
      const res = await affiliationApi.consultarAfiliacion(tipoDoc, numDoc);
      cardRes.style.display = 'block';

      resNombre.textContent = res.pacienteNombre || 'Paciente No Registrado Previamente';
      resDoc.textContent = `${res.tipoDocumento} ${res.numeroDocumento}`;
      resEpsNombre.textContent = res.epsNombre || 'PARTICULAR / NO ASEGURADO';
      resEpsCodigo.textContent = res.epsCodigo ? `Código: ${res.epsCodigo}` : '';
      resRegimen.textContent = formatEnumLabel(res.regimen, 'NO_ASEGURADO');
      resTipoAfiliado.textContent = formatEnumLabel(res.tipoAfiliado, 'N/A');
      resFuente.textContent = formatEnumLabel(res.fuenteVerificacion, 'CONSULTA_DIRECTA');

      if (res.estado === 'ACTIVO') {
        badgeEstado.textContent = 'ACTIVO';
        badgeEstado.className = 'badge badge--confirmed';
      } else if (res.estado === 'NO_AFILIADO') {
        badgeEstado.textContent = 'NO AFILIADO';
        badgeEstado.className = 'badge badge--warning';
      } else {
        badgeEstado.textContent = res.estado || 'DESCONOCIDO';
        badgeEstado.className = 'badge badge--neutral';
      }

      motion.stepTransition(cardRes, 'forward', { root: container });
      motion.pulseSubtle(badgeEstado, { root: container });
    } catch (err) {
      ui.showToast(err.message || 'Error consultando aseguramiento.', 'error');
    } finally {
      ui.setButtonLoading(btnConsultar, false);
    }
  });
}
