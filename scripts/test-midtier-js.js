// Optional executable test for Java-generated browser scripts. Only local mocks; no network traffic.
const fs = require('fs');
const vm = require('vm');
const output = fs.readFileSync(process.argv[2], 'utf8');
function script(key) {
  const line = output.split(/\r?\n/).find(s => s.startsWith(key + '='));
  if (!line) throw Error('missing ' + key);
  return Buffer.from(line.split('=')[1], 'base64').toString('utf8');
}
function run(mode, options={}) {
  const formMode = mode === 'FORM';
  let submitted = false;
  const elements = Object.assign({cfgStok:{value:'LIVE_TEST_TOKEN'}, server:{options:[{value:'other'},{value:'arserver'}],selectedIndex:0},
    form_name:{value:''}, form_flush:{value:'No'}, cache_flush:{value:'No'}, cache_flush_allpods:{value:'No'}},options.elements || {});
  const action = formMode ? 'http://ars.sandbox:8080/arsys/servlet/GoatConfigServlet?action=Cache&op_type=form_cleanup'
    : 'http://ars.sandbox:8080/arsys/servlet/GoatConfigServlet?action=Cache';
  const form = {elements,action:options.action || action, submit(){submitted=true;}};
  const forms = {[formMode ? 'cache_clenaup_form' : 'flush_cache_form']:form};
  const location = new URL('http://ars.sandbox:8080/arsys/shared/config/' + (formMode?'config_cache_cleanup.jsp':'config_cache.jsp'));
  const document = { forms,createElement(tag){
    if(tag!=='a')throw Error('unexpected DOM element');
    let parsed; let link={}; Object.defineProperty(link,'href',{set(v){parsed=new URL(v);}}); ['protocol','hostname','port','pathname','search'].forEach(k=>Object.defineProperty(link,k,{get(){return parsed[k];}}));return link;
  }};
  const code = script(mode+'_JS_BASE64');
  const result = vm.runInNewContext(code,{document,location});
  return {result,submitted,elements};
}
const assert = require('assert');
let count = 0;
function test(mode, options, expected, submitted) {
 const got = run(mode,options);
 assert.strictEqual(got.result,expected);
 assert.strictEqual(got.submitted,submitted);
 count++;
 return got;
}
const form = test('FORM',{},'SUBMITTED',true);
assert.strictEqual(form.elements.server.selectedIndex,1);
assert.strictEqual(form.elements.form_name.value,"HLX:O'Brien");
assert.strictEqual(form.elements.form_flush.value,'Yes');
const full = test('FULL',{},'SUBMITTED',true);
assert.strictEqual(full.elements.cache_flush.value,'Yes');
assert.strictEqual(full.elements.cache_flush_allpods.value,'No');
const pods = test('PODS',{},'SUBMITTED',true);
assert.strictEqual(pods.elements.cache_flush_allpods.value,'Yes');
assert.strictEqual(pods.elements.cache_flush.value,'No');
test('FORM',{elements:{cfgStok:{value:''}}},'NO_TOKEN',false);
test('FORM',{elements:{server:{options:[{value:'other'}]}}},'NO_SERVER',false);
test('FORM',{action:'https://evil.example.com/arsys/servlet/GoatConfigServlet?action=Cache&op_type=form_cleanup'},'BAD_ACTION',false);
test('FULL',{action:'http://ars.sandbox:8080/arsys/other?action=Cache'},'BAD_ACTION',false);
test('PODS',{action:'http://ars.sandbox:8080/arsys/servlet/GoatConfigServlet?action=Other'},'BAD_ACTION',false);
console.log('MidTier JavaScript DOM / submission tests: '+count+' PASS');
