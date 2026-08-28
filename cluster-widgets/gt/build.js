/*
 * Build do tema GT.
 *
 * O tema e um unico HTML sem dependencias (canvas + DOM puro), entao o build
 * so precisa embutir o CSS das fontes e escrever o arquivo final em
 * ../Themes/GT/index.html — que e o que o ThemeManager baixa para o carro.
 *
 *   node build.js
 */
var fs = require('fs');
var path = require('path');

var SRC = path.join(__dirname, 'index.html');
var OUT_DIR = path.join(__dirname, '..', 'Themes', 'GT');
var OUT = path.join(OUT_DIR, 'index.html');

var html = fs.readFileSync(SRC, 'utf8');

// inline de <link rel="stylesheet" href="...css">
var linkRe = /<link[^>]*href=["']?([^"'\s>]+\.css)["']?[^>]*>/gi;
var m, count = 0;
while ((m = linkRe.exec(html)) !== null) {
  var cssPath = path.join(__dirname, m[1].replace(/^\.\//, ''));
  if (!fs.existsSync(cssPath)) {
    console.error('CSS nao encontrado: ' + cssPath);
    process.exit(1);
  }
  html = html.split(m[0]).join('<style>' + fs.readFileSync(cssPath, 'utf8') + '</style>');
  count++;
  linkRe.lastIndex = 0;
}

if (html.indexOf('<link') !== -1 && /<link[^>]*\.css/i.test(html)) {
  console.error('Sobrou <link> de CSS no HTML final — o tema precisa ser self-contained.');
  process.exit(1);
}

if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });
fs.writeFileSync(OUT, html, 'utf8');

console.log('CSS embutido: ' + count);
console.log('Gerado: ' + OUT + ' (' + (html.length / 1024).toFixed(1) + ' KB)');
console.log('Lembrete: ao publicar, bumpar a versao em Themes/GT/theme.xml E em Themes/themes.json.');
