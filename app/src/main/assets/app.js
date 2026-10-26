'use strict';
const brandLogo="<svg class=\"brandmark\" viewBox=\"0 0 108 108\" aria-hidden=\"true\"><path class=\"icon-bg\" d=\"M0,0h108v108h-108z\"/><path class=\"icon-n\" d=\"M27,66V39c0,-7 8,-11 14,-7l26,17V32h14v37c0,7 -8,11 -14,7L41,59v17H27z\"/></svg>";
const paths={settings:'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1z',more:'M5 12h.01M12 12h.01M19 12h.01',wifi:'M2 8a16 16 0 0 1 20 0M5 12a11 11 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 20h.01',folder:'M3 6h7l2 2h9v12H3zM3 6V4h6l2 2',upload:'M12 16V4m-5 5 5-5 5 5M3 16v5h18v-5',download:'M12 3v12m-5-5 5 5 5-5M3 15v6h18v-6',help:'M9 9a3 3 0 0 1 6 1c0 2-3 2-3 4m0 3h.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',chevron:'m7 10 5 5 5-5',power:'M12 2v10M5 5a9 9 0 1 0 14 0',copy:'M9 9h12v12H9zM15 5V3H3v12h2',qr:'M3 3h6v6H3zM15 3h6v6h-6zM3 15h6v6H3zM15 15h2v2h4v4h-6v-2',shield:'M12 2 3 6v6c0 5 9 10 9 10s9-5 9-10V6zM8 12l3 3 5-6','plus-circle':'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0M12 7v10M7 12h10',image:'M3 3h18v18H3zM3 17l5-5 4 4 3-3 6 6M15 7h.01',file:'M14 2H5v20h14V7zM14 2v6h5M8 12h8M8 16h6',video:'M3 4h18v16H3zM10 8l6 4-6 4z',close:'m6 6 12 12M18 6 6 18',search:'M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',trash:'M3 6h18M8 6V3h8v3M5 6l1 15h12l1-15M10 10v7M14 10v7'};
const $=id=>document.getElementById(id),icon=n=>`<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${paths[n]||paths.file}"/></svg>`,esc=v=>String(v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
document.querySelectorAll('[data-icon]').forEach(e=>e.innerHTML=icon(e.dataset.icon));
const native=!!window.NoxNative,params=new URLSearchParams(location.search),preview=params.has('preview'),admin=native||params.get('preview')==='admin';
let state={running:false,files:[],messages:[],urls:[],network:{}},online=true,refreshing=false,sending=false,uploading=false,cancelled=false,currentUpload=null,busy=false,expanded=true,tray=false,fileTab='all',chatVersion='',infoKind='',forceBottom=true,toastTimer,confirmFn;
const selection=new Set(),blobs=new Map(),blobUrls=new Map();
const makeId=()=>window.crypto&&crypto.randomUUID?crypto.randomUUID():Date.now().toString(36)+Math.random().toString(36).slice(2);
const owner=f=>f.uploader||'主机',myName=()=>admin?'主机':state.clientIp||'访客设备';
const ownFile=f=>owner(f)===myName();
const displayName=value=>value==='主机'||value==='访客设备'?t(value):value;
const formatSize=n=>{if(n<0)returnt('未知大小');if(!n)return'0 B';const units=['B','KB','MB','GB','TB'],i=Math.min(4,Math.floor(Math.log(n)/Math.log(1024)));return(n/1024**i).toFixed(i?1:0)+' '+units[i]};
function kind(f){const mime=f.type||'',ext=f.name.split('.').pop().toLowerCase();if(mime.startsWith('image/')||/^(png|jpg|jpeg|webp|gif|heic|heif|avif|bmp)$/.test(ext))return'image';if(mime.startsWith('video/')||/^(mp4|m4v|mov|mkv|avi|webm|3gp|ogv)$/.test(ext))return'video';return'file'}
const clock=t=>new Date(t).toLocaleTimeString(NoxI18n.locale(),{hour:'2-digit',minute:'2-digit'});
function toast(text){$('toast').textContent=NoxI18n.error(text);$('toast').classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').classList.remove('visible'),3500)}
async function request(url,options={},timeout=5000){const controller=typeof AbortController==='function'?new AbortController():null;let timer;try{return await Promise.race([fetch(url,Object.assign({},options,controller?{signal:controller.signal}:{})),new Promise((_,reject)=>{timer=setTimeout(()=>{if(controller)controller.abort();reject(Error(t('连接超时')))},timeout)})])}finally{clearTimeout(timer)}}
function setExpanded(value){expanded=value;$('sharing-body').hidden=!value;$('sharing-heading').setAttribute('aria-expanded',String(value))}
function render(){
 $('menu-settings').hidden=false;document.querySelector('.host-settings').hidden=!admin;if($('settings-dialog').open){$('clear-on-stop').checked=!!state.clearOnStop;renderPermissions()}
 document.body.classList.toggle('visitor',!admin);document.body.classList.toggle('offline',!online);
 $('connection').textContent=state.running&&online?t('局域网 · 已连接'):admin?t('局域网共享'):t('正在尝试连接');$('identity').textContent=displayName(myName());
 $('sharing-title').textContent=state.running&&online?t('正在共享'):admin?t('开启共享'):t('共享未连接');$('sharing-dot').classList.toggle('live',state.running&&online);$('sharing-summary').textContent=expanded?(admin?t('文件与文字互传'):''):(state.running?(state.urls[0]||'').replace(/^http:\/\//,''):t('点击展开'));
 $('toggle').hidden=!admin;$('toggle').disabled=busy;$('toggle').classList.toggle('on',state.running);$('toggle').innerHTML=icon('power')+(state.running?t('关闭共享'):t('开启共享'));
 $('sharing-description').hidden=!admin;$('address-row').hidden=!admin||!state.running;$('address').textContent=state.urls[0]?state.urls[0].replace(/^http:\/\//,'').replace(/\/$/,''):t('请连接 Wi-Fi');$('copy-address').disabled=!state.urls.length;$('qr').disabled=!state.urls.length;
 $('visitor-sharing').hidden=admin;$('visitor-sharing').children[1].textContent=t('主机地址');$('visitor-address').textContent=preview?'192.168.1.108:8787':location.host;
 $('menu-count').textContent=state.files.length;$('composer-status').textContent=state.running&&online?(uploading?t('正在上传文件…'):t('文字、图片、文件均可发送')):t('开启共享后可发送文字');$('send').disabled=sending||!state.running||!online||!$('message-input').value.trim();
 $('pick-file').disabled=uploading||(!admin&&(!online||!state.running));$('pick-image').disabled=$('pick-file').disabled;
 if($('file-dialog').open)renderFiles();if(infoKind==='network'&&$('info-dialog').open)$('info-content').innerHTML=networkHtml();renderChat();
}
function previewUrl(f){if(preview){if(blobUrls.has(f.id))return blobUrls.get(f.id);return f.preview||''}return native?'https://noxshare.local/preview/'+encodeURIComponent(f.id):'preview/'+encodeURIComponent(f.id)}
function linkifyText(value){
 const text=String(value),pattern=/(?:https?:\/\/|www\.)[^\s<>"'，。！？；、（）【】《》“”‘’]+|(?:\d{1,3}\.){3}\d{1,3}:\d{1,5}(?:\/[^\s<>"'，。！？；、（）【】《》“”‘’]*)?/gi;
 let out='',last=0;
 for(const match of text.matchAll(pattern)){
  if(match.index>0&&/[\w@]/.test(text[match.index-1]))continue;
  let label=match[0].replace(/[.,!?;:]+$/,'');
  for(const [open,close] of [['(',')'],['[',']'],['{','}']])while(label.endsWith(close)&&label.split(close).length>label.split(open).length)label=label.slice(0,-1);
  let url;try{url=new URL(/^https?:/i.test(label)?label:'http://'+label);if(!['http:','https:'].includes(url.protocol)||!url.hostname)continue}catch{continue}
  out+=esc(text.slice(last,match.index))+'<a href="'+esc(url.href)+'" data-web-link="'+esc(url.href)+'" target="_blank" rel="noopener noreferrer">'+esc(label)+'</a>';last=match.index+label.length;
 }
 return out+esc(text.slice(last));
}
function renderChat(){
 const entries=state.files.map(f=>({id:'f'+f.id,time:f.added,sender:owner(f),own:ownFile(f),file:f})).concat(state.messages.map(m=>({id:'m'+m.id,time:m.time,sender:m.from==='host'?'主机':m.sender||'访客设备',own:admin?m.from==='host':m.sender===state.clientIp,message:m}))).sort((a,b)=>a.time-b.time||a.id.localeCompare(b.id));
 const version=JSON.stringify(entries);if(version===chatVersion)return;chatVersion=version;
 const chat=$('chat'),atBottom=chat.scrollHeight-chat.scrollTop-chat.clientHeight<100||forceBottom;
 if(!entries.length){$('timeline').innerHTML='<div class="chat-start"><div class="start-logo">'+brandLogo+t('</div><h2>NoxShare</h2><p>发送文字、图片或文件</p></div>');return}
 let previous=0;
 $('timeline').innerHTML=entries.map(e=>{
  const divider=e.time-previous>5*60000?`<div class="time-divider">${new Date(e.time).toLocaleDateString(NoxI18n.locale(),{month:'long',day:'numeric'})} ${clock(e.time)}</div>`:'';previous=e.time;
  let bubble;
  if(e.file){const f=e.file,url=previewUrl(f),action=admin?t('保存'):t('下载');
   if(kind(f)==='image'&&url)bubble=ui`<div class="bubble bubble-image" data-file-id="${esc(f.id)}"><button class="image-preview-button" data-media="${esc(f.id)}" aria-label="预览 ${esc(f.name)}"><img src="${esc(url)}" alt="${esc(f.name)}" loading="lazy" data-image="${esc(f.id)}"></button><div class="image-caption"><span title="${esc(f.name)}">${esc(f.name)}</span><button data-file="${esc(f.id)}">${action}</button></div></div>`;
   else bubble=`<div class="bubble bubble-file" data-file-id="${esc(f.id)}"><div class="file-card-main" ${kind(f)!=='file'||native?`role="button" tabindex="0" ${kind(f)==='file'?'data-open':'data-media'}="${esc(f.id)}" aria-label="${kind(f)==='file'?t('打开'):t('预览')} ${esc(f.name)}"`:''}><span class="file-glyph ${kind(f)}">${icon(kind(f))}</span><div><div class="file-card-name" title="${esc(f.name)}">${esc(f.name)}</div><small>${formatSize(f.size)}</small></div></div><div class="file-card-footer"><span>${kind(f)==='image'?t('图片 · 点击预览'):kind(f)==='video'?t('视频 · 点击播放'):t('文件')}</span><button data-file="${esc(f.id)}">${action}</button></div></div>`;
  }else bubble=ui`<div class="bubble"><p class="bubble-text" data-message-id="${esc(e.message.id)}">${linkifyText(e.message.text)}</p></div><button class="copy-message" data-copy="${esc(e.message.id)}">复制</button>`;
  return divider+`<article class="message-row ${e.own?'own':''}" data-entry="${esc(e.id)}"><div class="avatar ${e.sender==='主机'?'host':''}">${e.sender==='主机'?brandLogo:esc(e.sender.split('.').pop().slice(-3))}</div><div class="message-stack"><div class="sender-line"><span>${esc(displayName(e.sender))}</span><time>${clock(e.time)}</time></div>${bubble}</div></article>`;
 }).join('');
 $('timeline').querySelectorAll('img').forEach(img=>{img.onerror=()=>{const text=document.createElement('div');text.className='preview-placeholder';text.textContent=t('图片 · 可保存原图');img.replaceWith(text)};img.onload=()=>{if(atBottom)chat.scrollTop=chat.scrollHeight}});
 if(atBottom){requestAnimationFrame(()=>chat.scrollTop=chat.scrollHeight);$('new-messages').hidden=true}else $('new-messages').hidden=false;forceBottom=false;
}
async function refresh(){if(refreshing)return;refreshing=true;try{if(native){state=JSON.parse(NoxNative.state());if(state.language)NoxI18n.sync(state.language,state.resolvedLanguage);online=true}else if(!preview){const r=await request('api/state',{cache:'no-store'});if(!r.ok)throw Error();state=await r.json();online=true}render()}catch{online=false;state.messages=[];render()}finally{refreshing=false}}
async function copy(text){try{if(native)NoxNative.copy(text);else if(navigator.clipboard&&window.isSecureContext)await navigator.clipboard.writeText(text);else{const input=document.createElement('textarea');input.value=text;input.style.position='fixed';input.style.opacity='0';document.body.append(input);input.select();const copied=document.execCommand('copy');input.remove();if(!copied)throw Error()}toast(t('已复制'))}catch{toast(t('请长按文字进行复制'))}}
function fileAction(id){const f=state.files.find(x=>x.id===id);if(!f)return toast(t('文件已移除'));if(native){NoxNative.saveFile(id);return}if(preview){const blob=blobs.get(id);if(!blob)return toast(t('示例文件仅供预览'));const a=document.createElement('a');a.href=blobUrls.get(id);a.download=f.name;a.click();return}const a=document.createElement('a');a.href='download/'+encodeURIComponent(id);a.download=f.name;a.click()}
function info(title,html,kind=''){$('info-title').textContent=title;$('info-content').innerHTML=html;infoKind=kind;$('info-dialog').showModal()}
function networkHtml(){const n=state.network||{},rows=[[t('连接状态'),admin?(n.connected?t('已连接'):t('未连接')):(online?t('已连接主机'):t('连接中断'))],[t('当前身份'),displayName(myName())],[t('主机 IP'),n.ip||'—'],[t('网关'),n.gateway||'—'],[t('网络类型'),t(n.kind||'—')],[t('子网前缀'),n.prefix&&n.prefix!=='—'?'/'+n.prefix:'—'],[t('共享端口'),String(n.port||8787)],[t('服务自检'),t(state.serviceCheck)||(preview?t('界面预览'):'—')]];return '<div class="network-rows">'+rows.map(([k,v])=>'<div><span>'+k+'</span><b>'+esc(v)+'</b></div>').join('')+'</div>'}
function listFiles(){const q=$('file-search').value.trim().toLowerCase();return state.files.filter(f=>(fileTab==='all'||(fileTab==='sent'?ownFile(f):!ownFile(f)))&&(!q||(f.name+' '+owner(f)+' '+displayName(owner(f))).toLowerCase().includes(q))).sort((a,b)=>b.added-a.added)}
function openFiles(tab){fileTab=tab;selection.clear();$('file-search').value='';renderFiles();$('file-dialog').showModal()}
function renderFiles(){
 const list=listFiles(),ids=new Set(state.files.map(f=>f.id));selection.forEach(id=>{if(!ids.has(id))selection.delete(id)});
 document.querySelectorAll('[data-file-tab]').forEach(b=>b.classList.toggle('active',b.dataset.fileTab===fileTab));$('file-count').textContent=list.length;$('selection-label').textContent=selection.size?t('已选 ')+selection.size:t('全选');
 $('select-all').checked=!!list.length&&list.every(f=>selection.has(f.id));$('select-all').indeterminate=list.some(f=>selection.has(f.id))&&!$('select-all').checked;$('select-all').disabled=!list.length;
 $('batch-download').disabled=!selection.size||(!admin&&!online);$('batch-download').textContent=admin?t('保存所选'):t('打包下载');$('batch-download').hidden=admin;
 $('batch-remove').hidden=!admin;$('batch-remove').disabled=!selection.size;$('clear').hidden=!admin;$('clear').disabled=!state.files.length;
 $('file-list').innerHTML=list.length?list.map(f=>ui`<div class="file-row" data-file-id="${esc(f.id)}"><div class="file-main"><input type="checkbox" data-select="${esc(f.id)}" ${selection.has(f.id)?'checked':''} aria-label="选择 ${esc(f.name)}"><span class="file-glyph ${kind(f)}">${icon(kind(f))}</span><div class="file-name"><b title="${esc(f.name)}" ${kind(f)!=='file'||native?`role="button" tabindex="0" ${kind(f)==='file'?'data-open':'data-media'}="${esc(f.id)}" aria-label="${kind(f)==='file'?t('打开'):t('预览')} ${esc(f.name)}"`:''}>${esc(f.name)}</b><small>${formatSize(f.size)} · ${new Date(f.added).toLocaleDateString(NoxI18n.locale())}</small><span class="mobile-owner">上传者：${esc(displayName(owner(f)))}</span></div></div><div class="file-owner"><span title="上传者：${esc(displayName(owner(f)))}">${esc(displayName(owner(f)))}</span></div><div class="file-actions">${native?ui`<button data-open="${esc(f.id)}" aria-label="用默认应用打开 ${esc(f.name)}">${icon('file')}</button>`:''}<button data-file="${esc(f.id)}" aria-label="${admin?t('保存'):t('下载')} ${esc(f.name)}">${icon('download')}</button>${admin?ui`<button data-remove="${esc(f.id)}" aria-label="移除 ${esc(f.name)}">${icon('trash')}</button>`:''}</div></div>`).join(''):t('<div class="files-empty">暂无文件</div>');
 $('file-total').textContent=list.length+t(' 个文件 · ')+formatSize(list.reduce((sum,f)=>sum+Math.max(0,f.size),0));$('file-note').textContent=admin?t('主机原文件保留 · 接收副本可另存'):t('上传者以实际连接 IP 标记');
}
function confirmRemove(ids){if(!admin||$('confirm-dialog').open)return;ids=ids.filter(id=>state.files.some(f=>f.id===id));if(!ids.length)return;$('confirm-title').textContent=t('移除 ')+ids.length+t(' 个文件？');$('confirm-text').textContent=state.files.some(f=>ids.includes(f.id)&&f.uploaded)?t('接收副本会一并清理；主机选取的原文件及上传设备原文件保留。'):t('只移除共享记录，主机原文件保留。');confirmFn=async()=>{if(native){const error=NoxNative.remove(JSON.stringify(ids));if(error)return toast(error)}else{state.files=state.files.filter(f=>!ids.includes(f.id));ids.forEach(id=>{if(blobUrls.has(id)){URL.revokeObjectURL(blobUrls.get(id));blobUrls.delete(id);blobs.delete(id)}})}ids.forEach(id=>selection.delete(id));await refresh();toast(t('已移除共享记录'))};$('confirm-dialog').showModal()}
function chooseFiles(images){if(uploading)return;if(!admin&&(!online||!state.running))return toast(t('请先连接主机'));tray=false;$('attachment-tray').hidden=true;$('attach').setAttribute('aria-expanded','false');if(native){images?NoxNative.pickImages():NoxNative.pick()}else $(images?'image-input':'file-input').click()}
function sendFile(file,index,total){return new Promise((resolve,reject)=>{const xhr=new XMLHttpRequest();currentUpload=xhr;xhr.open('POST','api/upload');xhr.setRequestHeader('X-NoxShare','1');xhr.setRequestHeader('X-File-Name',encodeURIComponent(file.name));xhr.setRequestHeader('Content-Type',file.type||'application/octet-stream');$('upload-progress').value=0;$('upload-label').textContent=t('上传 ')+index+'/'+total+' · '+file.name;
 xhr.upload.onprogress=e=>{if(e.lengthComputable){const percent=Math.round(e.loaded/e.total*100);$('upload-progress').value=percent;$('upload-label').textContent=percent===100?t('主机正在保存 · ')+file.name:t('上传 ')+index+'/'+total+' · '+file.name+' · '+percent+'%'}};
 xhr.onload=()=>xhr.status===201?resolve():reject(Error(xhr.responseText||t('上传失败，请重试')));xhr.onerror=()=>reject(Error(t('连接中断，请确认主机仍在共享')));xhr.onabort=()=>reject(Error(t('已取消上传')));xhr.send(file);
})}
async function uploadChosen(e){const chosen=Array.from(e.target.files);e.target.value='';await uploadFiles(chosen)}
async function uploadFiles(chosen){if(!chosen.length)return;if(uploading)return toast(t('正在上传，请完成后再添加文件'));forceBottom=true;
 if(preview){chosen.forEach(f=>{const id=makeId();blobs.set(id,f);blobUrls.set(id,URL.createObjectURL(f));state.files.push({id,name:f.name,size:f.size,type:f.type,added:Date.now(),uploader:myName(),uploaded:!admin})});render();return}
 if(!state.running||!online)return toast(t('共享未连接'));uploading=true;cancelled=false;$('upload-status').hidden=false;$('cancel-upload').hidden=false;render();let done=0,failed=0,error='';
 for(let i=0;i<chosen.length;i++){if(cancelled)break;try{if(chosen[i].size>2147483648)throw Error(t('单个文件最大 2 GB'));await sendFile(chosen[i],i+1,chosen.length);done++;forceBottom=true;await refresh()}catch(e){error=e.message;if(!cancelled)failed++}finally{currentUpload=null}}
 uploading=false;$('cancel-upload').hidden=true;$('upload-label').textContent=cancelled?t('已取消 · 完成 ')+done+t(' 个'):failed?t('完成 ')+done+t(' 个，失败 ')+failed+t(' 个 · ')+error:t('已上传 ')+done+t(' 个文件');if(!failed&&!cancelled)$('upload-progress').value=100;render();await refresh();if(!failed&&!cancelled)setTimeout(()=>{if(!uploading)$('upload-status').hidden=true},2200);else toast($('upload-label').textContent);
}
$('sharing-heading').onclick=()=>setExpanded(!expanded);
$('toggle').onclick=()=>{if(busy)return;busy=true;render();if(native){NoxNative.toggle();setTimeout(()=>{busy=false;refresh()},800)}else{state.running=!state.running;if(!state.running){state.messages=[];if(state.clearOnStop){state.files=[];blobUrls.forEach(url=>URL.revokeObjectURL(url));blobUrls.clear();blobs.clear();selection.clear();if($('media-dialog').open)$('media-dialog').close()}}busy=false;render();toast(state.running?t('预览：正在共享'):t('预览：共享已关闭'))}};
$('copy-address').onclick=()=>copy(state.urls[0]);$('open-local').onclick=()=>native?NoxNative.openLocal():toast(t('请在 Android APP 中使用本机打开'));
$('qr').onclick=()=>{info(t('扫码连接'),'<div class="qr-box" id="qr-code"></div><p class="qr-url">'+esc(state.urls[0])+t('</p><p>接收设备连接同一 Wi-Fi 后扫码。</p>'));new QRCode($('qr-code'),{text:state.urls[0],width:220,height:220,colorDark:'#352963',colorLight:'#ffffff',correctLevel:QRCode.CorrectLevel.M})};
$('more').onclick=()=>{$('menu').hidden=!$('menu').hidden;$('more').setAttribute('aria-expanded',String(!$('menu').hidden))};
document.addEventListener('click',e=>{const link=e.target.closest('[data-web-link]');if(link&&native){e.preventDefault();NoxNative.openUrl(link.dataset.webLink)}const open=e.target.closest('[data-open]');if(open&&native)NoxNative.openFile(open.dataset.open);const media=e.target.closest('[data-media]');if(media)openMedia(media.dataset.media);if(!e.target.closest('#menu')&&!e.target.closest('#more')){$('menu').hidden=true;$('more').setAttribute('aria-expanded','false')}});
$('menu').onclick=e=>{const item=e.target.closest('[data-menu]');if(!item)return;$('menu').hidden=true;$('more').setAttribute('aria-expanded','false');const key=item.dataset.menu;if(['all','sent','received'].includes(key))openFiles(key);else if(key==='settings'){$('clear-on-stop').checked=!!state.clearOnStop;renderPermissions();$('settings-dialog').showModal()}else if(key==='network')info(t('网络信息'),networkHtml(),'network');else info(t('使用说明'),t('<p>1. 所有设备连接同一 Wi-Fi，主机开启共享。<br>2. 其他设备打开主机显示的地址。<br>3. 用底部输入框发文字，点击「＋」发送图片或文件。</p><p>单击图片或视频可预览，视频支持播放与拖动进度。<br>网址可点击打开，长按文字或链接可复制；安装 APK 时按提示授权。<br>点击顶部面板可收起共享信息；右上角「…」查看网络信息与文件列表。</p><p>接收文件保存在主机 APP 内，可另存到手机。关闭共享会清空文字；可在设置中选择同时清理文件。移除接收文件会清理接收副本。单个上传文件最大 2 GB。</p><p>NoxShare · @NoxLeo · v1.0.6</p>'))};
$('attach').onclick=()=>{tray=!tray;$('attachment-tray').hidden=!tray;$('attach').setAttribute('aria-expanded',String(tray))};$('pick-image').onclick=()=>chooseFiles(true);$('pick-file').onclick=()=>chooseFiles(false);$('file-input').onchange=uploadChosen;$('image-input').onchange=uploadChosen;
$('cancel-upload').onclick=()=>{cancelled=true;if(currentUpload)currentUpload.abort()};
$('message-input').oninput=()=>{$('text-counter').textContent=$('message-input').value.length+' / 4000';$('message-input').style.height='auto';$('message-input').style.height=Math.min(120,$('message-input').scrollHeight)+'px';$('send').disabled=sending||!state.running||!online||!$('message-input').value.trim()};
$('message-input').onkeydown=e=>{if(e.key==='Enter'&&!e.shiftKey&&!e.isComposing&&window.innerWidth>600){e.preventDefault();$('message-form').requestSubmit()}};
$('message-form').onsubmit=async e=>{e.preventDefault();const text=$('message-input').value.trim();if(!text||sending)return;if(!state.running||!online)return toast(t('请先开启共享'));sending=true;render();try{if(native){const error=NoxNative.send(text);if(error)throw Error(error)}else if(preview){state.messages.push({id:makeId(),text,from:admin?'host':'guest',sender:myName(),time:Date.now()})}else{const r=await request('api/messages',{method:'POST',headers:{'Content-Type':'text/plain;charset=UTF-8','X-NoxShare':'1'},body:text},7000);if(!r.ok)throw Error(t('发送失败，请重试'))}$('message-input').value='';$('message-input').style.height='auto';$('text-counter').textContent='0 / 4000';forceBottom=true;await refresh()}catch(e){toast(e.message)}finally{sending=false;render()}};
$('timeline').onclick=e=>{const file=e.target.closest('[data-file]'),copyButton=e.target.closest('[data-copy]');if(file)fileAction(file.dataset.file);if(copyButton){const m=state.messages.find(m=>String(m.id)===copyButton.dataset.copy);if(m)copy(m.text)}};
$('new-messages').onclick=()=>{$('chat').scrollTop=$('chat').scrollHeight;$('new-messages').hidden=true};$('chat').onscroll=()=>{if($('chat').scrollHeight-$('chat').scrollTop-$('chat').clientHeight<80)$('new-messages').hidden=true};
document.querySelectorAll('[data-close]').forEach(b=>b.onclick=()=>$(b.dataset.close).close());
document.querySelectorAll('[data-file-tab]').forEach(b=>b.onclick=()=>{fileTab=b.dataset.fileTab;selection.clear();renderFiles()});$('file-search').oninput=renderFiles;
$('select-all').onchange=e=>{listFiles().forEach(f=>e.target.checked?selection.add(f.id):selection.delete(f.id));renderFiles()};
$('file-list').onchange=e=>{const id=e.target.dataset.select;if(id){e.target.checked?selection.add(id):selection.delete(id);renderFiles()}};
$('file-list').onclick=e=>{const file=e.target.closest('[data-file]'),remove=e.target.closest('[data-remove]');if(file)fileAction(file.dataset.file);if(remove)confirmRemove([remove.dataset.remove])};
$('batch-remove').onclick=()=>confirmRemove([...selection]);$('clear').onclick=()=>confirmRemove(state.files.map(f=>f.id));
$('batch-download').onclick=()=>{if(selection.size>200)return toast(t('每次最多打包 200 个文件'));if(preview)return toast(t('预览模式请使用单个文件下载'));const a=document.createElement('a');a.href='bundle?ids='+encodeURIComponent([...selection].join(','));a.download='NoxShare.zip';a.click()};
$('cancel-confirm').onclick=()=>$('confirm-dialog').close();$('confirm-action').onclick=async()=>{$('confirm-dialog').close();if(confirmFn)await confirmFn()};
let mediaId='',mediaZoomed=false;
function mediaUrl(f){if(preview)return blobUrls.get(f.id)||f.preview||'';return(native?'https://noxshare.local/':'')+'media/'+encodeURIComponent(f.id)}
function openMedia(id){
 const f=state.files.find(x=>x.id===id);if(!f)return toast(t('文件已移除'));if(kind(f)==='file')return;
 const url=mediaUrl(f);if(!url)return toast(t('示例文件仅供预览'));
 resetMedia();mediaId=id;$('media-title').textContent=f.name;$('media-meta').textContent=displayName(owner(f))+' · '+formatSize(f.size);$('media-status').textContent=t('加载中…');$('media-save').textContent=admin?t('保存原文件'):t('下载原文件');$('media-zoom').hidden=kind(f)!=='image';
 const element=document.createElement(kind(f)==='video'?'video':'img');
 element.onerror=()=>{$('media-status').textContent=t('无法预览：格式不受支持、文件已移除或连接已中断。可保存原文件查看。')};
 if(kind(f)==='video'){
  element.controls=true;element.playsInline=true;element.preload='metadata';
  element.onloadeddata=()=>$('media-status').textContent='';element.onwaiting=()=>$('media-status').textContent=t('缓冲中…');element.onplaying=()=>$('media-status').textContent='';
 }else{element.alt=f.name;element.onload=()=>$('media-status').textContent='';}
 element.src=url;$('media-stage').append(element);$('media-dialog').showModal();
 if(kind(f)==='video')element.play().catch(()=>{if(!element.error)$('media-status').textContent=t('点击播放按钮开始播放')});
}
function resetMedia(){const video=$('media-stage').querySelector('video');if(video){video.pause();video.removeAttribute('src');video.load()}$('media-stage').replaceChildren();mediaId='';mediaZoomed=false;$('media-zoom').textContent=t('放大');$('media-stage').classList.remove('zoomed')}
window.pauseMedia=()=>{const video=$('media-stage').querySelector('video');if(video)video.pause()};
window.closeTopDialog=()=>{const dialogs=[...document.querySelectorAll('dialog[open]')];if(!dialogs.length)return false;dialogs[dialogs.length-1].close();return true};
$('media-dialog').onclose=resetMedia;$('close-media').onclick=()=>$('media-dialog').close();
$('media-dialog').onclick=e=>{if(e.target===$('media-dialog')){const r=e.target.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)e.target.close()}};
$('media-save').onclick=()=>fileAction(mediaId);
if(native){const open=document.createElement('button');open.id='media-open-default';open.textContent=t('默认应用打开');open.onclick=()=>NoxNative.openFile(mediaId);document.querySelector('.media-tools').append(open)}
$('media-zoom').onclick=()=>{const img=$('media-stage').querySelector('img');if(!img||!img.naturalWidth)return;mediaZoomed=!mediaZoomed;$('media-stage').classList.toggle('zoomed',mediaZoomed);img.style.width=mediaZoomed?Math.min(img.naturalWidth,$('media-stage').clientWidth)*2+'px':'';$('media-zoom').textContent=mediaZoomed?t('适应窗口'):t('放大')};
document.addEventListener('keydown',e=>{const media=e.target.closest('[data-media]');if(media&&e.target.tagName!=='BUTTON'&&(e.key==='Enter'||e.key===' ')){e.preventDefault();openMedia(media.dataset.media)}});
document.addEventListener('visibilitychange',()=>{if(document.hidden)pauseMedia()});
$('clear-on-stop').onchange=()=>{if(!admin)return;const enabled=$('clear-on-stop').checked;if(native){const error=NoxNative.setClearOnStop(enabled);if(error){$('clear-on-stop').checked=!!state.clearOnStop;return toast(error)}}else{try{localStorage.setItem('noxshare.clearOnStop',String(enabled))}catch{}}state.clearOnStop=enabled;toast(t('设置已保存'))};
let dragDepth=0;
const fileDrag=e=>e.dataTransfer&&Array.from(e.dataTransfer.types||[]).includes('Files');
function hideDrop(){dragDepth=0;$('drop-zone').hidden=true}
window.addEventListener('dragenter',e=>{if(!fileDrag(e))return;e.preventDefault();if(native)return;dragDepth++;$('drop-title').textContent=uploading?t('正在上传，请稍后再添加'):!state.running||!online?t('请先连接正在共享的主机'):t('松开即可上传');$('drop-detail').textContent=t('支持多个文件、图片和视频');$('drop-zone').hidden=false});
window.addEventListener('dragover',e=>{if(!fileDrag(e))return;e.preventDefault();e.dataTransfer.dropEffect=!native&&!uploading&&state.running&&online?'copy':'none'});
window.addEventListener('dragleave',e=>{if(!fileDrag(e))return;dragDepth=Math.max(0,dragDepth-1);if(!dragDepth)hideDrop()});
window.addEventListener('drop',e=>{if(!fileDrag(e))return;e.preventDefault();hideDrop();if(native)return;if(!state.running||!online)return toast(t('请先连接正在共享的主机'));const items=Array.from(e.dataTransfer.items||[]),files=Array.from(e.dataTransfer.files||[]).filter(f=>!items.some(i=>{const entry=i.webkitGetAsEntry&&i.webkitGetAsEntry();return entry&&entry.isDirectory&&i.getAsFile()&&i.getAsFile().name===f.name}));if(items.some(i=>{const entry=i.webkitGetAsEntry&&i.webkitGetAsEntry();return entry&&entry.isDirectory}))toast(t('暂不支持文件夹，请选择文件'));uploadFiles(files)});
window.addEventListener('blur',hideDrop);document.addEventListener('dragend',hideDrop);
let copyTextValue='',copyLinkValue='';
function renderPermissions(){const allowed=state.permissions||{};$('permission-options').hidden=!admin;$('install-permission-state').textContent=native?(allowed.install?t('已允许'):t('未允许')):t('在 APP 中设置');$('notification-permission-state').textContent=native?(allowed.notifications?t('已允许'):t('未允许')):t('在 APP 中设置')}
$('install-permission').onclick=()=>native?NoxNative.requestInstallPermission():toast(t('请在 Android APP 中授权'));
$('notification-permission').onclick=()=>native?NoxNative.notificationSettings():toast(t('请在 Android APP 中授权'));
$('app-permissions').onclick=()=>native?NoxNative.appSettings():toast(t('请在 Android APP 中打开系统设置'));
function messageMenu(id,link=''){const message=state.messages.find(m=>String(m.id)===id);if(!message)return;copyTextValue=message.text;copyLinkValue=link;$('copy-title').textContent=link?t('链接操作'):t('文字操作');$('copy-preview').textContent=link||message.text;$('copy-link-action').hidden=!link;$('open-link-action').hidden=!link;if(!$('copy-dialog').open)$('copy-dialog').showModal()}
$('copy-text-action').onclick=()=>{$('copy-dialog').close();copy(copyTextValue)};
$('copy-link-action').onclick=()=>{$('copy-dialog').close();copy(copyLinkValue)};
$('open-link-action').onclick=()=>{$('copy-dialog').close();if(native)NoxNative.openUrl(copyLinkValue);else window.open(copyLinkValue,'_blank','noopener,noreferrer')};
let press=null,suppressedId='',suppressUntil=0;
function cancelPress(){if(press)clearTimeout(press.timer);press=null}
function holdTarget(target){const message=target.closest('[data-message-id]');if(message)return{node:message,key:'m:'+message.dataset.messageId,id:message.dataset.messageId,link:target.closest('[data-web-link]')?.dataset.webLink||'',text:true};const file=target.closest('[data-file-id]');if(admin&&file)return{node:file,key:'f:'+file.dataset.fileId,id:file.dataset.fileId,text:false};return null}
function performHold(item){suppressedId=item.key;suppressUntil=Date.now()+1000;if(item.text)messageMenu(item.id,item.link);else confirmRemove([item.id])}
document.addEventListener('pointerdown',e=>{cancelPress();if(e.button!==0||e.isPrimary===false||e.target.closest('input')||e.target.closest('button:not(.image-preview-button)'))return;const item=holdTarget(e.target);if(!item)return;press={...item,x:e.clientX,y:e.clientY,pointer:e.pointerId,timer:setTimeout(()=>{press=null;performHold(item)},650)}});
document.addEventListener('pointermove',e=>{if(press&&e.pointerId===press.pointer&&(Math.abs(e.clientX-press.x)>10||Math.abs(e.clientY-press.y)>10))cancelPress()});
document.addEventListener('pointerup',cancelPress);document.addEventListener('pointercancel',cancelPress);document.addEventListener('scroll',e=>{if(press&&e.target.contains&&e.target.contains(press.node))cancelPress()},true);window.addEventListener('blur',cancelPress);
document.addEventListener('click',e=>{const item=holdTarget(e.target);if(item&&item.key===suppressedId&&Date.now()<suppressUntil){e.preventDefault();e.stopImmediatePropagation()}},true);
document.addEventListener('contextmenu',e=>{const item=holdTarget(e.target);if(!item)return;e.preventDefault();cancelPress();performHold(item)});
document.addEventListener('keydown',e=>{const open=e.target.closest('[data-open]');if(open&&native&&e.target.tagName!=='BUTTON'&&(e.key==='Enter'||e.key===' ')){e.preventDefault();NoxNative.openFile(open.dataset.open)}});
$('language-choice').onchange=()=>{try{NoxI18n.set($('language-choice').value);toast(t('语言设置已保存'))}catch(e){toast(e.message)}};
window.addEventListener('nox-language',()=>{chatVersion='';if($('media-open-default'))$('media-open-default').textContent=t('默认应用打开');render();renderPreviewBanner()});
function renderPreviewBanner(){const banner=document.querySelector('.preview-label');if(banner)banner.innerHTML=t('界面预览 · 示例数据 ')+'<a href="?preview='+(admin?'visitor':'admin')+'&demo=1">'+t('切换')+(admin?t('访问端'):t('主机端'))+'</a>'}
window.onNativeUpdate=()=>{forceBottom=true;refresh()};window.onNativeError=toast;
if(preview){try{state.clearOnStop=localStorage.getItem('noxshare.clearOnStop')==='true'}catch{}state.clientIp='192.168.1.120';state.network={connected:true,kind:'Wi-Fi',ip:'192.168.1.108',gateway:'192.168.1.1',prefix:24,port:8787};state.urls=['http://192.168.1.108:8787'];state.running=true;const banner=document.createElement('div');banner.className='preview-label';banner.innerHTML=t('界面预览 · 示例数据 <a href="?preview=')+(admin?'visitor':'admin')+t('&demo=1">切换')+(admin?t('访问端'):t('主机端'))+'</a>';document.querySelector('.app').append(banner);
 if(params.has('demo')){const now=Date.now();const art='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="420" height="270" viewBox="0 0 420 270"><defs><linearGradient id="s" x2="0" y2="1"><stop stop-color="#cbc2ef"/><stop offset="1" stop-color="#ede7f8"/></linearGradient></defs><path fill="url(#s)" d="M0 0h420v270H0z"/><circle fill="#faf3ff" cx="310" cy="66" r="28"/><path fill="#a495d3" d="M0 220 90 96 200 235 310 136 420 220v50H0z"/><path fill="#7763ad" d="M0 246 160 153 270 238 360 205 420 242v28H0z"/></svg>');state.files=[{id:'f1',name:t('周末旅行计划.pdf'),size:1539020,type:'application/pdf',added:now-110000,uploader:'主机'},{id:'f2',name:t('山野片刻.png'),size:3280120,type:'image/png',added:now-50000,uploader:'192.168.1.120',uploaded:true,preview:art}];state.messages=[{id:'m1',from:'host',sender:'主机',text:t('旅行计划整理好了，发给你。'),time:now-130000},{id:'m2',from:'guest',sender:'192.168.1.120',text:t('收到！我也分享一张喜欢的照片。'),time:now-80000},{id:'m3',from:'host',sender:'主机',text:t('很好看，下次就去这里。'),time:now-20000}]}}
renderPreviewBanner();setExpanded(true);refresh();setInterval(refresh,2000);
