'use strict';

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const PORT = Number(process.env.PORT || 3000);
const API_HOST = process.env.API_HOST || '127.0.0.1';
const API_PORT = Number(process.env.API_PORT || 8080);
const FRONTEND_DIR = path.resolve(__dirname, '..', 'frontend');

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
};

/**
 * Reenvía solicitudes /api/* y /actuator/* al backend local preservando método, cabeceras y cookies.
 */
function proxyToBackend(req, res) {
  const proxyReq = http.request(
    {
      hostname: API_HOST,
      port: API_PORT,
      path: req.url,
      method: req.method,
      headers: { ...req.headers, host: `${API_HOST}:${API_PORT}` },
    },
    (proxyRes) => {
      res.writeHead(proxyRes.statusCode || 502, proxyRes.headers);
      proxyRes.pipe(res);
    }
  );

  proxyReq.on('error', (err) => {
    if (!res.headersSent) {
      res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    }
    res.end(JSON.stringify({ codigo: 'BAD_GATEWAY', mensaje: err.message }));
  });

  req.pipe(proxyReq);
}

/**
 * Resuelve la ruta estática segura dentro de frontend/ o retorna index.html como fallback SPA.
 */
function serveStatic(req, res) {
  const cleanUrl = (req.url || '/').split('?')[0];
  let decodedPath;
  try {
    decodedPath = decodeURIComponent(cleanUrl);
  } catch {
    res.writeHead(400);
    res.end('Bad Request');
    return;
  }

  const relativePath = decodedPath === '/' ? 'index.html' : decodedPath.replace(/^\/+/, '');
  let targetPath = path.resolve(FRONTEND_DIR, relativePath);

  if (targetPath !== FRONTEND_DIR && !targetPath.startsWith(FRONTEND_DIR + path.sep)) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }

  if (!fs.existsSync(targetPath) || fs.statSync(targetPath).isDirectory()) {
    targetPath = path.join(FRONTEND_DIR, 'index.html');
  }

  const ext = path.extname(targetPath).toLowerCase();
  const contentType = MIME_TYPES[ext] || 'application/octet-stream';
  res.writeHead(200, { 'Content-Type': contentType });
  fs.createReadStream(targetPath).pipe(res);
}

const server = http.createServer((req, res) => {
  const urlPath = (req.url || '/').split('?')[0];
  if (urlPath.startsWith('/api/') || urlPath.startsWith('/actuator/')) {
    proxyToBackend(req, res);
    return;
  }
  serveStatic(req, res);
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`E2E server listening on http://127.0.0.1:${PORT} -> API http://${API_HOST}:${API_PORT}`);
});
