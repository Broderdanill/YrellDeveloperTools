package se.yrell.developertools.midtier;

/**
 * JavaScript evaluated inside SWT Browser. On Windows, the browser may still
 * run the legacy MSHTML / IE engine rather than Chromium/WebView2. Consequently
 * every expression must be ES5-compatible (no String.includes, Array.from,
 * URL constructor, URLSearchParams, arrow functions or similar APIs).
 *
 * Scripts are only evaluated on the configured MidTier origin, never on an IdP.
 * The browser owns its HttpOnly cookies and cfgStok: Java never copies them.
 */
public final class MidTierBrowserScript {
    private MidTierBrowserScript() { }

    private static String jsString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\r' -> out.append("\\r");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32 || c == 0x2028 || c == 0x2029)
                        out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    /** All helpers in this snippet are ES5-compatible, including MSHTML.
     * Using an HTML anchor to parse a URL is supported even by old browsers.
     * Query validation rejects duplicate/unknown fields, not only bad values.
     */
    private static final String ES5_HELPERS = """
        function getForm(name){
          return document.forms.namedItem ? document.forms.namedItem(name) : document.forms[name];
        }
        function getElement(form,name){
          return form.elements.namedItem ? form.elements.namedItem(name) : form.elements[name];
        }
        function link(raw){
          var el=document.createElement('a');
          el.href=raw;
          return el;
        }
        function endpointPath(url){
          return ('/'+String(url.pathname||'')).replace(/^\\/+/, '/');
        }
        function endpointPort(url){
          if(url.port)return String(url.port);
          if(String(url.protocol).toLowerCase()==='https:')return '443';
          if(String(url.protocol).toLowerCase()==='http:')return '80';
          return '?';
        }
        function sameEndpoint(actual,wanted){
          return String(actual.protocol).toLowerCase()===String(wanted.protocol).toLowerCase()
            && String(actual.hostname).toLowerCase()===String(wanted.hostname).toLowerCase()
            && endpointPort(actual)===endpointPort(wanted)
            && endpointPath(actual)===endpointPath(wanted);
        }
        function queryEquals(search,expected){
          var q=String(search||'').replace(/^\\?/, '');
          var parts=q?q.split('&'):[];
          if(parts.length!==expected.length)return false;
          var seen={};
          for(var i=0;i<parts.length;i++){
            var pair=parts[i].split('=');
            if(pair.length!==2)return false;
            var key,value;
            try{
              key=decodeURIComponent(pair[0].replace(/\\+/g,' '));
              value=decodeURIComponent(pair[1].replace(/\\+/g,' '));
            }catch(ignore){return false;}
            if(Object.prototype.hasOwnProperty.call(seen,key))return false;
            var matched=false;
            for(var j=0;j<expected.length;j++){
              if(key===expected[j][0]&&value===expected[j][1]){matched=true;break;}
            }
            if(!matched)return false;
            seen[key]=true;
          }
          return true;
        }
        function bodyText(){
          var b=document.body;
          if(!b)return '';
          return String(typeof b.textContent==='string'?b.textContent:(b.innerText||''));
        }
        function trimText(s){return String(s).replace(/^\\s+|\\s+$/g,'');}
        """;

    /** Read-only classification of the current MidTier Configuration Tool page. */
    public static String pageState() {
        return "(function(){" + ES5_HELPERS
                + "if(getForm('loginForm'))return 'CONFIG_LOGIN';"
                + "var content=bodyText().replace(/\\s+/g,' ').toLowerCase();"
                + "if(content.indexOf('configuration tool authorization failed')!==-1)return 'CONFIG_DENIED';"
                + "if(getForm('cache_clenaup_form')||getForm('flush_cache_form'))return 'CACHE_FORM';"
                + "return 'OTHER';})()";
    }

    /** Verify BMC's own Configuration Tool login action on the exact MidTier. */
    public static String configLoginCheck(String baseUrl) {
        String wanted = MidTierConfiguration.normalizeBaseUrl(baseUrl) + "/servlet/ConfigServlet";
        return "(function(){" + ES5_HELPERS
                + "var f=getForm('loginForm');if(!f)return 'NO_LOGIN_FORM';"
                + "var actual=link(f.getAttribute('action')||'');"
                + "var wanted=link(" + jsString(wanted) + ");"
                + "if(!sameEndpoint(actual,wanted)||!queryEquals(actual.search,[['action','logon']])"
                + "||String(f.method||'').toLowerCase()!=='post')return 'UNEXPECTED_LOGIN_ACTION';"
                + "if(!f.querySelector('input[name=\"username\"]')||!f.querySelector('input[name=\"pwd\"]')"
                + "||!f.querySelector('input[name=\"servername\"]'))return 'MISSING_LOGIN_FIELDS';"
                + "var cfg=f.querySelector('input[name=\"isFromCfgTool\"]');"
                + "if(!cfg||cfg.value!=='true')return 'NOT_CONFIG_TOOL_LOGIN';"
                + "return 'READY';})()";
    }

    /** Submit only BMC's verified Configuration Tool form, never an IdP form. */
    public static String submitConfigLogin(String baseUrl, String server, String user, String password) {
        return "(function(){" + ES5_HELPERS
                + "if(" + configLoginCheck(baseUrl) + "!=='READY')return false;"
                + "var f=getForm('loginForm');"
                + "f.querySelector('input[name=\"username\"]').value=" + jsString(user) + ";"
                + "f.querySelector('input[name=\"pwd\"]').value=" + jsString(password) + ";"
                + "var fields=f.querySelectorAll('input[name=\"servername\"]');"
                + "for(var i=0;i<fields.length;i++){if(!fields[i].disabled)fields[i].value=" + jsString(server) + ";}"
                + "var tz=f.querySelector('input[name=\"timezone\"]');"
                + "if(tz){var zone='Europe/Stockholm';try{"
                + "if(typeof Intl!=='undefined'&&Intl.DateTimeFormat){"
                + "zone=Intl.DateTimeFormat().resolvedOptions().timeZone||zone;}}catch(ignore){}tz.value=zone;}"
                + "f.submit();return true;})()";
    }

    /** Validates a cache form without triggering the operation. */
    public static String preflight(String baseUrl, int mode, String server) {
        String target = mode == 0 ? "cache_clenaup_form" : "flush_cache_form";
        String wanted = MidTierConfiguration.normalizeBaseUrl(baseUrl) + "/servlet/GoatConfigServlet";
        return "(function(){" + ES5_HELPERS
                + "var form=getForm(" + jsString(target) + ");"
                + "if(getForm('loginForm'))return 'LOGIN_REQUIRED';"
                + "if(!form)return 'NOT_CACHE_PAGE';"
                + "var token=getElement(form,'cfgStok');"
                + "if(!token||!token.value)return 'ERROR: Missing cfgStok';"
                + "var action=link(form.getAttribute('action')||'');"
                + "var wanted=link(" + jsString(wanted) + ");"
                + "if(!sameEndpoint(action,wanted))return 'ERROR: Unexpected form action';"
                + (mode == 0
                   ? "if(!queryEquals(action.search,[['action','Cache'],['op_type','form_cleanup']]))"
                     + "return 'ERROR: Invalid cleanup action';"
                   : "if(!queryEquals(action.search,[['action','Cache']]))"
                     + "return 'ERROR: Unexpected cleanup mode';")
                + (mode == 0
                   ? "var sel=getElement(form,'server');if(!sel||!sel.options)return 'ERROR: Missing server list';"
                     + "var found=false;for(var i=0;i<sel.options.length;i++){"
                     + "if(sel.options[i].value===" + jsString(server) + "){found=true;break;}}"
                     + "if(!found)return 'ERROR: AR server not in MidTier list';"
                     + "if(!getElement(form,'form_name')||!getElement(form,'form_flush'))"
                     + "return 'ERROR: Form inputs missing';"
                   : "if(!getElement(form,'cache_flush')||!getElement(form,'cache_flush_allpods'))"
                     + "return 'ERROR: Cache inputs missing';")
                + "return 'READY';})()";
    }

    /** Submits BMC's original form, retaining SWT Browser's session cookies. */
    public static String submit(String baseUrl, int mode, String server, String formName) {
        String target = mode == 0 ? "cache_clenaup_form" : "flush_cache_form";
        return "(function(){" + ES5_HELPERS
                + "if(" + preflight(baseUrl, mode, server) + "!=='READY')return false;"
                + "var f=getForm(" + jsString(target) + ");if(!f)return false;"
                + (mode == 0
                   ? "getElement(f,'server').value=" + jsString(server) + ";"
                     + "getElement(f,'form_name').value=" + jsString(formName) + ";"
                     + "getElement(f,'form_flush').value='Yes';"
                   : "getElement(f,'cache_flush').value='" + (mode == 1 ? "Yes" : "No") + "';"
                     + "getElement(f,'cache_flush_allpods').value='" + (mode == 2 ? "Yes" : "No") + "';")
                + "f.submit();return true;})()";
    }

    /** Read-only extraction of BMC's confirmation message after form submission. */
    public static String resultProbe() {
        return "(function(){" + ES5_HELPERS
                + "if(getForm('loginForm'))return 'LOGIN_REQUIRED';"
                + "if(bodyText().toLowerCase().indexOf('configuration tool authorization failed')!==-1)"
                + "return 'CONFIG_DENIED';"
                + "var nodes=document.querySelectorAll('.status,.error,.LoginError');"
                + "var parts=[];for(var i=0;i<nodes.length;i++){"
                + "var value=trimText(typeof nodes[i].textContent==='string'?nodes[i].textContent:nodes[i].innerText||'');"
                + "if(value)parts.push(value);}"
                + "return trimText(parts.join(' ').replace(/\\s+/g,' ')).substring(0,1024);})()";
    }
}
