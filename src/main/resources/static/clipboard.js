'use strict';
const $=id=>document.getElementById(id);
const api=new URL('api/clipboard',location.href).pathname;
let key='', generation=0, expiry=0, pollBusy=false, uploadBusy=false;
function message(text){$('message').textContent=text;}
function lock(){key='';generation++;expiry=0;$('key').value='';$('text').value='';$('files').value='';$('chosen').textContent='Up to 10 MB per file. 100 MB and 200 items total.';$('items').replaceChildren();$('workspace').hidden=true;$('unlock').hidden=false;message('Clipboard locked.');}
async function request(path='', options={}){
 const mine=generation;
 const response=await fetch(api+path,{...options,headers:{...options.headers,'X-API-Key':key},cache:'no-store',credentials:'omit'});
 if(mine!==generation)throw Error('Clipboard locked.');
 if(response.status===401){lock();throw Error('Invalid key. Enter your shared API key again.');}
 if(!response.ok){let detail;try{detail=await response.json();}catch{}throw Error(detail?.error||`Request failed (${response.status}).`);}
 return response;
}
const bytes=n=>n<1024?`${n} B`:n<1048576?`${(n/1024).toFixed(1)} KB`:`${(n/1048576).toFixed(1)} MB`;
function button(label,action,secondary=true){const b=document.createElement('button');b.textContent=label;if(secondary)b.className='secondary';b.onclick=async()=>{b.disabled=true;try{await action();}catch(e){message(e.message);}finally{b.disabled=false;}};return b;}
function render(data){
 expiry=Date.parse(data.expiresAt);$('zone').textContent=data.timezone;
 $('day').textContent=`${data.date} · ${data.items.length} items`;
 $('reset').textContent=`Resets at midnight · ${data.timezone}`;
 $('usage').textContent=`${bytes(data.usedBytes)} of ${bytes(data.maxBytes)} · ${data.maxItems} item limit`;
 $('items').replaceChildren();
 if(!data.items.length){const empty=document.createElement('p');empty.className='empty';empty.textContent='Nothing here yet. Send something from either device.';$('items').append(empty);}
 for(const item of data.items){
  const card=document.createElement('article');card.className='item';
  const head=document.createElement('div');head.className='itemHead';head.textContent=`${item.type==='text'?'TEXT':'FILE'} · ${bytes(item.size)} · ${new Date(item.createdAt).toLocaleTimeString()}`;card.append(head);
  const content=document.createElement(item.type==='text'?'pre':'h3');content.textContent=item.type==='text'?item.text:item.filename;card.append(content);
  const actions=document.createElement('div');actions.className='actions';
  if(item.type==='text')actions.append(button('Copy text',async()=>{
   // Refresh the item before copying so an expired/deleted entry cannot be copied from stale UI.
   const mine=generation;const current=await (await request('/'+item.id)).json();if(mine!==generation)return;
   try{await navigator.clipboard.writeText(current.text);message('Copied to clipboard.');}catch{const range=document.createRange();range.selectNodeContents(content);const selection=getSelection();selection.removeAllRanges();selection.addRange(range);message('Text selected. Press Ctrl+C (or long-press and Copy). Use HTTPS for one-click copying.');}
  },false));
  else actions.append(button('Download',async()=>{const mine=generation;const blob=await (await request('/'+item.id+'/download')).blob();if(mine!==generation)return;const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=item.filename;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),30000);},false));
  actions.append(button('Delete',async()=>{if(!confirm('Delete this item from all devices?'))return;await request('/'+item.id,{method:'DELETE'});await refresh();message('Item deleted.');}));card.append(actions);$('items').append(card);
 }
}
async function refresh(){if(!key||pollBusy)return;pollBusy=true;const mine=generation;try{const data=await(await request()).json();if(mine===generation)render(data);}finally{pollBusy=false;}}
$('unlockForm').onsubmit=async e=>{e.preventDefault();key=$('key').value.trim();generation++;try{await refresh();if(!key)return;$('key').value='';$('unlock').hidden=true;$('workspace').hidden=false;message('Connected. Your devices share this clipboard.');}catch(e){key='';message(e.message);}};
$('lock').onclick=lock;$('refresh').onclick=()=>refresh().catch(e=>message(e.message));
$('textForm').onsubmit=async e=>{e.preventDefault();const value=$('text').value;if(!value.trim())return message('Enter some text first.');$('sendText').disabled=true;try{await request('/text',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text:value})});if($('text').value===value)$('text').value='';await refresh();message('Text sent.');}catch(e){message(e.message);}finally{$('sendText').disabled=false;}};
async function upload(files){
 if(!key)return message('Unlock the clipboard first.');if(uploadBusy)return message('An upload is already in progress.');if(!files.length)return message('Choose a file first.');
 uploadBusy=true;$('sendFiles').disabled=true;let count=0;
 try{for(const file of files){if(!file.size)throw Error(`${file.name} is empty.`);if(file.size>10*1024*1024)throw Error(`${file.name} exceeds 10 MB.`);message(`Uploading ${file.name}…`);const data=new FormData();data.append('file',file);await request('/files',{method:'POST',body:data});count++;}
 $('files').value='';$('chosen').textContent='Up to 10 MB per file. 100 MB and 200 items total.';message(`${count} file(s) uploaded.`);
 }catch(e){message(`${count} file(s) uploaded. ${e.message}`);}finally{uploadBusy=false;$('sendFiles').disabled=false;await refresh().catch(e=>message(e.message));}
}
$('files').onchange=()=>$('chosen').textContent=Array.from($('files').files).map(f=>`${f.name} (${bytes(f.size)})`).join(', ');
$('fileForm').onsubmit=e=>{e.preventDefault();upload(Array.from($('files').files));};
const drop=document.querySelector('.drop');drop.ondragover=e=>e.preventDefault();drop.ondrop=e=>{e.preventDefault();upload(Array.from(e.dataTransfer.files));};
document.addEventListener('paste',e=>{if(key&&e.clipboardData.files.length){e.preventDefault();upload(Array.from(e.clipboardData.files));}});
setInterval(()=>{if(key&&!document.hidden)refresh().catch(e=>message(e.message));},10000);
setInterval(()=>{if(expiry&&Date.now()>=expiry){$('items').replaceChildren();expiry=0;message('Daily reset. Refreshing today’s clipboard…');refresh().catch(e=>message(e.message));}},1000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&key)refresh().catch(e=>message(e.message));});
