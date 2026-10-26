'use strict';
window.NoxI18n=(()=>{
 const english=window.NoxTranslations||{},escape=value=>value.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
 const pattern=new RegExp(Object.keys(english).sort((a,b)=>b.length-a.length).map(escape).join('|'),'g');
 const native=!!window.NoxNative;
 let preference='system',language='en';
 try{preference=native&&NoxNative.getLanguage?NoxNative.getLanguage():localStorage.getItem('noxshare.language')||'system'}catch{}
 const valid=value=>['system','zh','en'].includes(value)?value:'system';preference=valid(preference);
 function system(){try{if(native&&NoxNative.systemLanguage)return NoxNative.systemLanguage()}catch{}return navigator.language||'en'}
 function resolve(){return preference==='system'?(/^zh/i.test(system())?'zh':'en'):preference}
 language=resolve();
 function t(source){return language==='en'?String(source??'').replace(pattern,key=>english[key]):String(source??'')}
 const textNodes=[],attributes=[];
 const walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);
 while(walker.nextNode()){const node=walker.currentNode;if(!/^(SCRIPT|STYLE|TEXTAREA)$/.test(node.parentElement?.tagName||'')&&/[\u3400-\u9fff]/.test(node.nodeValue))textNodes.push({node,source:node.nodeValue})}
 document.querySelectorAll('*').forEach(node=>['aria-label','placeholder','title'].forEach(name=>{const source=node.getAttribute(name);if(source&&/[\u3400-\u9fff]/.test(source))attributes.push({node,name,source})}));
 function apply(){document.documentElement.lang=language==='zh'?'zh-CN':'en';textNodes.forEach(({node,source})=>{if(node.isConnected)node.nodeValue=t(source)});attributes.forEach(({node,name,source})=>{if(node.isConnected)node.setAttribute(name,t(source))});const select=document.getElementById('language-choice');if(select)select.value=preference}
 function notify(){apply();window.dispatchEvent(new Event('nox-language'))}
 function set(value){value=valid(value);if(native&&NoxNative.setLanguage){const error=NoxNative.setLanguage(value);if(error)throw Error(error)}else localStorage.setItem('noxshare.language',value);preference=value;language=resolve();notify()}
 function sync(value,resolved){const next=valid(value),nextLanguage=resolved==='zh'?'zh':'en';if(next!==preference||nextLanguage!==language){preference=next;language=nextLanguage;notify()}}
 function error(source){if(language!=='en')return String(source);if(Object.prototype.hasOwnProperty.call(english,source))return english[source];const match=String(source).match(/^(\d+) 个文件无法添加，请从本地存储重新选择$/);return match?match[1]+' file(s) could not be added. Choose them from local storage.':String(source)}
 apply();return{t,set,sync,error,apply,locale:()=>language==='zh'?'zh-CN':'en-US',language:()=>language,preference:()=>preference,ui:(parts,...values)=>parts.map((part,i)=>t(part)+(i<values.length?values[i]:'' )).join('')};
})();
const t=window.NoxI18n.t,ui=window.NoxI18n.ui;
