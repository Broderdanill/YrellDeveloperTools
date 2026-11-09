const fs = require('node:fs'), vm = require('node:vm'), assert = require('node:assert/strict');
const script = Object.fromEntries(fs.readFileSync(process.argv[2], 'utf8').trim().split('\n').map(x=>{const j=x.indexOf('=');return [x.slice(0,j),Buffer.from(x.slice(j+1),'base64').toString()]}));
function mock({denied=false,login=true,evil=false}={}){
 let count=0;
 const fields={username:{value:''}, pwd:{value:''},servername:{value:'arserver'},timezone:{value:''},isFromCfgTool:{value:'true'}};
 const serverVisible={value:'arserver',disabled:true};
 const form={method:'post', getAttribute:(name)=>name==='action'?(evil?'https://evil.example/login':'https://mt.example/arsys/servlet/ConfigServlet?action=logon'):'',
  querySelector:(q)=>fields[q.match(/name="([^"]+)"/)[1]],
  querySelectorAll:(q)=>q.includes('servername')?[serverVisible, fields.servername]:[],
  submit(){count++}};
 const document={forms:{namedItem:(x)=>x==='loginForm'&&login?form:null},body:{textContent:denied?'Configuration Tool authorization failed. Click Configuration Tool to log in.':''}};
 const context={document, location:{href:'https://mt.example/arsys/shared/config/config.jsp'},URL,Intl};
 return {context,fields,get count(){return count}};
}
function run(code,context){ return vm.runInNewContext(code,context,{timeout:1000}); }
let c=mock({denied:true,login:false});assert.equal(run(script.state,c.context),'CONFIG_DENIED');
c=mock();assert.equal(run(script.state,c.context),'CONFIG_LOGIN');
assert.equal(run(script.check,c.context),'READY');
assert.equal(run(script.submit,c.context),true);
assert.equal(c.count,1);assert.equal(c.fields.username.value,'tester');assert.equal(c.fields.pwd.value,'p\\"ss;alert(1)//');
assert.equal(c.fields.servername.value,'arserver');
c=mock({evil:true});assert.equal(run(script.check,c.context),'UNEXPECTED_LOGIN_ACTION');
assert.equal(run(script.submit,c.context),false);assert.equal(c.count,0);
c=mock({denied:true,login:false});assert.equal(run(script.result,c.context),'CONFIG_DENIED');
console.log('JavaScript DOM simulation: 11 PASS');
