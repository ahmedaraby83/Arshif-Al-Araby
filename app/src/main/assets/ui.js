let selected='',groups=[],items=[],allItems=[],drawerOpen=false,screen='home',renderedScreen='';
const $=q=>document.querySelector(q);
const S={
 move:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 7h7l2 2h9v11H3zM7 14h10m-3-3 3 3-3 3"/></svg>',
 folder:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M3 8a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v9H3z"/><path d="M3 11h18"/></svg>',
 plus:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>',
 edit:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m4 16-1 5 5-1L19 9l-4-4zM13 7l4 4"/></svg>',
 open:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 5h11v11M19 5 6 18"/></svg>',
 share:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="2"/><circle cx="6" cy="12" r="2"/><circle cx="18" cy="19" r="2"/><path d="m8 11 8-5M8 13l8 5"/></svg>',
 trash:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 7h16M9 7V4h6v3m-9 0 1 14h10l1-14M10 11v6M14 11v6"/></svg>'
};
function esc(value){return String(value||'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
function arg(value){return encodeURIComponent(String(value||'')).replace(/'/g,'%27')}
function decoded(value){return decodeURIComponent(value)}
function ordered(){
 const out=[],seen=new Set();
 function add(parent,level){groups.filter(g=>(g.parent||'')===(parent||'')).forEach(g=>{if(seen.has(g.name))return;seen.add(g.name);out.push({...g,level});add(g.name,level+1)})}
 add('',0);groups.filter(g=>!seen.has(g.name)).forEach(g=>out.push({...g,level:0}));return out;
}
function descendants(name){const names=new Set([name]);let changed=true;while(changed){changed=false;for(const g of groups)if(g.parent&&names.has(g.parent)&&!names.has(g.name)){names.add(g.name);changed=true}}return names}
function fileIcon(mime){return String(mime).includes('pdf')?'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 2h8l4 4v16H6zM14 2v5h5M9 14h6M9 18h4"/></svg>':'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="9" cy="10" r="2"/><path d="m4 17 5-4 3 3 3-2 5 4"/></svg>'}
function folderCard(g,index){
 const members=descendants(g.name),files=allItems.filter(i=>members.has(i.group)),count=files.length,children=groups.filter(x=>x.parent===g.name).length;
 return `<article class="folderCard tone${index%5}" style="animation-delay:${Math.min(index*45,270)}ms">
  <button class="folderOpen" onclick="choose(decoded('${arg(g.name)}'))" aria-label="فتح ${esc(g.name)}">
   <span class="folderArt" aria-hidden="true"><span class="folderBack"></span><span class="folderPaper p1"></span><span class="folderPaper p2"></span><span class="folderFront"></span><span class="folderBadge">${count}</span></span><span class="folderTitle">${esc(g.name)}</span>
   <span class="folderStats"><b>${count}</b> ملف <i></i><b>${children}</b> فرع</span>
   <span class="folderPreview">${files.length?files.slice(0,2).map(f=>`<span>▤ ${esc(f.title)}</span>`).join(''):'<span>مجلد جاهز لملفاتك</span>'}</span>
  </button><div class="folderActions"><button onclick="Archive.addChild(decoded('${arg(g.name)}'))" aria-label="إضافة فرع إلى ${esc(g.name)}" title="إضافة فرع">${S.plus}</button><button onclick="Archive.renameGroup(decoded('${arg(g.name)}'))" aria-label="إعادة تسمية ${esc(g.name)}" title="إعادة تسمية">${S.edit}</button></div>
 </article>`;
}
function renderArchive(groupJson,data,all){
 try{groups=JSON.parse(groupJson)}catch(error){groups=[]}
 items=data||[];allItems=all||data||[];
 $('#total').textContent=allItems.length;$('#gCount').textContent=groups.length;
 $('#resultCount').textContent=items.length;
 const tree=ordered();
 $('#chips').innerHTML=`<button class="chip ${!selected?'active':''}" onclick="allDocs()" aria-label="كل الملفات">⌂</button>`+
  tree.map(g=>`<button class="chip ${g.level?'child':''} ${selected===g.name?'active':''}" onclick="choose(decoded('${arg(g.name)}'))">${g.level?'↳ ':''}${esc(g.name)}</button>`).join('');
 const roots=groups.filter(g=>!g.parent||!groups.some(p=>p.name===g.parent));
 $('#groupGrid').innerHTML=roots.length?roots.map(folderCard).join(''):`<div class="empty"><div class="emptyIcon">${S.folder}</div><b>ابدأ بأول مجموعة</b><small>أنشئ مجلدًا لتنظيم مستنداتك</small></div>`;
 $('#homeGroupGrid').innerHTML=roots.length?roots.map(folderCard).join(''):`<button class="createFirstFolder" onclick="Archive.addGroup()">${S.folder}<b>أنشئ أول مجلد</b><small>واختر مكانًا لكل ملف</small></button>`;
 const children=groups.filter(g=>g.parent===selected);
 $('#nestedGroups').hidden=!selected||!children.length;
 $('#nestedGroups').innerHTML=children.length?`<div class="nestedTitle"><b>الفروع</b><span>${children.length}</span></div><div class="groupGrid">${children.map(folderCard).join('')}</div>`:'';
 $('#folderHeading').hidden=!selected;
 $('#folderHeading').innerHTML=selected?`<button onclick="openGroups()" aria-label="العودة للمجموعات">‹</button><span><small>داخل المجموعة</small><b>${esc(selected)}</b></span><button class="folderAddChild" onclick="Archive.addChild(decoded('${arg(selected)}'))" aria-label="إضافة فرع">＋</button>`:'';
 $('#docs').innerHTML=items.length?items.map((item,index)=>`<article class="card" style="animation-delay:${Math.min(index*35,200)}ms"><div class="cardMain"><div class="fileIcon ${String(item.mime).includes('pdf')?'pdf':''}">${fileIcon(item.mime)}</div><div class="fileInfo"><b>${esc(item.title)}</b><small>${esc(item.group)} · ${esc(item.date)}</small></div></div><div class="cardActs"><button class="cardAct" onclick="Archive.favorite(${item.id})" title="مفضلة">★</button><button class="cardAct" onclick="Archive.edit(${item.id})" title="تعديل">✎</button><button class="cardAct moveAct" onclick="Archive.move(${item.id})" aria-label="نقل ${esc(item.title)}" title="نقل إلى مجلد">${S.move}</button><button class="cardAct" onclick="Archive.open(${item.id})" aria-label="فتح ${esc(item.title)}" title="فتح">${S.open}</button><button class="cardAct" onclick="Archive.share(${item.id})" aria-label="مشاركة ${esc(item.title)}" title="مشاركة">${S.share}</button><button class="cardAct del" onclick="Archive.delete(${item.id})" aria-label="حذف ${esc(item.title)}" title="حذف">${S.trash}</button></div></article>`).join(''):
  `<div class="empty"><div class="emptyIcon">${S.folder}</div><b>لا توجد ملفات هنا بعد</b><small>اضغط زر الإضافة لاستيراد صورة أو PDF</small></div>`;
 showScreen();
}
function showScreen(){
 document.body.dataset.screen=screen;
 const home=screen==='home';$('#search').parentElement.hidden=home;$('#chips').hidden=home;$('#filesHeading').hidden=home;$('#docs').hidden=home;
 $('#homePanel').hidden=screen==='groups';$('#groupsPanel').hidden=screen!=='groups';
 $('#search').placeholder=selected?'ابحث داخل '+selected:'ابحث باسم الملف أو الكلمات المفتاحية';
 $('#homeGroupSection').hidden=!!selected;
 activate(screen==='groups'||screen==='folder'?'groups':'home');
 const key=screen+selected;if(key!==renderedScreen){renderedScreen=key;const panel=screen==='groups'?$('#groupsPanel'):$('#homePanel');panel.classList.remove('screenEnter');void panel.offsetWidth;panel.classList.add('screenEnter')}
}
function reload(){Archive.refresh($('#search').value,selected)}
function choose(group){document.body.classList.add('openingFolder');setTimeout(()=>document.body.classList.remove('openingFolder'),500);selected=group;screen=group?'folder':'home';$('#search').value='';closeLayers();showScreen();reload();window.scrollTo(0,0)}
function allDocs(){selected='';screen='home';$('#search').value='';closeLayers();showScreen();reload();window.scrollTo(0,0)}
function activate(tab){document.querySelectorAll('.bottom .nav').forEach(n=>n.classList.toggle('active',n.dataset.tab===tab))}
function openGroups(){selected='';screen='groups';$('#search').value='';closeLayers();showScreen();reload();window.scrollTo(0,0)}
function backupNow(){openBackupMenu()}
function drawer(on){drawerOpen=on;$('#drawer').classList.toggle('open',on);$('#shade').classList.toggle('show',on)}
function openBackupMenu(){drawer(false);drawerOpen=true;activate('drive');$('#backupSheet').classList.add('open');$('#shade').classList.add('show')}
function closeLayers(){drawerOpen=false;$('#drawer').classList.remove('open');$('#backupSheet').classList.remove('open');$('#groupsSheet').classList.remove('open');$('#shade').classList.remove('show');activate(screen==='groups'||screen==='folder'?'groups':'home')}
function handleBack(){if(drawerOpen||$('#shade').classList.contains('show')){closeLayers();return}if(screen==='folder'){openGroups();return}if(screen==='groups'){allDocs();return}Archive.exit()}
function toast(message){const x=$('#toast');x.textContent=message;x.classList.add('show');setTimeout(()=>x.classList.remove('show'),2400)}
window.renderArchive=renderArchive;window.handleBack=handleBack;

const themeIcons={light:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="4" fill="#ffcd64"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/></svg>',dark:'<svg viewBox="0 0 24 24" fill="#99a9f9" stroke="currentColor" stroke-width="1.6"><path d="M20 15.2A8.5 8.5 0 0 1 8.8 4 8.5 8.5 0 1 0 20 15.2Z"/><path d="M17 3v4m-2-2h4"/></svg>'};
function applyTheme(theme){document.body.dataset.theme=theme;const light=theme==='light';const button=$('#themeToggle');button.innerHTML=themeIcons[light?'dark':'light'];button.setAttribute('aria-label',light?'تفعيل الثيم الغامق':'تفعيل الثيم الفاتح');button.setAttribute('aria-pressed',String(light));}
function toggleTheme(){const theme=document.body.dataset.theme==='light'?'dark':'light';applyTheme(theme);try{localStorage.setItem('archiveTheme',theme)}catch(e){}}
try{applyTheme(localStorage.getItem('archiveTheme')||'dark')}catch(e){applyTheme('dark')}
const tabIcons={home:'<svg viewBox="0 0 28 28"><path d="M3 13 14 3l11 10v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z" fill="#87dfbf"/><path d="M2 13 14 2l12 11" fill="none" stroke="#cef8e8" stroke-width="2.5" stroke-linecap="round"/><rect x="11" y="16" width="6" height="10" rx="2" fill="#287866"/><rect x="6" y="13" width="4" height="4" rx="1" fill="#edfff9"/></svg>',groups:'<svg viewBox="0 0 28 28"><path d="M3 6a3 3 0 0 1 3-3h6l3 4h7a3 3 0 0 1 3 3v13H3Z" fill="#ffcc71"/><path d="M2 12a2 2 0 0 1 2-2h21l-2 14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z" fill="#ffab57"/><path d="M8 15h12" stroke="#ffe6ac" stroke-width="2" stroke-linecap="round"/></svg>',drive:'<svg viewBox="0 0 28 28"><path d="M10 3h8l9 16h-8Z" fill="#ffd166"/><path d="M10 3 1 19h9l8-16Z" fill="#60d394"/><path d="M1 19l4 7h18l4-7Z" fill="#79b7ff"/></svg>'};
document.querySelectorAll('.bottom .nav').forEach(n=>{n.querySelector('svg').outerHTML=tabIcons[n.dataset.tab]});
