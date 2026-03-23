var fs = require('fs');
var path = require('path');

// Função para processar um HTML e inlinear CSS/JS
function processHtml(htmlPath, outputPath) {
  console.log(`🔄 Processando: ${htmlPath}`);
  
  if (!fs.existsSync(htmlPath)) {
    console.log(`❌ Arquivo não encontrado: ${htmlPath}`);
    return;
  }

  var htmlContent = fs.readFileSync(htmlPath, 'utf8');

  // Inline CSS
  var cssRegex = /<link[^>]*href=([^>\s]+\.css)[^>]*>/g;
  var cssMatch;
  while ((cssMatch = cssRegex.exec(htmlContent)) !== null) {
    var cssPath = cssMatch[1].replace(/['"]/g, '');
    
    // Converte caminho relativo para absoluto
    var fullCssPath;
    if (cssPath.startsWith('/src/')) {
      fullCssPath = path.join(__dirname, cssPath.substring(1));
    } else {
      fullCssPath = path.join(__dirname, 'dist', cssPath);
    }
    
    if (fs.existsSync(fullCssPath)) {
      var cssContent = fs.readFileSync(fullCssPath, 'utf8');
      htmlContent = htmlContent.split(cssMatch[0]).join('<style>' + cssContent + '</style>');
      console.log('✅ CSS inlined:', cssPath);
    }
  }

  // Inline JavaScript
  var jsRegex = /<script([^>]*)src=["']?([^"'\s>]+\.js)["']?([^>]*)><\/script>/g;
  var jsMatch;
  while ((jsMatch = jsRegex.exec(htmlContent)) !== null) {
    var beforeSrc = jsMatch[1];
    var jsPath = jsMatch[2];
    var afterSrc = jsMatch[3];
    
    var fullJsPath = path.join(__dirname, 'dist', jsPath);
    
    if (fs.existsSync(fullJsPath)) {
      var jsContent = fs.readFileSync(fullJsPath, 'utf8');
      
      // Reconstitute attributes, looking for type="module"
      var attributes = (beforeSrc + ' ' + afterSrc).trim();
      var isModule = attributes.includes('type="module"') || attributes.includes('type=module');
      
      var scriptTag = isModule ? '<script type="module">' : '<script>';
      var replacement = scriptTag + jsContent + '</script>';
      
      // Use split/join to avoid $ special characters in jsContent when using .replace()
      htmlContent = htmlContent.split(jsMatch[0]).join(replacement);
      
      if (fs.existsSync(fullJsPath)) {
        fs.unlinkSync(fullJsPath);
      }
      console.log('✅ JS inlined:', jsPath + (isModule ? ' (as module)' : ''));
    }
  }

  // Salva o HTML processado
  fs.writeFileSync(outputPath, htmlContent, 'utf8');
  console.log(`✅ HTML gerado: ${outputPath}`);
}

// Process index.html
console.log('🚀 Iniciando build unificado...');

var indexHtmlPath = path.join(__dirname, 'dist', 'index.html');
var indexOutputPath = path.join(__dirname, 'dist', 'index.html');
processHtml(indexHtmlPath, indexOutputPath);

// Create app_night.html and app_light.html copies for Android
var unifiedHtmlContent = fs.readFileSync(indexOutputPath, 'utf8');
fs.writeFileSync(path.join(__dirname, 'dist', 'app_night.html'), unifiedHtmlContent, 'utf8');
fs.writeFileSync(path.join(__dirname, 'dist', 'app_light.html'), unifiedHtmlContent, 'utf8');
console.log('✅ Cópias para Android criadas: app_night.html, app_light.html');

// Remove pasta assets vazia
var assetsDir = path.join(__dirname, 'dist', 'assets');
if (fs.existsSync(assetsDir)) {
  var files = fs.readdirSync(assetsDir);
  if (files.length === 0) {
    fs.rmdirSync(assetsDir);
    console.log('✅ Pasta assets removida');
  }
}

// Remove arquivos CSS originais
var cssFiles = ['night.style.css', 'light.style.css', 'style.css'];
cssFiles.forEach(function(cssFile) {
  var cssPath = path.join(__dirname, 'dist', cssFile);
  if (fs.existsSync(cssPath)) {
    fs.unlinkSync(cssPath);
    console.log(`✅ CSS removido: ${cssFile}`);
  }
});

console.log('🎉 Build completo! Arquivo gerado:');
console.log('  📄 index.html (unificado)');
