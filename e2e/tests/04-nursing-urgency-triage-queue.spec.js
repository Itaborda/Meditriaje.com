// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * Flujo Fase 2 (Lote U): Enfermería y Circuito de Urgencias Presenciales
 * Cubre los requisitos del Plan Maestro:
 * - RF-001 / U01: Experiencia y rol de enfermería (ROLE_ENFERMERIA).
 * - RF-002 / U02: Admisión presencial de urgencias sin cuenta previa ni bloqueo administrativo.
 * - RF-003 / U03: Identidad provisional para paciente indocumentado (NN-XXXXXX).
 * - RF-004 / U03: Identificación posterior y trazabilidad de paciente provisional.
 * - RF-005 / U04: Valoración presencial de triaje humano I-V con signos vitales y Glasgow.
 * - RF-006 / U04: Múltiples reevaluaciones dinámicas inmutables append-only.
 * - RF-007 / U05: Cola priorizada de urgencias por sede en tiempo real.
 */

test.describe('Fase 2: Circuito de Enfermería, Urgencias y Triaje Presencial', () => {
  const nurseEmail = process.env.DEMO_NURSE_EMAIL || 'enfermera@demo.meditriaje.test';
  const password = process.env.DEMO_PASSWORD || 'DemoSecretPass123!';

  test.beforeEach(async ({ page }) => {
    // Interceptor inteligente de APIs para sesión de enfermería persistente
    await page.route('**/api/v1/auth/refresh', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: 'usr-enfermera-01',
          email: nurseEmail,
          nombreCompleto: 'Enfermera Asistencial Turno Urgencias',
          roles: ['ROLE_ENFERMERIA']
        })
      });
    });

    await page.route('**/api/v1/admin/sites', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { publicId: 'sede-valledupar-01', nombre: 'Sede Principal Valledupar', ciudad: 'Valledupar' }
        ])
      });
    });

    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: 'usr-enfermera-01',
          email: nurseEmail,
          nombreCompleto: 'Enfermera Asistencial Turno Urgencias',
          roles: ['ROLE_ENFERMERIA']
        })
      });
    });
  });

  test('debe autenticarse como enfermería y visualizar el Centro de Urgencias con métricas', async ({ page }) => {
    let isUserLoggedIn = false;
    await page.route('**/api/v1/auth/refresh', async (route) => {
      if (!isUserLoggedIn) {
        await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ message: 'No session' }) });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            publicId: 'usr-enfermera-01',
            email: nurseEmail,
            nombreCompleto: 'Enfermera Asistencial Turno Urgencias',
            roles: ['ROLE_ENFERMERIA']
          })
        });
      }
    });

    await page.route('**/api/v1/auth/login', async (route) => {
      isUserLoggedIn = true;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: 'usr-enfermera-01',
          email: nurseEmail,
          nombreCompleto: 'Enfermera Asistencial',
          roles: ['ROLE_ENFERMERIA']
        })
      });
    });

    await page.route('**/api/v1/emergency/queue*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          sedeId: 'sede-valledupar-01',
          totalEnEspera: 2,
          criticos: 1,
          pendientesTriaje: 1,
          pacientesNN: 1,
          items: [
            {
              episodioId: 'ep-001',
              fechaIngreso: new Date().toISOString(),
              pacienteNombre: 'Paciente NN (NN-849201)',
              esIdentidadProvisional: true,
              codigoProvisional: 'NN-849201',
              motivoIngreso: 'Accidente de tránsito en moto, inconsciente',
              nivelTriaje: 'I',
              minutosEspera: 5,
              estado: 'EN_TRIAJE',
              medicoAsignado: null
            },
            {
              episodioId: 'ep-002',
              fechaIngreso: new Date().toISOString(),
              pacienteNombre: 'Carlos Andrés Gómez',
              esIdentidadProvisional: false,
              documento: '1065123456',
              motivoIngreso: 'Dolor abdominal cólico difuso',
              nivelTriaje: 'III',
              minutosEspera: 45,
              estado: 'REGISTRADO',
              medicoAsignado: null
            }
          ]
        })
      });
    });

    await page.goto('/#/login');
    await expect(page.locator('#formLogin')).toBeVisible();
    await page.fill('#loginEmail', nurseEmail);
    await page.fill('#loginPassword', password);
    await page.click('#formLogin button[type="submit"]');

    // Debe dirigir al panel de enfermería
    await expect(page).toHaveURL(/.*#\/nursing/);
    await expect(page.locator('h1')).toContainText('Centro de Urgencias y Triaje');

    // Verificar tarjetas de KPIs de urgencias
    await expect(page.locator('#kpiTotal')).toContainText('2');
    await expect(page.locator('#kpiCriticos')).toContainText('1');
    await expect(page.locator('#kpiNN')).toContainText('1');

    // Verificar que la tabla muestra las filas de la cola
    const queueTable = page.locator('#urgencyQueueTable');
    await expect(queueTable).toBeVisible();
    await expect(page.locator('text=NN-849201').first()).toBeVisible();
    await expect(page.locator('text=Carlos Andrés Gómez')).toBeVisible();
  });

  test('debe admitir un paciente indocumentado NN con código provisional opaco (U02, U03)', async ({ page }) => {
    let admissionPayload = null;
    await page.route('**/api/v1/emergency/admissions', async (route) => {
      admissionPayload = route.request().postDataJSON();
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          episodioPublicId: 'ep-nn-003',
          codigoProvisional: 'NN-991204',
          esNN: true,
          estado: 'REGISTRADO',
          mensaje: 'Paciente provisional admitido exitosamente'
        })
      });
    });

    await page.goto('/#/nursing/admission');
    await expect(page.locator('h1')).toContainText('Admisión Presencial de Urgencias');

    // Hospital único: no hay selector de sede (el servidor la resuelve)

    // Activar switch de paciente no identificado (NN)
    const chkNN = page.locator('#chkEsNN');
    await chkNN.check();

    // Comprobar que el bloque NN se hace visible y se oculta la búsqueda civil
    await expect(page.locator('#bloquePacienteNN')).toBeVisible();
    await expect(page.locator('#bloquePacienteCivil')).toBeHidden();

    // Rellenar datos estimativos del paciente NN
    await page.fill('#txtEdadAparente', '45');
    await page.selectOption('#selGeneroAparente', 'MASCULINO');
    await page.selectOption('#selCondicionLlegada', 'DESORIENTADO');
    await page.fill('#txtDescripcionFisica', 'Cicatriz en ceja izquierda, pantalón de mezclilla azul');

    // Vía de ingreso y motivo de urgencia
    await page.selectOption('#selViaIngreso', 'AMBULANCIA');
    await page.fill('#txtAcompananteNombre', 'Paramédico Defensa Civil');
    await page.fill('#txtAcompananteTelefono', '3009876543');
    await page.fill('#txtMotivoConsulta', 'Paciente hallado en vía pública con traumatismo craneoencefálico leve y confusión');

    // Enviar admisión
    await page.click('#btnSubmitAdmission');

    // Verificar que el payload contiene la bandera esNN y los datos
    expect(admissionPayload).not.toBeNull();
    expect(admissionPayload.esIdentidadProvisional).toBe(true);
    expect(admissionPayload.motivoLlegada).toContain('traumatismo craneoencefálico');
    expect(admissionPayload.edadAparente).toBe(45);
  });

  test('debe calcular el nivel de triaje automáticamente desde los síntomas ingresados (U04)', async ({ page }) => {
    const episodeId = 'ep-test-val-01';

    await page.route('**/api/v1/triage/symptoms', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { publicId: 'sym-1', codigo: 'DOLOR_TORACICO_OPRESIVO', nombre: 'Dolor torácico opresivo', categoria: 'CARDIOVASCULAR', esAlarma: true },
          { publicId: 'sym-7', codigo: 'FIEBRE', nombre: 'Fiebre', categoria: 'GENERAL', esAlarma: false },
          { publicId: 'sym-12', codigo: 'DOLOR_ABDOMINAL', nombre: 'Dolor abdominal', categoria: 'DIGESTIVO', esAlarma: false }
        ])
      });
    });

    await page.route('**/api/v1/emergency/triage-assessments/calculate', async (route) => {
      const req = route.request().postDataJSON();
      const hasAlarm = (req.sintomas || []).some(s => s.codigo === 'DOLOR_TORACICO_OPRESIVO');
      const maxInt = Math.max(...(req.sintomas || []).map(s => s.intensidad ?? 0));
      const nivel = hasAlarm ? 'I' : (maxInt >= 7 ? 'II' : (maxInt >= 4 ? 'III' : 'IV'));
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          nivel,
          rutaSugerida: nivel === 'I' ? 'URGENCIAS' : 'ATENCION_PRIORITARIA',
          esEmergencia: nivel === 'I',
          versionReglas: 'v1-prototipo',
          sintomasAlarma: hasAlarm ? ['DOLOR_TORACICO_OPRESIVO'] : [],
          mensaje: nivel === 'I' ? 'Llama al 123 o acude a urgencias de inmediato.' : 'Se sugiere atención prioritaria.'
        })
      });
    });

    // Mock detalle del episodio
    await page.route(`**/api/v1/emergency/episodes/${episodeId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: episodeId,
          pacienteNombre: 'María Elena Restrepo',
          pacienteDocumento: '39485762',
          edad: 52,
          motivoConsulta: 'Fiebre persistente y malestar general',
          viaIngreso: 'ESPONTANEO',
          estado: 'REGISTRADO'
        })
      });
    });

    await page.route(`**/api/v1/emergency/episodes/${episodeId}/triage-assessments`, async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([])
        });
      } else {
        assessmentPayload = route.request().postDataJSON();
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            publicId: 'val-001',
            episodioPublicId: episodeId,
            version: 1,
            nivel: 'II',
            sintomas: [
              { codigo: 'FIEBRE', nombre: 'Fiebre', duracionHoras: 6, intensidad: 8, esAlarma: false }
            ]
          })
        });
      }
    });

    let assessmentPayload = null;

    await page.goto(`/#/nursing/assessment/${episodeId}`);
    await expect(page.locator('#formAssessmentTitle')).toContainText('Registro de Valoración de Triaje');

    // La enfermera no debe poder seleccionar manualmente el nivel de prioridad
    await expect(page.locator('input[name="rdoNivel"]')).toHaveCount(0);

    // Seleccionar síntoma e intensidad alta para calcular Nivel II automáticamente
    await page.click('#nursingSymptomsList button[data-code="FIEBRE"]');
    await page.click('#selectedSymptomsContainer .scale-btn[data-code="FIEBRE"][data-val="8"]');
    await page.click('#selectedSymptomsContainer .duration-preset[data-code="FIEBRE"][data-hours="6"]');

    await expect(page.locator('#calculatedPriorityPanel')).toHaveAttribute('data-calculated-level', 'II');
    await expect(page.locator('#calculatedPriorityPanel')).toContainText('Nivel II');

    // Rellenar signos vitales completos
    await page.fill('#txtPA', '150/95');
    await page.fill('#txtFC', '102');
    await page.fill('#txtFR', '24');
    await page.fill('#txtSO2', '93');
    await page.fill('#txtTemp', '39.1');
    await page.fill('#txtGlasgow', '15');

    // Hallazgos clínicos
    await page.fill('#txtHallazgos', 'Diaforesis profusa, taquicardia febril y escalofríos');

    // Guardar valoración
    await page.click('#btnSubmitAssessment');

    // Verificar que no se envía nivel manual y sí se envían los síntomas ingresados
    expect(assessmentPayload).not.toBeNull();
    expect(assessmentPayload.nivel).toBeUndefined();
    expect(assessmentPayload.sintomas).toEqual([
      { codigo: 'FIEBRE', duracionHoras: 6, intensidad: 8 }
    ]);
    expect(assessmentPayload.presionArterial).toBe('150/95');
    expect(assessmentPayload.frecuenciaCardiaca).toBe(102);
    expect(assessmentPayload.escalaGlasgow).toBe(15);
  });

  test('debe permitir la reevaluación dinámica de un paciente ya clasificado (RF-006 / U04)', async ({ page }) => {
    const episodeId = 'ep-test-reeval-02';

    await page.route('**/api/v1/triage/symptoms', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { publicId: 'sym-10', codigo: 'DOLOR_CABEZA', nombre: 'Dolor de cabeza', categoria: 'NEUROLOGICO', esAlarma: false }
        ])
      });
    });

    // Mock episodio con valoración previa existente
    await page.route(`**/api/v1/emergency/episodes/${episodeId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicId: episodeId,
          pacienteNombre: 'Jorge Iván Arboleda',
          motivoConsulta: 'Cefalea intensa y mareo',
          estado: 'EN_TRIAJE'
        })
      });
    });

    // Simular que ya existe 1 valoración previa en el historial inmutable
    await page.route(`**/api/v1/emergency/episodes/${episodeId}/triage-assessments`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            publicId: 'val-prev-01',
            nivel: 'III',
            evaluadorNombre: 'Enfermera Turno Mañana',
            fechaRegistro: new Date(Date.now() - 3600000).toISOString(),
            presionArterial: '130/85',
            frecuenciaCardiaca: 82,
            escalaGlasgow: 15,
            hallazgosClinicos: 'Valoración inicial de ingreso',
            sintomas: [
              { codigo: 'DOLOR_CABEZA', nombre: 'Dolor de cabeza', duracionHoras: 4, intensidad: 5, esAlarma: false }
            ]
          }
        ])
      });
    });

    await page.goto(`/#/nursing/assessment/${episodeId}`);

    await expect(page.locator('input[name="rdoNivel"]')).toHaveCount(0);

    // Comprobar que detecta automáticamente que es una REEVALUACIÓN clínica (Versión 2)
    await expect(page.locator('#bloqueReevaluacion')).toBeVisible();
    await expect(page.locator('#formAssessmentTitle')).toContainText('Reevaluación Clínica de Urgencias (Versión 2)');

    // El campo de motivo de cambio debe estar presente
    await expect(page.locator('#txtMotivoReevaluacion')).toBeVisible();
  });

  test('debe buscar paciente registrado por cédula y admitirlo con su UUID (U02)', async ({ page }) => {
    let searchQuery = null;
    await page.route('**/api/v1/emergency/patients/search**', async (route) => {
      searchQuery = new URL(route.request().url()).searchParams.get('numeroDocumento');
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            publicId: 'pac-uuid-1134',
            tipoDocumento: 'CC',
            numeroDocumento: '1134169348',
            nombres: 'Soraya',
            apellidos: 'Jimenez',
            nombreCompleto: 'Soraya Jimenez',
            fechaNacimiento: '1996-04-10',
            telefono: '3001112233',
            tieneCuentaMediTriaje: true
          }
        ])
      });
    });

    let admissionPayload = null;
    await page.route('**/api/v1/emergency/admissions', async (route) => {
      admissionPayload = route.request().postDataJSON();
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          episodioPublicId: 'ep-ced-001',
          pacientePublicId: 'pac-uuid-1134',
          estado: 'REGISTRADO'
        })
      });
    });

    await page.goto('/#/nursing/admission');
    await page.fill('#txtBuscarPaciente', '1134169348');
    await page.click('#btnBuscarPaciente');

    expect(searchQuery).toBe('1134169348');
    await expect(page.locator('text=Soraya Jimenez')).toBeVisible();
    await page.click('.btn-seleccionar-paciente');
    await expect(page.locator('#hdnPacientePublicId')).toHaveValue('pac-uuid-1134');

    await page.selectOption('#selViaIngreso', 'ESPONTANEO');
    await page.fill('#txtMotivoConsulta', 'Fiebre alta');
    await page.click('#btnSubmitAdmission');

    expect(admissionPayload).not.toBeNull();
    expect(admissionPayload.pacientePublicId).toBe('pac-uuid-1134');
    expect(admissionPayload.esIdentidadProvisional).toBe(false);
    expect(admissionPayload.motivoLlegada).toBe('Fiebre alta');
  });

  test('debe permitir admitir un paciente presencial sin cuenta previa en MediTriaje (U02)', async ({ page }) => {
    await page.route('**/api/v1/emergency/patients/search**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([])
      });
    });

    let admissionPayload = null;
    await page.route('**/api/v1/emergency/admissions', async (route) => {
      admissionPayload = route.request().postDataJSON();
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          episodioPublicId: 'ep-walkin-002',
          pacientePublicId: 'pac-walkin-new',
          estado: 'REGISTRADO'
        })
      });
    });

    await page.goto('/#/nursing/admission');
    await page.fill('#txtBuscarPaciente', '1099887766');
    await page.click('#btnBuscarPaciente');

    await expect(page.locator('#bloquePacienteManual')).toBeVisible();
    await expect(page.locator('#txtManualNumDoc')).toHaveValue('1099887766');
    await page.fill('#txtManualNombres', 'Laura');
    await page.fill('#txtManualApellidos', 'Morales');
    await page.fill('#txtManualFechaNac', '1994-08-20');
    await page.fill('#txtManualTelefono', '3005556677');

    await page.selectOption('#selViaIngreso', 'ESPONTANEO');
    await page.fill('#txtMotivoConsulta', 'Dolor lumbar agudo');
    await page.click('#btnSubmitAdmission');

    expect(admissionPayload).not.toBeNull();
    expect(admissionPayload.pacientePublicId).toBeNull();
    expect(admissionPayload.esIdentidadProvisional).toBe(false);
    expect(admissionPayload.tipoDocumentoPaciente).toBe('CC');
    expect(admissionPayload.numeroDocumentoPaciente).toBe('1099887766');
    expect(admissionPayload.nombresPaciente).toBe('Laura');
    expect(admissionPayload.apellidosPaciente).toBe('Morales');
    expect(admissionPayload.fechaNacimientoPaciente).toBe('1994-08-20');
  });
});
