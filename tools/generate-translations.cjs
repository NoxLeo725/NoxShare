const fs=require('node:fs'),path=require('node:path');
const assets=path.resolve(__dirname,'../app/src/main/assets');
const translations=JSON.parse(fs.readFileSync(path.join(assets,'translations.json'),'utf8'));
fs.writeFileSync(path.join(assets,'i18n-translations.js'),'window.NoxTranslations='+JSON.stringify(translations)+';\n');
console.log('Generated '+Object.keys(translations).length+' translations.');
