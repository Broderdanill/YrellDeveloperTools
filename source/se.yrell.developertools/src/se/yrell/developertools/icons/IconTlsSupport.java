package se.yrell.developertools.icons;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.Socket;

import se.yrell.developertools.Log;

/**
 * TLS for PWA icon resources. Eclipse SWT/Edge uses the Windows certificate
 * store while plain URLConnection normally uses Java's configured truststore.
 * Trust a chain if EITHER the JVM truststore OR Windows-ROOT validates it.
 *
 * No trust-all manager, hostname override, global SSL defaults, or changes
 * to other Developer Studio HTTP clients are involved.
 */
final class IconTlsSupport {
    private static volatile SSLSocketFactory socketFactory;
    private static volatile boolean initialized;
    private static volatile String trustDescription = "JVM default truststore";

    private IconTlsSupport() { }

    static URLConnection open(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        if (connection instanceof HttpsURLConnection https) {
            SSLSocketFactory factory = getSocketFactory();
            if (factory != null) {
                // Per-connection only; HttpsURLConnection's built-in hostname
                // verification remains enabled and unchanged.
                https.setSSLSocketFactory(factory);
            }
        }
        return connection;
    }

    private static SSLSocketFactory getSocketFactory() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return null; // Keep JVM default TLS behavior on Linux/macOS.
        }
        if (initialized) return socketFactory;
        synchronized (IconTlsSupport.class) {
            if (initialized) return socketFactory;
            try {
                // Respect javax.net.ssl.trustStore and JVM cacerts as usual.
                X509TrustManager jvmTrust = defaultTrustManager(null);
                KeyStore windowsRoots = KeyStore.getInstance("Windows-ROOT");
                windowsRoots.load(null, null);
                X509TrustManager windowsTrust = defaultTrustManager(windowsRoots);
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[] { new EitherTrustedManager(jvmTrust, windowsTrust) }, null);
                socketFactory = context.getSocketFactory();
                trustDescription = "JVM truststore + Windows-ROOT";
                Log.info("PWA Icon Helper HTTPS: enabled combined JVM and Windows-ROOT certificate validation.");
            } catch (Exception e) {
                // Never weaken TLS on error. Retain Java's default validation.
                Log.warn("PWA Icon Helper could not read Windows-ROOT; falling back to JVM truststore: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
                trustDescription = "JVM default truststore (Windows-ROOT unavailable)";
            } finally {
                initialized = true;
            }
            return socketFactory;
        }
    }

    private static X509TrustManager defaultTrustManager(KeyStore store) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        for (TrustManager candidate : factory.getTrustManagers()) {
            if (candidate instanceof X509TrustManager trustManager) return trustManager;
        }
        throw new GeneralSecurityException("No X509TrustManager provided by this Java runtime");
    }

    static String trustDescription() {
        getSocketFactory();
        return trustDescription;
    }

    static String describeHandshakeFailure(URL requested, IOException failure) {
        Throwable cause = failure;
        boolean certificateFailure = false;
        for (int i = 0; cause != null && i < 16; i++, cause = cause.getCause()) {
            if (cause instanceof CertificateException
                    || cause.getClass().getSimpleName().contains("CertPath")
                    || cause.getClass().getSimpleName().contains("SSLHandshake")) {
                certificateFailure = true;
                break;
            }
        }
        if (!certificateFailure) return failure.getMessage();
        String store = System.getProperty("javax.net.ssl.trustStore", "<Java default>");
        String runtime = System.getProperty("java.home", "<unknown>");
        return "TLS certificate validation failed when requesting " + requested.getHost()
                + " (or its HTTPS redirect). " + failure.getMessage()
                + ". Trust: " + trustDescription() + "; java.home=" + runtime
                + "; javax.net.ssl.trustStore=" + store
                + ". Check the full server/redirect certificate chain and which Java runtime Developer Studio uses.";
    }

    /** Two *validating* trust managers, never an accept-all fallback. */
    static final class EitherTrustedManager extends X509ExtendedTrustManager {
        private final X509TrustManager jvm;
        private final X509TrustManager windows;

        EitherTrustedManager(X509TrustManager jvm, X509TrustManager windows) {
            if (jvm == null || windows == null) throw new IllegalArgumentException("Trust managers are required");
            this.jvm = jvm;
            this.windows = windows;
        }

        @FunctionalInterface
        private interface Check { void run(X509TrustManager manager) throws CertificateException; }

        private void either(Check check) throws CertificateException {
            try {
                check.run(jvm);
            } catch (CertificateException jvmFailure) {
                try {
                    check.run(windows);
                } catch (CertificateException windowsFailure) {
                    jvmFailure.addSuppressed(windowsFailure);
                    throw jvmFailure;
                }
            }
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            either(trust -> trust.checkServerTrusted(chain, authType));
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            either(trust -> {
                if (trust instanceof X509ExtendedTrustManager extended)
                    extended.checkServerTrusted(chain, authType, socket);
                else trust.checkServerTrusted(chain, authType);
            });
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            either(trust -> {
                if (trust instanceof X509ExtendedTrustManager extended)
                    extended.checkServerTrusted(chain, authType, engine);
                else trust.checkServerTrusted(chain, authType);
            });
        }

        // Client certificates are unrelated to downloading a PWA CSS asset.
        // Keep the JVM's standard trust policy for all client-cert checks.
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException { jvm.checkClientTrusted(chain, authType); }

        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            if (jvm instanceof X509ExtendedTrustManager extended)
                extended.checkClientTrusted(chain, authType, socket);
            else jvm.checkClientTrusted(chain, authType);
        }

        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            if (jvm instanceof X509ExtendedTrustManager extended)
                extended.checkClientTrusted(chain, authType, engine);
            else jvm.checkClientTrusted(chain, authType);
        }

        @Override public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] left = jvm.getAcceptedIssuers();
            X509Certificate[] right = windows.getAcceptedIssuers();
            X509Certificate[] combined = Arrays.copyOf(left, left.length + right.length);
            System.arraycopy(right, 0, combined, left.length, right.length);
            return combined;
        }
    }
}
