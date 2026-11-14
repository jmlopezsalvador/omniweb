// Herramienta de depuración: evalúa JS en el WebView de la app que corre en el emulador, vía CDP (sin Playwright:
// no soporta WebView). Uso (desde web/):  node tools/cdp.mjs "<expresión JS>"   → imprime el resultado como JSON.
// Requiere la app abierta en modo debug (WebView.setWebContentsDebuggingEnabled(true)) y adb (variable ADB para cambiar la ruta).
import { execFileSync } from 'node:child_process';

const ADB = process.env.ADB || 'C:/Users/jmlop/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const sh = (...a) => execFileSync(ADB, a, { encoding: 'utf8' });

const pid = sh('shell', 'pidof', 'com.omniweb').trim().split(/\s+/)[0];
if (!pid) throw new Error('com.omniweb no está en ejecución: abre la app en el emulador');
sh('forward', 'tcp:9222', `localabstract:webview_devtools_remote_${pid}`);

const pages = (await (await fetch('http://127.0.0.1:9222/json')).json()).filter((p) => p.type === 'page');
if (!pages.length) throw new Error('El WebView no tiene páginas');
const ws = new WebSocket(pages[0].webSocketDebuggerUrl);
await new Promise((res, rej) => ((ws.onopen = res), (ws.onerror = rej)));

const r = await new Promise((res) => {
  ws.onmessage = (m) => {
    const d = JSON.parse(m.data);
    if (d.id === 1) res(d);
  };
  ws.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression: process.argv[2], returnByValue: true, awaitPromise: true } }));
});
ws.close();
if (r.result?.exceptionDetails) console.error('Error JS:', r.result.exceptionDetails.exception?.description ?? r.result.exceptionDetails.text);
const v = r.result?.result?.value;
console.log(typeof v === 'string' ? v : JSON.stringify(v, null, 2));
