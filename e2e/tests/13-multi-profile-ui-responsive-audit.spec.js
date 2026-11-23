// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * Auditoría Visual, Funcional y Responsive Multi-Perfil (Light / Dark / 1280px / 375px / Reduced Motion)
 * Verifica que todas las vistas públicas y de cada rol rendericen sin errores de consola/runtime,
 * sin bloqueos 403/404 y sin desbordamiento horizontal.
 */

const USERS = {
  patient: {
    publicId: 'usr-pat-01',
    email: 'paciente@demo.meditriaje.test',
    nombreCompleto: 'Carlos Andrés Gómez',
    roles: ['ROLE_PACIENTE']
  },
  nursing: {
    publicId: 'usr-enf-01',
    email: 'enfermeria@demo.meditriaje.test',
    nombreCompleto: 'Enf. Soraya Jiménez',
    roles: ['ROLE_ENFERMERIA']
  },
  professional: {
    publicId: 'usr-prof-01',
    email: 'medico@demo.meditriaje.test',
    nombreCompleto: 'Dr. Roberto Mendoza',
    roles: ['ROLE_PROFESIONAL']
  },
  pharmacy: {
    publicId: 'usr-farm-01',
    email: 'farmacia@demo.meditriaje.test',
    nombreCompleto: 'QF. Valeria Ríos',
    roles: ['ROLE_FARMACEUTICO']
  },
  admin: {
    publicId: 'usr-admin-01',
    email: 'admin@demo.meditriaje.test',
    nombreCompleto: 'Director Administrativo',
    roles: ['ROLE_ADMINISTRADOR']
  }
};

async function setupApiMocks(page, userOrNull) {
  // Catch-all LIFO base para que ninguna petición escape al puerto 8080 sin mock
  await page.route('**/api/v1/**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: [], totalElements: 0, totalPages: 1 })
    });
  });

  await page.route('**/api/v1/auth/refresh', async (route) => {
    if (!userOrNull) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'No autenticado' })
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(userOrNull)
    });
  });

  // Pública: Resumen de emergencia
  await page.route('**/api/v1/emergency-summary/*/check', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ valido: true, requierePin: false })
    });
  });

  await page.route('**/api/v1/emergency-summary/*', async (route) => {
    if (route.request().url().endsWith('/check')) {
      await route.fallback();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        generadoAt: '2026-10-15T10:00:00Z',
        advertenciaLegal: 'Prototipo académico asistencial.',
        paciente: {
          nombreCompleto: 'Carlos Andrés Gómez',
          tipoDocumento: 'CC',
          numeroDocumento: '1065123456',
          grupoSanguineo: 'O+',
          tipoSangre: 'O+',
          telefono: '3001234567',
          contactoEmergenciaNombre: 'María Gómez',
          contactoEmergenciaTelefono: '3017654321'
        },
        alergias: [
          { sustancia: 'Penicilina', severidad: 'SEVERA', reaccion: 'Broncoespasmo y urticaria' }
        ],
        medicamentosActivos: [
          { nombre: 'Losartán 50mg', dosis: '1 tableta cada 24h' }
        ],
        atencionesRecientes: [
          { fecha: '2026-10-01T09:00:00Z', diagnostico: 'Hipertensión esencial controlada', profesional: 'Dr. Roberto Mendoza' }
        ]
      })
    });
  });

  // Paciente
  await page.route('**/api/v1/patients/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        publicId: 'pat-1',
        nombreCompleto: 'Carlos Andrés Gómez',
        email: 'paciente@demo.meditriaje.test',
        tipoDocumento: 'CC',
        numeroDocumento: '1065123456',
        epsNombre: 'EPS SURA'
      })
    });
  });

  await page.route('**/api/v1/patients/me/appointments*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            publicId: 'cita-pat-1',
            especialidadNombre: 'Medicina General',
            profesionalNombre: 'Dr. Roberto Mendoza',
            sedeNombre: 'Hospital Central',
            inicio: '2026-10-20T09:00:00Z',
            estado: 'PROGRAMADA',
            modalidad: 'PRESENCIAL'
          }
        ],
        totalElements: 1,
        totalPages: 1
      })
    });
  });

  await page.route('**/api/v1/patients/me/history*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            publicId: 'att-0',
            fechaInicio: '2026-10-01T09:00:00Z',
            especialidadNombre: 'Medicina General',
            profesionalNombre: 'Dr. Roberto Mendoza',
            diagnosticoCodigo: 'I10X',
            diagnosticoDescripcion: 'Hipertensión esencial',
            evolucion: 'Paciente estable con adherencia al tratamiento.'
          }
        ],
        totalElements: 1,
        totalPages: 1
      })
    });
  });

  await page.route('**/api/v1/patients/me/prescriptions*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            publicId: 'rx-pat-1',
            codigoReclamo: 'RX-2026-001',
            fechaEmision: '2026-10-01T09:30:00Z',
            profesionalNombre: 'Dr. Roberto Mendoza',
            estado: 'ACTIVA',
            items: [{ nombreMedicamento: 'Losartán 50mg', dosis: '1 cada 24h', cantidad: 30 }]
          }
        ],
        totalElements: 1,
        totalPages: 1
      })
    });
  });

  await page.route('**/api/v1/patients/me/follow-ups*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            publicId: 'fu-1',
            titulo: 'Control de presión arterial',
            indicaciones: 'Registrar presión arterial matutina por 7 días.',
            fechaLimite: '2026-10-25',
            estado: 'PENDIENTE'
          }
        ],
        totalElements: 1,
        totalPages: 1
      })
    });
  });

  await page.route('**/api/v1/patients/me/allergies*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { publicId: 'alg-1', sustancia: 'Penicilina', tipo: 'MEDICAMENTO', severidad: 'SEVERA', reaccion: 'Urticaria', fechaRegistro: '2025-05-10' }
      ])
    });
  });

  await page.route('**/api/v1/patients/me/emergency-qr*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([])
    });
  });

  await page.route('**/api/v1/triage/symptoms*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { publicId: 'sym-1', codigo: 'DOLOR_TORACICO', nombre: 'Dolor torácico', sistemaOrganico: 'CARDIOVASCULAR', prioridadBase: 2 },
        { publicId: 'sym-2', codigo: 'FIEBRE_ALTA', nombre: 'Fiebre alta (>39°C)', sistemaOrganico: 'GENERAL', prioridadBase: 3 }
      ])
    });
  });

  await page.route('**/api/v1/availability*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { publicId: 'slot-av-1', profesionalNombre: 'Dr. Roberto Mendoza', especialidadNombre: 'Medicina General', sedeNombre: 'Hospital Central', inicio: '2026-10-22T09:00:00Z', fin: '2026-10-22T09:30:00Z', modalidad: 'PRESENCIAL' }
      ])
    });
  });

  // Urgencias y Enfermería
  await page.route('**/api/v1/emergency/queue*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          episodioPublicId: 'ep-1',
          publicId: 'ep-1',
          pacienteNombre: 'Carlos Andrés Gómez',
          motivoConsulta: 'Dolor abdominal agudo',
          estado: 'EN_TRIAJE',
          nivelTriaje: 'III',
          fechaIngreso: '2026-10-15T10:00:00Z',
          esNn: false
        }
      ])
    });
  });

  await page.route('**/api/v1/emergency/episodes/ep-1/triage-assessments*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([])
    });
  });

  await page.route('**/api/v1/emergency/episodes/ep-1', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        episodioPublicId: 'ep-1',
        publicId: 'ep-1',
        pacientePublicId: 'pat-1',
        pacienteNombre: 'Carlos Andrés Gómez',
        pacienteNombreCompleto: 'Carlos Andrés Gómez',
        pacienteDocumento: 'CC 1065123456',
        sedeNombre: 'Hospital Central',
        motivoConsulta: 'Dolor abdominal agudo',
        estado: 'EN_TRIAJE',
        nivelTriaje: 'III',
        fechaIngreso: '2026-10-15T10:00:00Z',
        esNn: false
      })
    });
  });

  // Profesional Asistencial
  await page.route('**/api/v1/professionals/me/agenda*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            publicId: 'cita-1',
            pacientePublicId: 'pat-1',
            pacienteNombre: 'Carlos Andrés Gómez',
            especialidadNombre: 'Medicina General',
            estado: 'CONFIRMADA',
            inicio: '2026-10-15T09:00:00Z',
            fin: '2026-10-15T09:30:00Z',
            modalidad: 'PRESENCIAL',
            motivoConsulta: 'Control general',
            atencionPublicId: 'att-1'
          }
        ],
        totalElements: 1,
        totalPages: 1
      })
    });
  });

  await page.route('**/api/v1/attentions/att-1/follow-ups*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([])
    });
  });

  await page.route('**/api/v1/attentions/att-1', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        publicId: 'att-1',
        pacientePublicId: 'pat-1',
        pacienteNombre: 'Carlos Andrés Gómez',
        especialidadNombre: 'Medicina General',
        estado: 'ABIERTA',
        fechaInicio: '2026-10-15T09:05:00Z',
        motivoConsulta: 'Control general'
      })
    });
  });

  await page.route('**/api/v1/clinical/break-glass/active', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
  });

  await page.route('**/api/v1/clinical/patients/pat-1/history*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            publicId: 'att-0',
            fechaInicio: '2026-09-01T10:00:00Z',
            profesionalNombre: 'Dr. Roberto Mendoza',
            especialidadNombre: 'Medicina General',
            diagnosticoCodigo: 'J00',
            diagnosticoDescripcion: 'Rinofaringitis aguda',
            motivoConsulta: 'Congestión nasal',
            evolucion: 'Estable',
            indicaciones: 'Hidratación oral'
          }
        ],
        totalElements: 1,
        totalPages: 1
      })
    });
  });

  await page.route('**/api/v1/clinical/patients/pat-1/allergies*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{ publicId: 'alg-1', sustancia: 'Penicilina', severidad: 'SEVERA', tipo: 'MEDICAMENTO' }])
    });
  });

  // Farmacia
  await page.route('**/api/v1/pharmacy/prescriptions*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            recetaPublicId: 'rx-1',
            codigoReclamo: 'RX-2026-001',
            pacienteNombre: 'Carlos Andrés Gómez',
            pacienteDocumento: 'CC 1065123456',
            fechaEmision: '2026-10-15T10:00:00Z',
            estado: 'PENDIENTE',
            items: [{ nombreMedicamento: 'Acetaminofén 500mg', cantidadPrescrita: 10, cantidadDispensada: 0 }]
          }
        ],
        totalElements: 1,
        totalPages: 1
      })
    });
  });

  // Hospital y Centro de Mando
  await page.route('**/api/v1/hospital/census*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        totalCamas: 20,
        ocupadas: 12,
        disponibles: 6,
        enLimpieza: 1,
        enMantenimiento: 1,
        tasaOcupacion: 60.0,
        resumenPabellones: [
          { areaServicio: 'URGENCIAS', areaNombre: 'Urgencias Adultos', ocupadas: 6, total: 10, porcentajeOcupacion: 60.0 }
        ]
      })
    });
  });

  await page.route('**/api/v1/hospital/beds*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          publicId: 'bed-1',
          codigo: 'URG-01',
          codigoCama: 'URG-01',
          habitacionCodigo: '101',
          habitacion: '101',
          areaNombre: 'Urgencias Adultos',
          pabellon: 'Urgencias Adultos',
          areaServicio: 'URGENCIAS',
          estado: 'DISPONIBLE'
        }
      ])
    });
  });

  await page.route('**/api/v1/hospital/areas*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{ publicId: 'area-1', codigo: 'URG', nombre: 'Urgencias Adultos', tipo: 'URGENCIAS', habitaciones: [] }])
    });
  });

  await page.route('**/api/v1/hospital/episodes/ep-1/movements*', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
  });

  await page.route('**/api/v1/operational/dashboard*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        totalCamas: 20,
        camasOcupadas: 12,
        tasaOcupacionGlobal: 60.0,
        alertasActivasCount: 1,
        esperaPromMin: { I: 0, II: 8, III: 25, IV: 45, V: 60 },
        alertasActivas: []
      })
    });
  });

  await page.route('**/api/v1/operational/alerts*', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
  });

  // Administración y EPS
  await page.route('**/api/v1/admin/specialties*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: [{ publicId: 'spec-1', nombre: 'Medicina General', duracionSlotMin: 20, estado: 'ACTIVO' }], totalElements: 1, totalPages: 1 })
    });
  });

  await page.route('**/api/v1/admin/professionals*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [{ publicId: 'pro-1', nombres: 'Roberto', apellidos: 'Mendoza', tipoDocumento: 'CC', numeroDocumento: '1065123456', registroMedico: 'RM-54321', especialidadNombre: 'Medicina General', estado: 'ACTIVO' }],
        totalElements: 1,
        totalPages: 1
      })
    });
  });

  await page.route('**/api/v1/admin/slots*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [{ publicId: 'slot-1', profesionalNombre: 'Dr. Roberto Mendoza', sedeNombre: 'Hospital Central', modalidad: 'PRESENCIAL', inicio: '2026-10-15T08:00:00Z', fin: '2026-10-15T08:30:00Z', estado: 'LIBRE' }],
        totalElements: 1,
        totalPages: 1
      })
    });
  });

  await page.route('**/api/v1/admin/appointments*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [{ publicId: 'cita-adm-1', pacienteNombre: 'Carlos Andrés Gómez', profesionalNombre: 'Dr. Roberto Mendoza', especialidadNombre: 'Medicina General', inicio: '2026-10-15T09:00:00Z', fin: '2026-10-15T09:30:00Z', estado: 'PROGRAMADA', modalidad: 'PRESENCIAL', nivelTriaje: 'III' }],
        totalElements: 1,
        totalPages: 1
      })
    });
  });

  await page.route('**/api/v1/admin/reports/operational*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        citas: { totalCitas: 40, tasaCumplimiento: 90, tasaCancelacion: 5, porEspecialidad: { 'Medicina General': 30 }, porSede: { 'Hospital Central': 40 } },
        triaje: { totalTriajes: 15, emergencias: 2, tasaEmergencia: 13, nivel1: 2, nivel2: 3, nivel3: 5, nivel4: 3, nivel5: 2 },
        farmacia: { totalRecetas: 20, unidadesDispensadas: 50, dispensadasTotal: 18, dispensadasParcial: 2 },
        breakGlass: { totalActivaciones: 1, activacionesActivas: 0 }
      })
    });
  });

  await page.route('**/api/v1/admin/audit*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [{ id: 101, fechaHora: '2026-10-10T14:00:00Z', usuarioEmail: 'admin@demo.meditriaje.test', tipoEvento: 'LOGIN_EXITOSO', resultado: 'EXITO', ipOrigen: '127.0.0.1' }],
        totalElements: 1,
        totalPages: 1
      })
    });
  });

  await page.route('**/api/v1/affiliations/eps*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([{ publicId: 'eps-1', codigoMinSalud: 'EPS010', nombre: 'EPS SURA' }])
    });
  });
}

async function auditRoutes(page, routes, { viewport, theme = 'light', allowAuth401 = false }) {
  await page.setViewportSize(viewport);
  await page.evaluate((selectedTheme) => {
    localStorage.setItem('theme', selectedTheme);
    if (selectedTheme === 'dark') {
      document.documentElement.setAttribute('data-theme', 'dark');
    } else {
      document.documentElement.removeAttribute('data-theme');
    }
  }, theme).catch(() => {});
  await page.addInitScript((selectedTheme) => {
    localStorage.setItem('theme', selectedTheme);
  }, theme);

  const pageErrors = [];
  const consoleErrors = [];
  const onPageError = (err) => pageErrors.push(err.message);
  const onConsole = (msg) => {
    if (msg.type() !== 'error') return;
    const text = msg.text();
    if (allowAuth401 && text.includes('status of 401')) return;
    consoleErrors.push(text);
  };
  page.on('pageerror', onPageError);
  page.on('console', onConsole);

  try {
    for (const route of routes) {
      await page.goto(`/#${route}`);
      await page.waitForTimeout(280);

      const appText = await page.locator('#app').innerText();
      expect(appText.trim().length, `La ruta #${route} no renderizó contenido`).toBeGreaterThan(20);
      expect(appText, `La ruta #${route} devolvió 404`).not.toContain('Página no encontrada (404)');
      expect(appText, `La ruta #${route} devolvió 403`).not.toContain('Acceso Denegado (403)');

      const hasNoOverflow = await page.evaluate(() => {
        return document.documentElement.scrollWidth <= window.innerWidth + 5;
      });
      expect(hasNoOverflow, `Desbordamiento horizontal en #${route} (${viewport.width}px, tema ${theme})`).toBe(true);
    }

    expect(pageErrors, `Errores JS de página en tema ${theme}`).toEqual([]);
    expect(consoleErrors, `Errores de consola en tema ${theme}`).toEqual([]);
  } finally {
    page.off('pageerror', onPageError);
    page.off('console', onConsole);
  }
}

test.describe('Auditoría Multi-Perfil UI/UX, Responsive, Dark Mode y Reduced Motion', () => {
  test('1. Vistas públicas renderizan limpiamente en desktop (1280px) y móvil (375px)', async ({ page }) => {
    await setupApiMocks(page, null);
    const routes = ['/', '/login', '/register', '/emergency-summary/demo-token-123'];
    await auditRoutes(page, routes, { viewport: { width: 1280, height: 800 }, allowAuth401: true });
    await auditRoutes(page, routes, { viewport: { width: 375, height: 812 }, allowAuth401: true });
  });

  test('2. Perfil Paciente: todas sus vistas adaptan sin desbordamiento en desktop y móvil', async ({ page }) => {
    await setupApiMocks(page, USERS.patient);
    const routes = [
      '/patient/dashboard',
      '/patient/triage',
      '/patient/book',
      '/patient/appointments',
      '/patient/history',
      '/patient/prescriptions',
      '/patient/emergency-qr',
      '/patient/allergies',
      '/patient/follow-ups'
    ];
    await auditRoutes(page, routes, { viewport: { width: 1280, height: 800 } });
    await auditRoutes(page, routes, { viewport: { width: 375, height: 812 } });
  });

  test('3. Perfil Enfermería: Urgencias, Admisión, Valoración e Identidad en desktop y móvil', async ({ page }) => {
    await setupApiMocks(page, USERS.nursing);
    const routes = [
      '/nursing/dashboard',
      '/nursing/admission',
      '/nursing/assessment/ep-1',
      '/nursing/identity/ep-1'
    ];
    await auditRoutes(page, routes, { viewport: { width: 1280, height: 800 } });
    await auditRoutes(page, routes, { viewport: { width: 375, height: 812 } });
  });

  test('4. Perfil Profesional: Agenda, Atención, Receta, Historial y Egreso en desktop y móvil', async ({ page }) => {
    await setupApiMocks(page, USERS.professional);
    const routes = [
      '/professional/agenda',
      '/professional/attention/att-1',
      '/professional/prescription/att-1',
      '/professional/patient-history/pat-1',
      '/hospital/discharge/ep-1'
    ];
    await auditRoutes(page, routes, { viewport: { width: 1280, height: 800 } });
    await auditRoutes(page, routes, { viewport: { width: 375, height: 812 } });
  });

  test('5a. Perfil Farmacia: ventanilla de dispensación en desktop y móvil', async ({ page }) => {
    await setupApiMocks(page, USERS.pharmacy);
    await auditRoutes(page, ['/pharmacy/dispensation'], { viewport: { width: 1280, height: 800 } });
    await auditRoutes(page, ['/pharmacy/dispensation'], { viewport: { width: 375, height: 812 } });
  });

  test('5b. Hospitalización, Centro de Mando y Admin/EPS en desktop y móvil', async ({ page }) => {
    await setupApiMocks(page, USERS.admin);
    const adminRoutes = [
      '/hospital/census',
      '/hospital/beds/ep-1',
      '/operational/dashboard',
      '/admin/dashboard',
      '/admin/appointments',
      '/admin/reports',
      '/admin/audit',
      '/affiliations/import',
      '/affiliations/search'
    ];
    await auditRoutes(page, adminRoutes, { viewport: { width: 1280, height: 800 } });
    await auditRoutes(page, adminRoutes, { viewport: { width: 375, height: 812 } });
  });

  test('6. Dark mode multi-perfil en 375px y prefers-reduced-motion', async ({ page, browser }) => {
    await setupApiMocks(page, USERS.admin);
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto('/#/admin/dashboard');

    const themeBtn = page.locator('#themeToggleBtn');
    await expect(themeBtn).toBeVisible();
    await themeBtn.click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

    await auditRoutes(
      page,
      ['/hospital/census', '/operational/dashboard', '/admin/dashboard', '/affiliations/search'],
      { viewport: { width: 375, height: 812 }, theme: 'dark' }
    );

    await page.emulateMedia({ reducedMotion: 'reduce' });
    await auditRoutes(
      page,
      ['/operational/dashboard', '/admin/reports'],
      { viewport: { width: 375, height: 812 }, theme: 'dark' }
    );

    // Auditar también Paciente y Enfermería en Dark Mode 375px + reduced motion
    const patientCtx = await browser.newContext({ viewport: { width: 375, height: 812 }, reducedMotion: 'reduce' });
    try {
      const pPage = await patientCtx.newPage();
      await setupApiMocks(pPage, USERS.patient);
      await auditRoutes(pPage, ['/patient/dashboard', '/patient/triage', '/patient/prescriptions'], { viewport: { width: 375, height: 812 }, theme: 'dark' });
    } finally {
      await patientCtx.close();
    }

    const nursingCtx = await browser.newContext({ viewport: { width: 375, height: 812 } });
    try {
      const nPage = await nursingCtx.newPage();
      await setupApiMocks(nPage, USERS.nursing);
      await auditRoutes(nPage, ['/nursing/dashboard', '/nursing/admission'], { viewport: { width: 375, height: 812 }, theme: 'dark' });
    } finally {
      await nursingCtx.close();
    }
  });
});
