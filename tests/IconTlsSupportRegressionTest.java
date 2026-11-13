package se.yrell.developertools.icons;

import java.net.URL;
import java.net.URLConnection;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;

/** Checks that the Windows trust fallback still requires real CA validation. */
public final class IconTlsSupportRegressionTest {
    private static int assertions;
    private static void yes(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        assertions++;
    }

    private static final class TestTrust extends X509ExtendedTrustManager {
        boolean allow;
        int checks;
        TestTrust(boolean allow) { this.allow = allow; }
        private void check() throws CertificateException {
            checks++;
            if (!allow) throw new CertificateException("test rejection");
        }
        @Override public void checkServerTrusted(X509Certificate[] certs, String auth) throws CertificateException { check(); }
        @Override public void checkServerTrusted(X509Certificate[] certs, String auth, Socket s) throws CertificateException { check(); }
        @Override public void checkServerTrusted(X509Certificate[] certs, String auth, SSLEngine s) throws CertificateException { check(); }
        @Override public void checkClientTrusted(X509Certificate[] certs, String auth) throws CertificateException { check(); }
        @Override public void checkClientTrusted(X509Certificate[] certs, String auth, Socket s) throws CertificateException { check(); }
        @Override public void checkClientTrusted(X509Certificate[] certs, String auth, SSLEngine s) throws CertificateException { check(); }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }

    public static void main(String[] args) throws Exception {
        X509Certificate[] fakeChain = new X509Certificate[0];
        TestTrust javaTrust = new TestTrust(true);
        TestTrust windowsTrust = new TestTrust(false);
        IconTlsSupport.EitherTrustedManager both = new IconTlsSupport.EitherTrustedManager(javaTrust, windowsTrust);
        both.checkServerTrusted(fakeChain, "RSA");
        yes(javaTrust.checks == 1 && windowsTrust.checks == 0, "JVM trust takes priority");
        javaTrust.allow = false;
        windowsTrust.allow = true;
        both.checkServerTrusted(fakeChain, "RSA", (Socket) null);
        yes(javaTrust.checks == 2 && windowsTrust.checks == 1, "Windows fallback on JVM rejection");
        both.checkServerTrusted(fakeChain, "RSA", (SSLEngine) null);
        yes(javaTrust.checks == 3 && windowsTrust.checks == 2, "SSLEngine trust delegates correctly");
        windowsTrust.allow = false;
        boolean rejected = false;
        try { both.checkServerTrusted(fakeChain, "RSA"); }
        catch (CertificateException expected) { rejected = true; yes(expected.getSuppressed().length == 1, "both rejection causes retained"); }
        yes(rejected, "Both truststores rejecting must reject the server");
        rejected = false;
        try { both.checkClientTrusted(fakeChain, "RSA"); }
        catch (CertificateException expected) { rejected = true; }
        yes(rejected, "Client trust cannot bypass JVM validation");
        URL url = new URL("https://example.invalid/styles.css");
        URLConnection connection = IconTlsSupport.open(url); // no network request
        yes(connection instanceof HttpsURLConnection, "HTTPS uses HttpsURLConnection");
        yes(((HttpsURLConnection) connection).getHostnameVerifier() != null, "Hostname verifier retained");
        yes(IconTlsSupport.describeHandshakeFailure(url, new java.io.IOException("test HTTP failure")).equals("test HTTP failure"),
                "Non-TLS error is not misreported as certificate error");
        yes(IconTlsSupport.trustDescription().length() > 0, "Trust source is described");
        System.out.println("IconTlsSupportRegressionTest: " + assertions + " PASS");
    }
}
