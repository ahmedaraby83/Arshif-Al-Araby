let selected='',groups=[],items=[],allItems=[],screen='home',drawerOpen=false,activeDocumentId=null,documentActionCloseTimer=null;
const $=q=>document.querySelector(q);
const icons={
 plus:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>',
 edit:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m4 16-1 5 5-1L19 9l-4-4zM13 7l4 4"/></svg>',
 move:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"><path d="M3 7h7l2 2h9v11H3zM7 14h10m-3-3 3 3-3 3"/></svg>',
 open:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 5h11v11M19 5 6 18"/></svg>',
 share:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="2"/><circle cx="6" cy="12" r="2"/><circle cx="18" cy="19" r="2"/><path d="m8 11 8-5M8 13l8 5"/></svg>',
 star:'<svg viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" stroke-width="1.5"><path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9z"/></svg>',
 trash:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 7h16M9 7V4h6v3m-9 0 1 14h10l1-14M10 11v6M14 11v6"/></svg>',
 dots:'<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="5" r="1.8"></circle><circle cx="12" cy="12" r="1.8"></circle><circle cx="12" cy="19" r="1.8"></circle></svg>',
 folder:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 7h7l2 2h9v10H3z"/></svg>'
};
function esc(v){return String(v||'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39'}[c]))}
function arg(v){return encodeURIComponent(String(v||''))}
function decoded(v){return decodeURIComponent(v)}
function descendants(name){const set=new Set([name]);let changed=true;while(changed){changed=false;groups.forEach(g=>{if(g.parent&&set.has(g.parent)&&!set.has(g.name)){set.add(g.name);changed=true}})}return set}
function ordered(){
 const out=[],seen=new Set();
 function add(parent,level){groups.filter(g=>(g.parent||'')===parent).forEach(g=>{if(seen.has(g.name))return;seen.add(g.name);out.push({...g,level});add(g.name,level+1)})}
 add('',0);groups.forEach(g=>{if(!seen.has(g.name))out.push({...g,level:0})});return out;
}
function folderSvg(index){
 const classes=['folder-gold','folder-blue','folder-purple','folder-teal','folder-pink','folder-orange'];
 return `<div class="glass-folder ${classes[index%classes.length]}" aria-hidden="true"><span class="folder-tab"></span><span class="folder-body"></span><span class="folder-shine"></span></div>`;
}
function folderCard(g,index,manage){
 const members=descendants(g.name),files=allItems.filter(i=>members.has(i.group)),children=groups.filter(x=>x.parent===g.name).length;
 return `<article class="folder-card ${manage?'has-menu':''}">
  <button class="folder-open" type="button" onclick="choose('${arg(g.name)}')" aria-label="فتح ${esc(g.name)}">
   <span class="folder-art">${folderSvg(index)}<span class="folder-badge">${files.length}</span></span>
   <span class="folder-name">${esc(g.name)}</span>
   <span class="folder-meta">${manage?`${files.length} ملف • ${children} مجلد فرعي`:`${files.length} ملف`}</span>
  </button>
  ${manage?`<button type="button" class="folder-menu" onclick="Archive.renameGroup(decoded('${arg(g.name)}'))" aria-label="قائمة المجموعة">⋮</button>`:''}
 </article>`;
}
function fileIcon(item){
 const pdf=String(item.mime||'').includes('pdf');
 return pdf
 ?'<div class="document-type-icon pdf"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 2h8l4 4v16H6zM14 2v5h5"/><text x="9" y="17" font-size="6" fill="currentColor" stroke="none">PDF</text></svg></div>'
 :'<div class="document-type-icon image"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="9" cy="10" r="2"/><path d="m4 17 5-4 3 3 3-2 5 4"/></svg></div>';
}
function documentRow(item){
 const favorite=!!item.favorite;
 const size=item.size?` • ${esc(item.size)}`:'';
 const id=Number(item.id);
 const favoriteLabel=favorite?'إزالة من المفضلة':'إضافة إلى المفضلة';
 return `<article class="document-row" onclick="openDocumentFromRow(event,${id})" aria-label="فتح المستند ${esc(item.title)}">
  ${fileIcon(item)}
  <div class="document-info"><div class="document-title">${esc(item.title)}</div><div class="document-meta">${esc(item.date)}${size}</div></div>
  <div class="document-actions">
   <button type="button" class="document-favorite-button ${favorite?'active':''}" onclick="event.stopPropagation();toggleFavorite(${id})" aria-label="${favoriteLabel}" title="${favoriteLabel}">${icons.star}</button>
   <button type="button" class="document-menu-button" data-document-id="${id}" aria-label="إجراءات المستند" title="إجراءات المستند" onclick="event.stopPropagation();openDocumentMenu(event,${id})">${icons.dots}</button>
  </div>
 </article>`;
}
function openDocumentFromRow(event,id){
 if(event&&event.target.closest('button'))return;
 if(window.Archive)Archive.open(Number(id));
 else toast("هذا الإجراء يعمل داخل تطبيق Android");
}
function renderDocumentActionList(item){
 const favoriteAction=item.favorite
  ?{action:'favorite',label:'إزالة من المفضلة',icon:icons.star}
  :{action:'favorite',label:'إضافة إلى المفضلة',icon:icons.star};
 const actions=[
  {action:'open',label:'فتح المستند',icon:icons.open},
  {action:'edit',label:'تعديل الاسم والبيانات',icon:icons.edit},
  {action:'move',label:'نقل إلى مجموعة',icon:icons.move},
  {action:'share',label:'مشاركة',icon:icons.share},
  favoriteAction,
  {action:'delete',label:'حذف المستند',icon:icons.trash,danger:true}
 ];
 $('#documentActionName').textContent=item.title||'';
 $('#documentActionList').innerHTML=actions.map(({action,label,icon,danger})=>
  `<button type="button" class="document-action-item${danger?' danger':''}" aria-label="${label}" title="${label}" onclick="runDocumentAction('${action}')">${icon}<span>${label}</span></button>`
 ).join('');
}
function openDocumentMenu(event,documentId){
 if(event){event.preventDefault();event.stopPropagation()}
 if(documentActionCloseTimer){clearTimeout(documentActionCloseTimer);documentActionCloseTimer=null}
 activeDocumentId=Number(documentId);
 const documentItem=allItems.find(item=>Number(item.id)===activeDocumentId);
 if(!documentItem){activeDocumentId=null;toast("تعذر العثور على المستند");return}
 renderDocumentActionList(documentItem);
 const shade=$('#documentActionShade'),sheet=$('#documentActionSheet');
 shade.hidden=false;sheet.hidden=false;
 requestAnimationFrame(()=>{
  shade.classList.add('visible');sheet.classList.add('visible');
  const firstAction=sheet.querySelector('.document-action-item');
  if(firstAction)firstAction.focus();
 });
}
function closeDocumentMenu(){
 const shade=$('#documentActionShade'),sheet=$('#documentActionSheet');
 if(!shade||!sheet)return;
 shade.classList.remove('visible');sheet.classList.remove('visible');
 if(documentActionCloseTimer)clearTimeout(documentActionCloseTimer);
 documentActionCloseTimer=window.setTimeout(()=>{
  shade.hidden=true;sheet.hidden=true;activeDocumentId=null;documentActionCloseTimer=null;
 },220);
}
function runDocumentAction(action){
 const id=activeDocumentId;
 if(!Number.isFinite(id)){closeDocumentMenu();return}
 closeDocumentMenu();
 if(!window.Archive){toast("هذا الإجراء يعمل داخل تطبيق Android");return}
 switch(action){
  case 'open':Archive.open(id);break;
  case 'edit':Archive.edit(id);break;
  case 'move':Archive.move(id);break;
  case 'share':Archive.share(id);break;
  case 'favorite':Archive.favorite(id);break;
  case 'delete':Archive.delete(id);break;
 }
}
function renderArchive(groupJson,data,all){
 try{groups=JSON.parse(groupJson||'[]')}catch(e){groups=[]}
 items=data||[];allItems=all||data||[];
 $('#documentCount').textContent=allItems.length;
 $('#groupCount').textContent=groups.length;
 const roots=groups.filter(g=>!g.parent);
 $('#homeFolderStrip').innerHTML=roots.length?roots.map((g,index)=>folderCard(g,index,false)).join(''):'';
 $('#groupGrid').innerHTML=roots.length?roots.map((g,index)=>folderCard(g,index,true)).join(''):'';
 const recent=screen==='favorites'?allItems.filter(i=>i.favorite):allItems.slice(0,6);
 const emptyState=screen==='favorites'
  ?'<div class="empty-state"><b>لا توجد مفضلات بعد</b><small>اختر نجمة بجانب أي مستند لإضافته</small></div>'
  :'<div class="empty-state"><b>لا توجد ملفات بعد</b><small>اضغط زر الإضافة لاستيراد صورة أو PDF</small></div>';
 $('#homeDocumentList').innerHTML=recent.length?recent.map(documentRow).join(''):emptyState;
 renderSelectedFolder();
 showScreen();
}
function renderSelectedFolder(){
 if(!selected)return;
 const members=descendants(selected),folderItems=allItems.filter(i=>members.has(i.group)),children=groups.filter(g=>g.parent===selected);
 $('#folderName').textContent=selected;
 $('#folderBreadcrumb').textContent=`المجموعات / ${selected}`;
 $('#folderMeta').textContent=`${folderItems.length} ملف • ${children.length} فروع`;
 const summaryIndex=Math.max(0,ordered().findIndex(g=>g.name===selected));
 $('#folderIcon').innerHTML=folderSvg(summaryIndex);
 $('#subfolderGrid').innerHTML=children.length?children.map((g,index)=>`<article class="subfolder-card"><div class="subfolder-icon">${folderSvg(index)}</div><div><b>${esc(g.name)}</b><small>${allItems.filter(i=>i.group===g.name).length} ملف</small></div><button type="button" onclick="Archive.renameGroup('${arg(g.name)}')">⋮</button></article>`).join(''):'';
 const query=($('#folderSearch')?.value||'').trim().toLowerCase();
 const visible=folderItems.filter(i=>!query||String(i.title).includes(query)||String(i.notes||'').includes(query)||String(i.tags||'').includes(query));
 $('#folderDocumentList').innerHTML=visible.length?visible.map(documentRow).join(''):'<div class="empty-state"><b>لا توجد مستندات في هذا المجلد</b><small>اضغط زر الإضافة لاستيراد ملف</small></div>';
}
function showScreen(){
 $('#homeScreen').hidden=screen!=='home'&&screen!=='favorites';
 $('#groupsScreen').hidden=screen!=='groups';
 $('#folderScreen').hidden=screen!=='folder';
 $('#backupScreen').hidden=screen!=='backup';
 document.querySelectorAll('.nav-item').forEach(n=>n.classList.toggle('active',n.dataset.tab===screen||(screen==='folder'&&n.dataset.tab==='groups')));
 $('#cameraFab').hidden=screen==='backup';
 $('#addFab').hidden=screen==='backup';
 const floating=$('.floating-actions');if(floating)floating.hidden=screen==='backup';
 if(screen==='folder')renderSelectedFolder();
 window.scrollTo(0,0);
}
function showHome(){selected='';screen='home';closeDrawer();showScreen();reload()}
function openGroups(){selected='';screen='groups';closeDrawer();showScreen();reload()}
function choose(encoded){selected=decoded(encoded);screen='folder';closeDrawer();showScreen();reload()}
function openBackup(){screen='backup';selected='';closeDrawer();showScreen();$('#backupStatus').textContent='غير مرتبط'}
function showFavorites(){screen='favorites';selected='';closeDrawer();showScreen();const favorites=allItems.filter(i=>i.favorite);$('#homeDocumentList').innerHTML=favorites.length?favorites.map(documentRow).join(''):'<div class="empty-state"><b>لا توجد مفضلات بعد</b><small>اختر نجمة بجانب أي مستند لإضافته</small></div>'}
function showAllDocuments(){screen='home';selected='';showScreen();reload()}
function filterGroups(){const q=$('#groupSearch').value.trim();document.querySelectorAll('#groupGrid .folder-card').forEach(card=>{card.hidden=q&&!card.textContent.includes(q)})}
function reload(){
 let q=$('#homeSearch')?.value||'';
 if(screen==='folder')q=$('#folderSearch')?.value||'';
 if(typeof Archive==='undefined')return;
 Archive.refresh(q,selected);
}
function toggleFavorite(id){if(typeof Archive!=='undefined'&&typeof Archive.favorite==='function'){Archive.favorite(id)}else{const item=allItems.find(i=>i.id===id);if(item)item.favorite=!item.favorite;renderArchive(JSON.stringify(groups),items,allItems)}}
function drawer(on){drawerOpen=on;$('#drawer').classList.toggle('open',on);$('#shade').classList.toggle('show',on)}
function closeDrawer(){drawerOpen=false;$('#drawer').classList.remove('open');$('#shade').classList.remove('show')}
function handleBack(){if(!$('#documentActionSheet').hidden){closeDocumentMenu();return true}if(drawerOpen){closeDrawer();return}if(screen==='backup'||screen==='groups'||screen==='folder'||screen==='favorites'){showHome();return}if(typeof Archive!== 'undefined')Archive.exit()}
function toast(msg){const el=$('#toast');el.textContent=msg;el.classList.add('show');setTimeout(()=>el.classList.remove('show'),2200)}

const themeIcons={
 dark:'<svg viewBox="0 0 24 24" fill="#ffd66b" stroke="currentColor" stroke-width="1.7"><circle cx="12" cy="12" r="3.5"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2"/></svg>',
 light:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M20 15A8 8 0 0 1 9 4a8.5 8.5 0 1 0 11 11z"/></svg>'
};
function applyTheme(theme){
 const light=theme==='light';document.body.classList.toggle('light',light);
 $('#themeToggle').innerHTML=light?themeIcons.light:themeIcons.dark;
 if($('#folderTheme'))$('#folderTheme').innerHTML=light?themeIcons.light:themeIcons.dark;
 if(window.Archive&&typeof window.Archive.setTheme==='function')window.Archive.setTheme(light?'light':'dark');
 try{localStorage.setItem('archiveTheme',theme)}catch(e){}
}
function toggleTheme(){const current=document.body.classList.contains('light')?'light':'dark';applyTheme(current==='light'?'dark':'light')}
try{applyTheme(localStorage.getItem('archiveTheme')||'dark')}catch(e){applyTheme('dark')}
if($('#authScreen'))document.body.classList.add('auth-active');
const isAndroidWebView=typeof window.Archive!=='undefined';
document.body.classList.toggle('android-webview',isAndroidWebView);
document.body.classList.toggle('browser-preview',!isAndroidWebView);

window.renderArchive=renderArchive;
window.handleBack=handleBack;
window.unlockArchive=function(){const el=$('#authScreen');if(!el)return;document.body.classList.remove('auth-active');el.classList.add('unlocking');setTimeout(()=>el.remove(),500)};
document.addEventListener('keydown',event=>{
 if(event.key==='Escape'&&!$('#documentActionSheet').hidden){event.preventDefault();closeDocumentMenu()}
});

if(typeof Archive==='undefined'){
 const previewGroups=[{name:'العمل',parent:null},{name:'قرارات وزارية',parent:null},{name:'الشهادات',parent:null},{name:'ملفات شخصية',parent:null},{name:'العقود',parent:'العمل'},{name:'المراسلات',parent:'العمل'}];
 const previewItems=[
  {id:1,title:'قرار إداري',group:'قرارات وزارية',notes:'',tags:'',mime:'application/pdf',date:'2026-05-20',favorite:true,size:'2.4 MB'},
  {id:2,title:'شهادة تدريب',group:'الشهادات',notes:'',tags:'',mime:'image/jpeg',date:'2026-05-18',favorite:false,size:'1.7 MB'},
  {id:3,title:'مستندات العمل',group:'العمل',notes:'',tags:'',mime:'application/pdf',date:'2026-05-16',favorite:false,size:'3.1 MB'}
 ];
 renderArchive(JSON.stringify(previewGroups),previewItems,previewItems);
}
