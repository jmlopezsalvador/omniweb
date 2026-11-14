// Compila el motor a un único .js (IIFE) y copia la página de pruebas a dist/. dist/ se versiona.
import { build } from 'esbuild';
import { cpSync, mkdirSync } from 'node:fs';

mkdirSync('dist/test', { recursive: true });
await build({
  entryPoints: ['src/index.ts'],
  bundle: true,
  format: 'iife',
  target: 'es2019', // WebViews antiguos (Android 8+, iOS 15)
  minify: true,
  outfile: 'dist/omniweb.js',
});
cpSync('test/page.html', 'dist/test/page.html');
console.log('OK dist/omniweb.js');
