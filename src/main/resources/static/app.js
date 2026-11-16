"use strict";
let csrf="",demo=false,profiles=[],selected="",currentTab="profiles";
const $=id=>document.getElementById(id);
const labels={MATCH:"Подходит",UNKNOWN:"Недостаточно данных",NO_MATCH:"Не подходит",SUCCEEDED:"Успешно",FAILED:"Ошибка",RUNNING:"Выполняется",INTERRUPTED:"Прервано",PENDING:"В очереди",DELIVERED:"Доставлено",DEAD:"Нужен повтор"};
function node(tag,text,cls){const el=document.createElement(tag);if(text!==undefined)el.textContent=text;if(cls)el.className=cls;return el;}
function notice(text,error=false){const box=$("notice");box.hidden=false;box.textContent=text;box.classList.toggle("error",error);}
async function api(path,method="GET",body){
  const response=await fetch("/api/v1"+path,{method,headers:{"Content-Type":"application/json","X-AutoWatcher-CSRF":csrf},body:body===undefined?undefined:JSON.stringify(body)});
  if(!response.ok){let detail;try{detail=(await response.json()).detail;}catch(e){}throw new Error(detail||"Запрос не выполнен ("+response.status+")");}
  const text=await response.text();return text?JSON.parse(text):null;
}
async function action(fn){try{await fn();}catch(e){notice(e.message,true);}}
function badge(status){return node("span",labels[status]||status,"status"+(["UNKNOWN","PENDING","RUNNING"].includes(status)?" warn":["FAILED","DEAD","INTERRUPTED"].includes(status)?" fail":""));}
function button(text,fn,cls){const b=node("button",text,cls);b.type="button";b.addEventListener("click",()=>action(fn));return b;}
function empty(container,text){container.replaceChildren(node("div",text,"empty"));}
async function refreshProfiles(){
  profiles=await api("/profiles");$("profile-count").textContent="("+profiles.length+")";
  if(!profiles.some(p=>p.id===selected))selected=profiles[0]?.id||"";
  for(const name of ["results-profile","history-profile"]){
    const select=$(name);select.replaceChildren();
    for(const p of profiles){const option=node("option",p.name);option.value=p.id;select.append(option);}
    select.value=selected;
  }
  const list=$("profile-list");list.replaceChildren();$("empty-profiles").hidden=profiles.length!==0;
  for(const p of profiles){
    const card=node("article",undefined,"card profile-card");const top=node("div",undefined,"topline");
    top.append(node("span",p.sources.join(" · "),"badge"),node("span",p.enabled?"Активен":"На паузе","muted"));
    card.append(top,node("h3",p.name));
    const c=p.criteria;const lines=[c.brand,c.model,c.yearFrom||c.yearTo?[c.yearFrom||"…",c.yearTo||"…"].join("–"):null,c.priceTo?"до "+Number(c.priceTo).toLocaleString("ru")+" ₽":null,c.region].filter(Boolean);
    card.append(node("p",lines.join(" · ")||"Без ограничений"));
    const actions=node("div",undefined,"actions");
    actions.append(button("Проверить",async()=>{await api("/profiles/"+p.id+"/checks/queue","POST");notice("Проверка в очереди. Результат появится в истории.");},"primary"),
      button("Изменить",()=>editProfile(p)),
      button(p.enabled?"Пауза":"Включить",async()=>{await api("/profiles/"+p.id+"/status","PATCH",{enabled:!p.enabled});await refreshProfiles();}),
      button("Удалить",async()=>{if(confirm("Удалить фильтр «"+p.name+"» и его историю?")){await api("/profiles/"+p.id,"DELETE");await refreshProfiles();}},"danger"));
    card.append(actions);list.append(card);
  }
}
async function editProfile(profile){
  const form=$("profile-form");form.reset();form.hidden=false;$("editor-title").textContent=profile?"Изменить фильтр":"Новый фильтр";
  form.elements.id.value=profile?.id||"";
  for(const key of ["name","brand","model","yearFrom","yearTo","priceFrom","priceTo","mileageTo","region"])
    form.elements[key].value=(key==="name"?profile?.name:profile?.criteria[key])??"";
  for(const input of form.querySelectorAll('input[name="source"]'))input.checked=(profile?.sources||[]).includes(input.value);
  if(!profile)form.querySelector('input[value="'+(demo?"FAKE":"AVITO")+'"]').checked=true;
  if(profile){
    const config=await api("/profiles/"+profile.id+"/monitoring");
    form.elements.intervalMinutes.value=config.intervalSeconds/60;form.elements.scheduled.checked=config.scheduled;
    form.elements.avitoUrl.value=config.avitoUrl||"";form.elements.autoRuUrl.value=config.autoRuUrl||"";
  }
  form.scrollIntoView({behavior:"smooth",block:"start"});form.elements.name.focus();
}
async function saveProfile(event){
  event.preventDefault();const form=event.target;const submit=form.querySelector('button[type="submit"]');submit.disabled=true;
  try{
    const criteria={};for(const key of ["brand","model","region"])criteria[key]=form.elements[key].value.trim()||null;
    for(const key of ["yearFrom","yearTo","priceFrom","priceTo","mileageTo"]){const v=form.elements[key].value;criteria[key]=v===""?null:Number(v);}
    const sources=[...form.querySelectorAll('input[name="source"]:checked')].map(i=>i.value);
    if(!sources.length)throw new Error("Выбери хотя бы одну площадку");
    const config={intervalSeconds:Math.round(Number(form.elements.intervalMinutes.value)*60),scheduled:form.elements.scheduled.checked,
      avitoUrl:form.elements.avitoUrl.value.trim()||null,autoRuUrl:form.elements.autoRuUrl.value.trim()||null};
    for(const [source,key,host]of [["AVITO","avitoUrl","avito.ru"],["AUTO_RU","autoRuUrl","auto.ru"]]){
      if(sources.includes(source)&&!config[key])throw new Error("Добавь ссылку поиска для "+source);
      if(config[key]){const u=new URL(config[key]);if(u.protocol!=="https:"||![host,"www."+host].includes(u.hostname)||u.port||u.username)throw new Error("Проверь ссылку "+source);}
    }
    const id=form.elements.id.value;const profile=await api("/profiles"+(id?"/"+id:""),id?"PUT":"POST",{name:form.elements.name.value.trim(),criteria,sources});
    form.elements.id.value=profile.id;
    await api("/profiles/"+profile.id+"/monitoring","PUT",config);
    selected=profile.id;form.hidden=true;notice("Фильтр сохранён");await refreshProfiles();
  }catch(e){notice(e.message,true);}finally{submit.disabled=false;}
}
async function refreshResults(){
  const container=$("result-list");if(!selected){empty(container,"Сначала создай фильтр поиска");return;}
  const rows=await api("/profiles/"+selected+"/results");container.replaceChildren();
  if(!rows.length){empty(container,"Объявлений пока нет. Запусти проверку и посмотри историю.");return;}
  for(const r of rows){
    const card=node("article",undefined,"card result");const left=node("div");left.append(badge(r.result));
    const title=node("h3");const link=node("a",r.title);try{const url=new URL(r.url);if(url.protocol==="https:"){link.href=url.href;link.target="_blank";link.rel="noreferrer";}}catch(e){}
    title.append(link);left.append(title,node("p",[r.source,r.year,r.mileage===null?null:Number(r.mileage).toLocaleString("ru")+" км",r.region].filter(Boolean).join(" · ")),
      node("p","Последняя проверка: "+new Date(r.checked_at).toLocaleString("ru")));
    card.append(left,node("div",r.price===null?"Цена не указана":Number(r.price).toLocaleString("ru")+" ₽","price"));container.append(card);
  }
}
function table(container,headers,rows){const wrap=node("div",undefined,"table-wrap");const t=node("table");const head=node("tr");headers.forEach(h=>head.append(node("th",h)));t.append(head);for(const row of rows){const tr=node("tr");for(const cell of row){const td=node("td");td.append(cell instanceof Node?cell:document.createTextNode(String(cell??"—")));tr.append(td);}t.append(tr);}wrap.append(t);container.replaceChildren(wrap);}
async function refreshHistory(){
  if(!selected){empty($("run-list"),"Сначала создай фильтр");$("outbox-list").replaceChildren();return;}
  const [runs,outbox]=await Promise.all([api("/profiles/"+selected+"/runs"),api("/profiles/"+selected+"/outbox")]);
  if(runs.length)table($("run-list"),["Когда","Площадка","Статус","Подробности"],runs.map(r=>[new Date(r.started_at).toLocaleString("ru"),r.source,badge(r.status),r.error_message||""]));else empty($("run-list"),"Проверок пока нет");
  table($("outbox-list"),["Канал","Статус","Попыток","Подробности"],outbox.map(o=>[o.channel,badge(o.status),o.attempts,o.error_code||""]));
}
async function refreshSettings(includeRetention=true){const s=await api("/settings");$("telegram-status").textContent=(s.telegramConfigured?"Токен сохранён. ":"")+s.telegramStatus;if(includeRetention)$("retention-form").elements.days.value=s.retentionDays;}
function showPair(value){$("pair-command").textContent=value.command;$("pairing").hidden=false;notice("Токен сохранён. Отправь команду своему боту.");}
async function switchTab(tab){currentTab=tab;for(const section of document.querySelectorAll(".panel"))section.hidden=section.id!==tab;for(const b of document.querySelectorAll("nav button"))b.classList.toggle("active",b.dataset.tab===tab);
  const titles={profiles:"Фильтры поиска",results:"Объявления",history:"История проверок",settings:"Настройки"};$("page-title").textContent=titles[tab];
  if(tab==="results")await refreshResults();if(tab==="history")await refreshHistory();if(tab==="settings")await refreshSettings();}
document.querySelectorAll("nav button").forEach(b=>b.addEventListener("click",()=>action(()=>switchTab(b.dataset.tab))));
$("new-profile").addEventListener("click",()=>action(()=>editProfile(null)));$("cancel-edit").addEventListener("click",()=>$("profile-form").hidden=true);$("profile-form").addEventListener("submit",saveProfile);
for(const name of ["results-profile","history-profile"])$(name).addEventListener("change",e=>action(async()=>{selected=e.target.value;await switchTab(currentTab);}));
$("check-selected").addEventListener("click",()=>action(async()=>{if(!selected)return;await api("/profiles/"+selected+"/checks/queue","POST");notice("Проверка запущена в фоне. Результат появится после загрузки площадок.");}));
$("refresh-history").addEventListener("click",()=>action(refreshHistory));
$("retry-notifications").addEventListener("click",()=>action(async()=>{if(selected){await api("/profiles/"+selected+"/outbox/retry","POST");notice("Неудачные отправки возвращены в очередь");await refreshHistory();}}));
$("telegram-form").addEventListener("submit",e=>{e.preventDefault();action(async()=>{showPair(await api("/settings/telegram","PUT",{token:e.target.elements.token.value}));e.target.reset();await refreshSettings();});});
$("pair-again").addEventListener("click",()=>action(async()=>showPair(await api("/settings/telegram/pair","POST"))));
$("disconnect-telegram").addEventListener("click",()=>action(async()=>{await api("/settings/telegram","DELETE");$("pairing").hidden=true;await refreshSettings();notice("Telegram отключён");}));
$("copy-pair").addEventListener("click",()=>action(async()=>{await navigator.clipboard.writeText($("pair-command").textContent);notice("Команда скопирована");}));
$("retention-form").addEventListener("submit",e=>{e.preventDefault();action(async()=>{await api("/settings/retention","PUT",{days:Number(e.target.elements.days.value)});notice("Срок хранения сохранён");});});
action(async()=>{const session=await api("/session");csrf=session.csrfToken;demo=session.demo;$("fake-option").hidden=!demo;await refreshProfiles();$("connection").textContent=demo?"ДЕМО-РЕЖИМ":"ЛОКАЛЬНО";});
setInterval(()=>action(async()=>{if($("profile-form").hidden)await refreshProfiles();if(currentTab==="results")await refreshResults();if(currentTab==="history")await refreshHistory();if(currentTab==="settings")await refreshSettings(false);}),5000);
