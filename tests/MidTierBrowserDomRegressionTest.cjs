'use strict';
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const scripts = Object.fromEntries(fs.readFileSync(process.argv[2], 'utf8').trim().split('\n')
  .map(line => { const pos = line.indexOf('='); return [line.slice(0, pos), Buffer.from(line.slice(pos + 1), 'base64').toString()]; }));
let checks = 0;
function check(a,b,message) { assert.equal(a,b,message); checks++; }

function fixture(type, opts={}) {
  let posted = 0;
  const host = 'https://mt.example';
  const base = host + '/arsys';
  const at = opts.pageUrl || (base + '/shared/config/config_cache_cleanup.jsp');
  const fields = {
    username: {value:''}, pwd: {value:''}, servername: {value:'arserver',disabled:false},
    timezone: {value:''},isFromCfgTool:{value:'true'},
    cfgStok: {value:opts.missingToken?'':'TEST-TOKEN'},
    server: {value:'', options:[{value:''},{value:'arserver'}]},
    form_name:{value:''}, form_flush:{value:'Yes'},
    cache_flush:{value:'No'}, cache_flush_allpods:{value:'No'}
  };
  const serverVisible = {value:'arserver',disabled:true};
  const loginForm = {method:'post',getAttribute(name){return name==='action'?
     (opts.loginAction || base+'/servlet/ConfigServlet?action=logon'):null;},
     querySelector(query){const m=query.match(/name="([^"]+)"/);return m?fields[m[1]]||null:null;},
     querySelectorAll(query){return query.indexOf('servername')>=0?[serverVisible,fields.servername]:[];},
     submit(){posted++;}};
  const cacheForm = {getAttribute(name){return name==='action'?
    (opts.cacheAction || base+'/servlet/GoatConfigServlet?action=Cache&op_type=form_cleanup'):null;},
    elements:{namedItem(name){return fields[name]||null;}}, submit(){posted++;}};
  const fullForm = {getAttribute(name){return name==='action'?
    (opts.cacheAction || base+'/servlet/GoatConfigServlet?action=Cache'):null;},
    elements:{namedItem(name){return fields[name]||null;}}, submit(){posted++;}};
  const forms = {namedItem(name){
    if(name==='loginForm')return type==='login'?loginForm:null;
    if(name==='cache_clenaup_form')return type==='form'?cacheForm:null;
    if(name==='flush_cache_form')return type==='all'?fullForm:null;
    return null;
  }};
  const document = {
    forms,
    body:{textContent: opts.denied?'Configuration Tool authorization failed. Click Configuration Tool to log in.':opts.body||''},
    createElement(tag) {
      assert.equal(tag,'a');
      let target=new URL(at);
      const element={};
      Object.defineProperty(element,'href',{
        set(value){target=new URL(value,at);},
        get(){return target.href;}
      });
      for(const prop of ['protocol','hostname','port','pathname','search']){
        Object.defineProperty(element,prop,{get(){return target[prop];}});
      }
      return element;
    },
    querySelectorAll(query){
      assert.equal(query,'.status,.error,.LoginError');
      return (opts.messages||[]).map(text=>({textContent:text}));
    }
  };
  const sandbox=vm.createContext({document,location:{href:at}});
  // Model the API surface of the older MSHTML engine. The tested scripts must
  // work even when Chrome-only/ES6 APIs aren't present.
  vm.runInContext(`
    Array.from = undefined;
    String.prototype.includes = undefined;
    Array.prototype.includes = undefined;
    URL = undefined;
    URLSearchParams = undefined;
  `,sandbox);
  return {sandbox,fields,loginForm,cacheForm,fullForm,get posted(){return posted;}};
}
function run(key,f){return vm.runInContext(scripts[key],f.sandbox,{timeout:1500});}

for (const [key,code] of Object.entries(scripts)) {
  assert.ok(!/\.includes\s*\(|Array\.from\s*\(|new\s+URL\s*\(|\.searchParams\b/.test(code),`${key} contains incompatible browser API`);
  checks++;
}
let f=fixture('none',{denied:true});check(run('state',f),'CONFIG_DENIED','authorization failure');
f=fixture('login');check(run('state',f),'CONFIG_LOGIN','login screen');
check(run('check',f),'READY','login form safe');
check(run('submit',f),true,'login submission');
check(f.posted,1,'login posted once');
check(f.fields.username.value,'tester','username');
check(f.fields.pwd.value,'p\\"ss;alert(1)//','password string safely escaped');
check(f.fields.servername.value,'arserver','server field');
f=fixture('login',{loginAction:'https://evil.example/servlet/ConfigServlet?action=logon'});
check(run('check',f),'UNEXPECTED_LOGIN_ACTION','cross origin login denied');
check(run('submit',f),false,'cross origin login not posted');check(f.posted,0,'no credentials sent');
f=fixture('login',{loginAction:'https://mt.example/arsys/servlet/ConfigServlet?action=logon&bogus=1'});
check(run('check',f),'UNEXPECTED_LOGIN_ACTION','extra query parameter login denied');
f=fixture('form');check(run('state',f),'CACHE_FORM','form recognized');
check(run('preflightForm',f),'READY','form preflight');
check(run('submitForm',f),true,'form submit');check(f.posted,1,'form sent once');
check(f.fields.form_name.value,'User','correct form');check(f.fields.form_flush.value,'Yes','flush flag');
f=fixture('form',{missingToken:true});check(run('preflightForm',f),'ERROR: Missing cfgStok','token required');
f=fixture('form',{cacheAction:'https://evil.example/arsys/servlet/GoatConfigServlet?action=Cache&op_type=form_cleanup'});
check(run('preflightForm',f),'ERROR: Unexpected form action','no external target');
check(run('submitForm',f),false,'no external submit');check(f.posted,0,'no flush sent');
f=fixture('form',{cacheAction:'https://mt.example/arsys/servlet/GoatConfigServlet?op_type=form_cleanup&action=Cache'});
check(run('preflightForm',f),'READY','reordered valid parameters');
f=fixture('form',{cacheAction:'https://mt.example/arsys/servlet/GoatConfigServlet?action=Cache&op_type=form_cleanup&action=Cache'});
check(run('preflightForm',f),'ERROR: Invalid cleanup action','duplicate params denied');
f=fixture('all');check(run('preflightAll',f),'READY','full flush preflight');check(run('submitAll',f),true,'full flush submitted');
check(f.fields.cache_flush.value,'Yes','full flush flag');check(f.fields.cache_flush_allpods.value,'No','all pods off');
f=fixture('all');check(run('preflightPods',f),'READY','pods preflight');check(run('submitPods',f),true,'pods flush submitted');
check(f.fields.cache_flush.value,'No','full flush off');check(f.fields.cache_flush_allpods.value,'Yes','all pods flag');
f=fixture('none',{messages:['Form User for server arserver is cleaned !']});
check(run('result',f),'Form User for server arserver is cleaned !','success response text');
f=fixture('none',{denied:true});check(run('result',f),'CONFIG_DENIED','auth failure response');
console.log('Legacy SWT Browser ES5 DOM simulation: '+checks+' PASS');
