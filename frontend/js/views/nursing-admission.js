/**
 * MediTriaje 2.0 — Admisión Presencial de Urgencias (nursing-admission.js)
 * Registro expedito de ingreso de urgencias para:
 * - Pacientes buscados por documento (registrados o atendidos previamente),
 * - Pacientes sin cuenta web previa en MediTriaje (registro de datos civiles básicos),
 * - Pacientes no identificados (NN) con código provisional opaco (U02, U03).
 */

import { emergencyApi } from '../api.js';
import { router } from '../router.js';
import { ui, esc } from '../ui.js';

export async function nursingAdmissionView(container) {
  container.innerHTML = `
    <div style="max-width: 800px; margin: 0 auto; padding-top: var(--space-4); padding-bottom: var(--space-12);">
      
      <!-- Navegación y Encabezado -->
      <div class="mb-6">
        <a href="#/nursing/dashboard" class="btn btn-ghost btn--sm flex items-center gap-1 mb-2" style="width: fit-content;">
          ${ui.icon('arrow-left', 'icon icon--sm')}
          <span>Volver a la Cola de Urgencias</span>
        </a>
        <h1 class="text-2xl font-bold m-0">Admisión Presencial de Urgencias</h1>
        <p class="text-sm text-muted m-0">Registro expedito de ingreso hospitalario sin bloqueos administrativos de aseguramiento</p>
      </div>

      <div class="card p-6">
        <form id="admissionForm" novalidate>
          
          <!-- Hospital único: el servidor asigna la sede del episodio -->

          <!-- Tipo de Identificación / Switch NN -->
          <div class="card p-4 mb-4" style="background-color: var(--card-bg, #f8fafc); border: 1px solid var(--border);">
            <div class="flex items-center justify-between mb-3">
              <div>
                <span class="font-bold text-sm">¿El paciente cuenta con identificación civil?</span>
                <p class="text-xs text-muted m-0">Si el paciente ingresa inconsciente, desorientado o indocumentado, active el registro provisional.</p>
              </div>
              <label class="switch flex items-center gap-2 cursor-pointer">
                <input type="checkbox" id="chkEsNN" style="width: 20px; height: 20px;">
                <span class="font-semibold text-sm" id="lblEsNN">No identificado (NN)</span>
              </label>
            </div>

            <!-- Bloque Paciente Identificado (búsqueda por cédula o registro sin cuenta web) -->
            <div id="bloquePacienteCivil">
              <label for="txtBuscarPaciente" class="form-label text-sm font-semibold">Buscar Paciente por Cédula / Documento</label>
              <div class="flex flex-wrap gap-2">
                <select id="selTipoDocBuscar" class="form-select" style="width: auto; min-width: 110px;" aria-label="Tipo de documento">
                  <option value="">Todos</option>
                  <option value="CC">CC</option>
                  <option value="TI">TI</option>
                  <option value="RC">RC</option>
                  <option value="CE">CE</option>
                  <option value="PA">PA</option>
                </select>
                <input type="text" id="txtBuscarPaciente" class="form-input" style="flex: 1; min-width: 180px;" placeholder="Ej. 1134169348">
                <button type="button" id="btnBuscarPaciente" class="btn btn-secondary btn--sm">Buscar</button>
              </div>
              <div id="resultadoBusquedaPaciente" class="mt-2 text-sm"></div>
              <input type="hidden" id="hdnPacientePublicId" value="">

              <div class="mt-3 pt-3 border-t flex flex-wrap items-center justify-between gap-2">
                <span class="text-xs text-muted">¿El paciente no está registrado en MediTriaje?</span>
                <button type="button" id="btnTogglePacienteManual" class="btn btn-ghost btn--xs">
                  ${ui.icon('user-plus', 'icon icon--xs')}
                  <span>Ingresar datos del paciente (sin cuenta en MediTriaje)</span>
                </button>
              </div>

              <!-- Bloque Registro Presencial Sin Cuenta Web -->
              <div id="bloquePacienteManual" style="display: none;" class="mt-3 p-3 rounded border bg-white">
                <div class="flex items-center justify-between mb-3">
                  <span class="font-bold text-sm">Datos Civiles del Paciente (Sin Cuenta MediTriaje)</span>
                  <span class="badge badge--info text-xs">Ingreso presencial directo</span>
                </div>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-3 mb-3">
                  <div class="form-group m-0">
                    <label for="selManualTipoDoc" class="form-label text-xs font-semibold">Tipo de Documento <span class="text-danger">*</span></label>
                    <select id="selManualTipoDoc" class="form-select">
                      <option value="CC">Cédula de Ciudadanía (CC)</option>
                      <option value="TI">Tarjeta de Identidad (TI)</option>
                      <option value="RC">Registro Civil (RC)</option>
                      <option value="CE">Cédula de Extranjería (CE)</option>
                      <option value="PA">Pasaporte (PA)</option>
                    </select>
                  </div>
                  <div class="form-group m-0">
                    <label for="txtManualNumDoc" class="form-label text-xs font-semibold">Número de Documento <span class="text-danger">*</span></label>
                    <input type="text" id="txtManualNumDoc" class="form-input" placeholder="Ej. 1134169348">
                  </div>
                  <div class="form-group m-0">
                    <label for="txtManualNombres" class="form-label text-xs font-semibold">Nombres <span class="text-danger">*</span></label>
                    <input type="text" id="txtManualNombres" class="form-input" placeholder="Ej. Laura Sofía">
                  </div>
                  <div class="form-group m-0">
                    <label for="txtManualApellidos" class="form-label text-xs font-semibold">Apellidos <span class="text-danger">*</span></label>
                    <input type="text" id="txtManualApellidos" class="form-input" placeholder="Ej. Morales Ruiz">
                  </div>
                  <div class="form-group m-0">
                    <label for="txtManualFechaNac" class="form-label text-xs font-semibold">Fecha de Nacimiento <span class="text-danger">*</span></label>
                    <input type="date" id="txtManualFechaNac" class="form-input">
                  </div>
                  <div class="form-group m-0">
                    <label for="txtManualTelefono" class="form-label text-xs font-semibold">Teléfono Celular</label>
                    <input type="tel" id="txtManualTelefono" class="form-input" placeholder="Ej. 3001234567">
                  </div>
                </div>
              </div>
            </div>

            <!-- Bloque Paciente Sin Identificación (NN) -->
            <div id="bloquePacienteNN" style="display: none;" class="mt-4 pt-4 border-t">
              <div class="badge badge--warning mb-3">Modo Paciente No Identificado (NN) Activo</div>
              
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-3">
                <div class="form-group">
                  <label for="txtEdadAparente" class="form-label text-sm">Edad Aparente Estimada</label>
                  <input type="number" id="txtEdadAparente" class="form-input" min="0" max="120" placeholder="Ej. 40">
                </div>
                <div class="form-group">
                  <label for="selGeneroAparente" class="form-label text-sm">Género Aparente</label>
                  <select id="selGeneroAparente" class="form-select">
                    <option value="">Sin especificar</option>
                    <option value="MASCULINO">Masculino</option>
                    <option value="FEMENINO">Femenino</option>
                    <option value="OTRO">Otro / Indeterminado</option>
                  </select>
                </div>
              </div>

              <div class="form-group mb-3">
                <label for="selCondicionLlegada" class="form-label text-sm">Condición Neurológica / Estado de Llegada</label>
                <select id="selCondicionLlegada" class="form-select">
                  <option value="CONSCIENTE">Consciente / Orientado</option>
                  <option value="DESORIENTADO">Desorientado / Confuso</option>
                  <option value="INCONSCIENTE">Inconsciente / En coma</option>
                  <option value="PARO">Paro Cardiorrespiratorio inminente</option>
                </select>
              </div>

              <div class="form-group">
                <label for="txtDescripcionFisica" class="form-label text-sm">Rasgos Físicos Relevantes / Señas Particulares</label>
                <textarea id="txtDescripcionFisica" class="form-input" rows="2" placeholder="Ej. Tatuaje en antebrazo derecho, cicatriz frontal, vestimenta azul..."></textarea>
              </div>
            </div>
          </div>

          <!-- Datos de Llegada y Admisión -->
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
            <div class="form-group">
              <label for="selViaIngreso" class="form-label font-semibold">Vía de Ingreso <span class="text-danger">*</span></label>
              <select id="selViaIngreso" class="form-select" required>
                <option value="ESPONTANEO">Ingreso Espontáneo (por sus propios medios)</option>
                <option value="AMBULANCIA">Ambulancia / Traslado prehospitalario (TAM/TAB)</option>
                <option value="REMITIDO">Remisión de otra IPS</option>
                <option value="POLICIA">Acompañado por Fuerza Pública / Policía</option>
              </select>
            </div>

            <div class="form-group">
              <label for="txtAcompananteNombre" class="form-label">Nombre del Acompañante / Reportante</label>
              <input type="text" id="txtAcompananteNombre" class="form-input" placeholder="Nombre completo">
            </div>
          </div>

          <div class="form-group mb-4">
            <label for="txtAcompananteTelefono" class="form-label">Teléfono de Contacto del Acompañante</label>
            <input type="tel" id="txtAcompananteTelefono" class="form-input" placeholder="Ej. 3001234567">
          </div>

          <!-- Motivo de Ingreso -->
          <div class="form-group mb-4">
            <label for="txtMotivoConsulta" class="form-label font-semibold">Motivo Principal de Consulta / Síntomas Agudos <span class="text-danger">*</span></label>
            <textarea id="txtMotivoConsulta" class="form-input" rows="3" required placeholder="Describa brevemente la urgencia por la que acude el paciente..."></textarea>
          </div>

          <div class="form-group mb-6">
            <label for="txtObservaciones" class="form-label">Observaciones Administrativas / Notas de Triage Previo</label>
            <textarea id="txtObservaciones" class="form-input" rows="2" placeholder="Información de antecedentes inmediatos, traslados o circunstancias del evento..."></textarea>
          </div>

          <!-- Acciones -->
          <div class="flex items-center justify-end gap-3">
            <a href="#/nursing/dashboard" class="btn btn-ghost">Cancelar</a>
            <button type="submit" id="btnSubmitAdmission" class="btn btn-primary flex items-center gap-2">
              ${ui.icon('check', 'icon icon--sm')}
              <span>Completar Admisión y Proceder a Triaje</span>
            </button>
          </div>

        </form>
      </div>

    </div>
  `;

  const chkNN = document.getElementById('chkEsNN');
  const bloqueCivil = document.getElementById('bloquePacienteCivil');
  const bloqueNN = document.getElementById('bloquePacienteNN');
  const bloqueManual = document.getElementById('bloquePacienteManual');
  const btnToggleManual = document.getElementById('btnTogglePacienteManual');
  const btnBuscar = document.getElementById('btnBuscarPaciente');
  const txtBuscar = document.getElementById('txtBuscarPaciente');
  const selTipoBuscar = document.getElementById('selTipoDocBuscar');
  const divResultado = document.getElementById('resultadoBusquedaPaciente');
  const hdnPacienteId = document.getElementById('hdnPacientePublicId');

  const setManualOpen = (open) => {
    bloqueManual.style.display = open ? 'block' : 'none';
    if (open) {
      hdnPacienteId.value = '';
    }
  };

  chkNN.addEventListener('change', () => {
    if (chkNN.checked) {
      bloqueCivil.style.display = 'none';
      bloqueNN.style.display = 'block';
      hdnPacienteId.value = '';
      setManualOpen(false);
    } else {
      bloqueCivil.style.display = 'block';
      bloqueNN.style.display = 'none';
    }
  });

  btnToggleManual.addEventListener('click', () => {
    const willOpen = bloqueManual.style.display === 'none';
    setManualOpen(willOpen);
    if (willOpen) {
      divResultado.innerHTML = `<span class="text-xs text-muted">Modo de registro presencial directo activo.</span>`;
      const q = txtBuscar.value.trim();
      const manualDoc = document.getElementById('txtManualNumDoc');
      if (q && manualDoc && !manualDoc.value.trim()) {
        manualDoc.value = q;
      }
      if (selTipoBuscar.value) {
        document.getElementById('selManualTipoDoc').value = selTipoBuscar.value;
      }
    } else {
      divResultado.innerHTML = '';
    }
  });

  const showSelectedPatient = (label) => {
    divResultado.innerHTML = `
      <div class="p-3 border rounded bg-white flex flex-wrap items-center justify-between gap-2">
        <div class="text-success font-semibold">✓ Paciente seleccionado: ${esc(label)}</div>
        <button type="button" id="btnCambiarPaciente" class="btn btn-ghost btn--xs">Cambiar</button>
      </div>
    `;
    document.getElementById('btnCambiarPaciente')?.addEventListener('click', () => {
      hdnPacienteId.value = '';
      divResultado.innerHTML = '';
      txtBuscar.focus();
    });
  };

  btnBuscar.addEventListener('click', async () => {
    const q = txtBuscar.value.trim();
    const tipoDoc = selTipoBuscar.value;
    if (!q) {
      divResultado.innerHTML = `<span class="text-warning">Ingrese un número de documento a buscar.</span>`;
      return;
    }

    hdnPacienteId.value = '';
    divResultado.innerHTML = `<span class="text-muted">Buscando paciente por documento...</span>`;
    try {
      const pacientes = await emergencyApi.buscarPacientesPorDocumento(q, tipoDoc);
      if (Array.isArray(pacientes) && pacientes.length > 0) {
        setManualOpen(false);
        divResultado.innerHTML = `
          <div class="flex flex-col gap-2 mt-2">
            ${pacientes.map(p => `
              <div class="p-3 border rounded bg-white flex flex-wrap items-center justify-between gap-2">
                <div>
                  <div class="font-bold text-sm">${esc(p.nombreCompleto || `${p.nombres || ''} ${p.apellidos || ''}`.trim())}</div>
                  <div class="text-xs text-muted font-mono">
                    ${esc(p.tipoDocumento)} ${esc(p.numeroDocumento)}${p.telefono ? ` · Tel: ${esc(p.telefono)}` : ''}
                  </div>
                </div>
                <div class="flex items-center gap-2">
                  <span class="badge ${p.tieneCuentaMediTriaje ? 'badge--confirmed' : 'badge--info'} text-xs">
                    ${p.tieneCuentaMediTriaje ? 'Registrado en MediTriaje' : 'Sin cuenta web'}
                  </span>
                  <button
                    type="button"
                    class="btn btn-xs btn-primary btn-seleccionar-paciente"
                    data-public-id="${esc(p.publicId)}"
                    data-label="${esc(`${p.nombreCompleto || ''} (${p.tipoDocumento} ${p.numeroDocumento})`)}"
                  >
                    Seleccionar
                  </button>
                </div>
              </div>
            `).join('')}
          </div>
        `;

        divResultado.querySelectorAll('.btn-seleccionar-paciente').forEach(btn => {
          btn.addEventListener('click', () => {
            const pubId = btn.getAttribute('data-public-id') || '';
            const label = btn.getAttribute('data-label') || pubId;
            hdnPacienteId.value = pubId;
            setManualOpen(false);
            showSelectedPatient(label);
          });
        });
      } else {
        divResultado.innerHTML = `
          <div class="p-3 border rounded bg-amber-50 text-xs text-amber-900">
            No se encontró un paciente registrado con el documento <strong>${esc(q)}</strong>.
            Complete los datos a continuación para admitirlo sin necesidad de cuenta en MediTriaje.
          </div>
        `;
        setManualOpen(true);
        document.getElementById('txtManualNumDoc').value = q;
        if (tipoDoc) {
          document.getElementById('selManualTipoDoc').value = tipoDoc;
        }
      }
    } catch (err) {
      divResultado.innerHTML = `<span class="text-danger">Error buscando paciente: ${esc(err.message || 'Error de servidor')}</span>`;
    }
  });

  // Envío del Formulario
  const form = document.getElementById('admissionForm');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const esNN = chkNN.checked;
    const pacientePubId = hdnPacienteId.value.trim();
    const manualActivo = bloqueManual.style.display !== 'none';
    const viaIngreso = document.getElementById('selViaIngreso').value;
    const motivoConsulta = document.getElementById('txtMotivoConsulta').value.trim();
    const acompananteNombre = document.getElementById('txtAcompananteNombre').value.trim();
    const acompananteTelefono = document.getElementById('txtAcompananteTelefono').value.trim();
    const observaciones = document.getElementById('txtObservaciones').value.trim();

    if (!motivoConsulta) {
      ui.showToast('El motivo de consulta es obligatorio.', 'warning');
      return;
    }

    let payload;
    if (esNN) {
      payload = {
        pacientePublicId: null,
        esIdentidadProvisional: true,
        medioLlegada: viaIngreso,
        motivoLlegada: motivoConsulta,
        acompananteNombre: acompananteNombre || null,
        acompananteContacto: acompananteTelefono || null,
        observaciones: observaciones || null,
        descripcionFisica: document.getElementById('txtDescripcionFisica').value.trim() || null,
        edadAparente: document.getElementById('txtEdadAparente').value
          ? parseInt(document.getElementById('txtEdadAparente').value, 10)
          : null,
        generoAparente: document.getElementById('selGeneroAparente').value || null,
        condicionLlegada: document.getElementById('selCondicionLlegada').value || null
      };
    } else if (pacientePubId) {
      payload = {
        pacientePublicId: pacientePubId,
        esIdentidadProvisional: false,
        medioLlegada: viaIngreso,
        motivoLlegada: motivoConsulta,
        acompananteNombre: acompananteNombre || null,
        acompananteContacto: acompananteTelefono || null,
        observaciones: observaciones || null
      };
    } else if (manualActivo) {
      const tipoDoc = document.getElementById('selManualTipoDoc').value;
      const numDoc = document.getElementById('txtManualNumDoc').value.trim();
      const nombres = document.getElementById('txtManualNombres').value.trim();
      const apellidos = document.getElementById('txtManualApellidos').value.trim();
      const fechaNac = document.getElementById('txtManualFechaNac').value;
      const telefono = document.getElementById('txtManualTelefono').value.trim();

      if (!numDoc || !nombres || !apellidos || !fechaNac) {
        ui.showToast('Complete documento, nombres, apellidos y fecha de nacimiento del paciente.', 'warning');
        return;
      }

      payload = {
        pacientePublicId: null,
        esIdentidadProvisional: false,
        tipoDocumentoPaciente: tipoDoc,
        numeroDocumentoPaciente: numDoc,
        nombresPaciente: nombres,
        apellidosPaciente: apellidos,
        fechaNacimientoPaciente: fechaNac,
        telefonoPaciente: telefono || null,
        medioLlegada: viaIngreso,
        motivoLlegada: motivoConsulta,
        acompananteNombre: acompananteNombre || null,
        acompananteContacto: acompananteTelefono || null,
        observaciones: observaciones || null
      };
    } else {
      ui.showToast('Busque y seleccione un paciente por cédula, ingrese sus datos sin cuenta o active el modo NN.', 'warning');
      return;
    }

    const submitBtn = document.getElementById('btnSubmitAdmission');
    submitBtn.disabled = true;
    submitBtn.innerHTML = `Guardando admisión...`;

    try {
      const resp = await emergencyApi.registrarAdmision(payload);
      ui.showToast('Admisión de urgencias registrada con éxito.', 'success');
      router.navigate(`/nursing/assessment/${resp.episodioPublicId}`);
    } catch (err) {
      submitBtn.disabled = false;
      submitBtn.innerHTML = `${ui.icon('check', 'icon icon--sm')} <span>Completar Admisión y Proceder a Triaje</span>`;
      ui.showToast('Error registrando admisión: ' + (err.message || 'Error de servidor'), 'error');
    }
  });
}
