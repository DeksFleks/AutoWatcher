const { chromium } = require("playwright");
const { spawn } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

(async () => {
  const temporary=fs.mkdtempSync(path.join(os.tmpdir(),"autowatcher-browser-"));
  fs.mkdirSync("target/browser",{recursive:true});
  const logfile=fs.openSync("target/browser/application.log","w");
  const app=spawn("java",["-jar","target/autowatcher-0.1.0-SNAPSHOT.jar","--spring.profiles.active=demo"],
    {env:{...process.env,AUTOWATCHER_DATA:temporary,AUTOWATCHER_PORT:"18081"},stdio:["ignore",logfile,logfile]});
  let browser;
  try {
    const deadline=Date.now()+90000;
    while(true){
      if(app.exitCode!==null)throw new Error("App exited");
      try{const r=await fetch("http://127.0.0.1:18081/actuator/health");if(r.ok)break;}catch(e){}
      if(Date.now()>deadline)throw new Error("App startup timed out");
      await new Promise(resolve=>setTimeout(resolve,1000));
    }
    browser=await chromium.launch({headless:true});
    const page=await browser.newPage({viewport:{width:1440,height:1000}});
    const errors=[];page.on("pageerror",error=>errors.push(error.message));
    await page.goto("http://127.0.0.1:18081");
    await page.getByRole("button",{name:"+ Новый фильтр",exact:true}).click();
    await page.getByLabel("Название",{exact:true}).fill("Browser BMW");
    await page.getByLabel("Марка",{exact:true}).fill("BMW");
    await page.getByLabel("Цена до, ₽",{exact:true}).fill("3000000");
    await page.getByRole("button",{name:"Сохранить фильтр",exact:true}).click();
    await page.locator(".profile-card h3").filter({hasText:"Browser BMW"}).waitFor();
    await page.locator(".profile-card").getByRole("button",{name:"Проверить",exact:true}).click();
    await page.getByRole("button",{name:"Объявления",exact:true}).click();
    await page.locator(".result").first().waitFor({timeout:45000});
    if(await page.locator(".result").count()!==4)throw new Error("Expected four fixture results");
    await page.getByRole("button",{name:"Фильтры поиска",exact:true}).click();
    await page.getByRole("button",{name:"Изменить",exact:true}).click();
    await page.getByLabel("Название",{exact:true}).fill("Browser BMW updated");
    await page.getByRole("button",{name:"Сохранить фильтр",exact:true}).click();
    await page.locator(".profile-card h3").filter({hasText:"Browser BMW updated"}).waitFor();
    await page.reload();
    await page.locator(".profile-card h3").filter({hasText:"Browser BMW updated"}).waitFor();
    await page.screenshot({path:"target/browser/desktop.png",fullPage:true});
    await page.setViewportSize({width:390,height:844});
    await page.screenshot({path:"target/browser/mobile.png",fullPage:true});
    await page.getByRole("button",{name:"Настройки",exact:true}).click();
    await page.getByRole("heading",{name:"Новые объявления в твоём чате"}).waitFor();
    if(errors.length)throw new Error(errors.join("\n"));
    console.log("Browser UI: create, check, results, edit, reload, settings and responsive screenshots passed");
  } finally {
    if(browser)await browser.close();
    app.kill();
    await new Promise(resolve=>{if(app.exitCode!==null)return resolve();app.once("exit",resolve);setTimeout(()=>{app.kill("SIGKILL");resolve();},15000);});
    fs.closeSync(logfile);
  }
})().catch(error=>{console.error(error);process.exitCode=1;});
