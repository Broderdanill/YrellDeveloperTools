package se.yrell.developertools.midtier;

/**
 * Only evaluated on the configured MidTier origin and exact cache JSP.
 * The browser keeps its own authentication cookies (including HttpOnly).
 * Java never reads cookies, cfgStok, passwords, or the contents of an IdP page.
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
                    if (c < 32 || c == 0x2028 || c == 0x2029) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    /** Read-only classification, only called on the configured MidTier origin.
     * RSSO may authenticate successfully while MidTier Configuration Tool
     * independently refuses access. That response is NOT a cache page.
     */
    public static String pageState() {
        return "(function(){"
                + "if(document.forms.namedItem('loginForm'))return 'CONFIG_LOGIN';"
                + "var content=(document.body&&document.body.textContent||'').replace(/\\s+/g,' ').toLowerCase();"
                + "if(content.includes('configuration tool authorization failed'))return 'CONFIG_DENIED';"
                + "if(document.forms.namedItem('cache_clenaup_form')||document.forms.namedItem('flush_cache_form'))return 'CACHE_FORM';"
                + "return 'OTHER';})()";
    }

    /** Verify BMC's real configuration login form; NEVER submit on an IdP page. */
    public static String configLoginCheck(String baseUrl) {
        return "(function(){"
                + "var base=new URL(" + jsString(MidTierConfiguration.normalizeBaseUrl(baseUrl)) + ");"
                + "var f=document.forms.namedItem('loginForm');if(!f)return 'NO_LOGIN_FORM';"
                + "var target=new URL(f.getAttribute('action')||'',location.href);"
                + "var wanted=new URL(base.pathname+'/servlet/ConfigServlet',base.origin);"
                + "if(target.origin!==wanted.origin||target.pathname!==wanted.pathname"
                + "||target.search!=='?action=logon'||(f.method||'').toLowerCase()!=='post')return 'UNEXPECTED_LOGIN_ACTION';"
                + "if(!f.querySelector('input[name=\"username\"]')||!f.querySelector('input[name=\"pwd\"]')"
                + "||!f.querySelector('input[name=\"servername\"]'))return 'MISSING_LOGIN_FIELDS';"
                + "var cfg=f.querySelector('input[name=\"isFromCfgTool\"]');"
                + "if(!cfg||cfg.value!=='true')return 'NOT_CONFIG_TOOL_LOGIN';"
                + "return 'READY';})()";
    }

    /** Submit only BMC's verified login form on the exact configured origin.
     * Username/password exist only in this ephemeral executed script and browser
     * memory, are never stored in plugin preferences or logged.
     */
    public static String submitConfigLogin(String baseUrl, String server, String user, String password) {
        return "(function(){if(" + configLoginCheck(baseUrl) + "!=='READY')return false;"
                + "var f=document.forms.namedItem('loginForm');"
                + "f.querySelector('input[name=\"username\"]').value=" + jsString(user) + ";"
                + "f.querySelector('input[name=\"pwd\"]').value=" + jsString(password) + ";"
                + "Array.from(f.querySelectorAll('input[name=\"servername\"]')).filter(function(x){return !x.disabled;})"
                + ".forEach(function(x){x.value=" + jsString(server) + ";});"
                + "var tz=f.querySelector('input[name=\"timezone\"]');"
                + "if(tz)tz.value=Intl.DateTimeFormat().resolvedOptions().timeZone||'Europe/Stockholm';"
                + "f.submit();return true;})()";
    }

    /** Read-only check on the exact cache JSP; never launches an operation. */
    public static String preflight(String baseUrl, int mode, String server) {
        String expectedPath = mode == 0 ? "/servlet/GoatConfigServlet" : "/servlet/GoatConfigServlet";
        String formName = mode == 0 ? "cache_clenaup_form" : "flush_cache_form";
        return "(function(){"
                + "var base=new URL(" + jsString(MidTierConfiguration.normalizeBaseUrl(baseUrl)) + ");"
                + "var form=document.forms.namedItem(" + jsString(formName) + ");"
                + "if(document.forms.namedItem('loginForm'))return 'LOGIN_REQUIRED';"
                + "if(!form)return 'NOT_CACHE_PAGE';"
                + "var token=form.elements.namedItem('cfgStok');"
                + "if(!token||!token.value)return 'ERROR: Missing cfgStok';"
                + "var action=new URL(form.getAttribute('action'),location.href);"
                + "var wanted=new URL(base.pathname+" + jsString(expectedPath) + ",base.origin);"
                + "if(action.origin!==wanted.origin||action.pathname!==wanted.pathname)"
                + "return 'ERROR: Unexpected form action';"
                + "if(action.searchParams.get('action')!=='Cache')return 'ERROR: Invalid cache action';"
                + (mode == 0 ? "if(action.searchParams.get('op_type')!=='form_cleanup')return 'ERROR: Invalid cleanup action';"
                              : "if(action.searchParams.has('op_type'))return 'ERROR: Unexpected cleanup mode';")
                + "if(action.searchParams.size!==" + (mode == 0 ? 2 : 1) + ")return 'ERROR: Unexpected action parameters';"
                + (mode == 0 ? "var sel=form.elements.namedItem('server');"
                     + "if(!sel||!Array.from(sel.options).some(function(o){return o.value===" + jsString(server) + ";}))"
                     + "return 'ERROR: AR server not in MidTier list';"
                     + "if(!form.elements.namedItem('form_name')||!form.elements.namedItem('form_flush'))return 'ERROR: Form inputs missing';" :
                     "if(!form.elements.namedItem('cache_flush')||!form.elements.namedItem('cache_flush_allpods'))return 'ERROR: Cache inputs missing';")
                + "return 'READY';})()";
    }

    /** Submits BMC's original form, retaining the browser's authenticated session/token. */
    public static String submit(String baseUrl, int mode, String server, String formName) {
        String target = mode == 0 ? "cache_clenaup_form" : "flush_cache_form";
        return "(function(){if(" + preflight(baseUrl, mode, server) + "!=='READY')return false;"
                + "var f=document.forms.namedItem(" + jsString(target) + ");"
                + "if(!f)return false;"
                + (mode == 0
                    ? "f.elements.namedItem('server').value=" + jsString(server) + ";"
                      + "f.elements.namedItem('form_name').value=" + jsString(formName) + ";"
                      + "f.elements.namedItem('form_flush').value='Yes';"
                    : "f.elements.namedItem('cache_flush').value='" + (mode == 1 ? "Yes" : "No") + "';"
                      + "f.elements.namedItem('cache_flush_allpods').value='" + (mode == 2 ? "Yes" : "No") + "';")
                + "f.submit();return true;})()";
    }

    /** Probe result only in configured MidTier origin after the submission. */
    public static String resultProbe() {
        return "(function(){if(document.forms.namedItem('loginForm'))return 'LOGIN_REQUIRED';"
                + "var content=(document.body&&document.body.textContent||'').toLowerCase();"
                + "if(content.includes('configuration tool authorization failed'))return 'CONFIG_DENIED';"
                + "var nodes=document.querySelectorAll('.status,.error,.LoginError');"
                + "var text=Array.from(nodes).map(function(n){return (n.textContent||'').trim();})"
                + ".filter(Boolean).join(' ').replace(/\\s+/g,' ').trim();"
                + "return text.substring(0,1024);})()";
    }
}
