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
                + "var nodes=document.querySelectorAll('.status,.error,.LoginError');"
                + "var text=Array.from(nodes).map(function(n){return (n.textContent||'').trim();})"
                + ".filter(Boolean).join(' ').replace(/\\s+/g,' ').trim();"
                + "return text.substring(0,1024);})()";
    }
}
