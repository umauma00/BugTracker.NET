package btnet.runtime.net;

import btnet.runtime.mail.SmtpClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.CookieManager;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Stands for constructing a .NET 4.5 {@code System.Net.Http.HttpClient} over
 * {@code HttpClientHandler} [S1-4, S1-5]. {@code java.net.http.HttpClient.newBuilder()}
 * is banned in translated code; every client is built from {@link #newBuilder()}.
 *
 * <p>Presets (the .NET 4.5 defaults): HTTP/1.1 only; redirects followed
 * ({@code AllowAutoRedirect = true}, {@code Redirect.ALWAYS}: .NET also follows
 * https→http; at most 50 hops, via {@code jdk.httpclient.redirects.retrylimit}
 * unless already set); a fresh {@code CookieManager} ({@code UseCookies = true});
 * the system proxy ({@code UseProxy = true}); and an {@code SSLContext} whose trust
 * manager decides at each handshake (§3):
 * JDK PKIX validation + HTTPS host-name identification give problem 0 or 1; if
 * {@link ServicePointManager#CertificatePolicy()} is set, accept iff its
 * {@code CheckValidationResult} returns true; else if
 * {@link SmtpClient#TrustAllCertificates()} is set (the process-wide .NET
 * {@code ServerCertificateValidationCallback}, {@code email.cs:242}), accept; else
 * accept iff problem = 0. Callers may override any preset on the builder.
 */
public final class HttpClients {
    static {
        if (System.getProperty("jdk.httpclient.redirects.retrylimit") == null) {
            System.setProperty("jdk.httpclient.redirects.retrylimit", "50");
        }
    }

    private static volatile SSLContext sslContext;

    private HttpClients() {
    }

    /** A builder preset as described in the class comment. */
    public static HttpClient.Builder newBuilder() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .cookieHandler(new CookieManager())
                .proxy(ProxySelector.getDefault())
                .sslContext(sslContext());
    }

    /** The shared {@code SSLContext} with the .NET-policy trust manager. */
    public static SSLContext sslContext() {
        SSLContext c = sslContext;
        if (c == null) {
            synchronized (HttpClients.class) {
                c = sslContext;
                if (c == null) {
                    try {
                        c = SSLContext.getInstance("TLS");
                        c.init(null, new TrustManager[]{new DotNetPolicyTrustManager(defaultTrustManager())}, null);
                    } catch (GeneralSecurityException e) {
                        throw btnet.runtime.Exceptions.wrap(e);
                    }
                    sslContext = c;
                }
            }
        }
        return c;
    }

    private static X509ExtendedTrustManager defaultTrustManager() throws GeneralSecurityException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509ExtendedTrustManager x) {
                return x;
            }
        }
        throw new GeneralSecurityException("no X509ExtendedTrustManager");
    }

    /** Applies the .NET ServicePointManager / validation-callback decision. */
    static final class DotNetPolicyTrustManager extends X509ExtendedTrustManager {
        private final X509ExtendedTrustManager delegate;

        DotNetPolicyTrustManager(X509ExtendedTrustManager delegate) {
            this.delegate = delegate;
        }

        private void decide(X509Certificate[] chain, CertificateException problem) throws CertificateException {
            int problemCode = problem == null ? 0 : 1;
            ICertificatePolicy policy = ServicePointManager.CertificatePolicy();
            boolean accept;
            if (policy != null) {
                X509Certificate leaf = chain == null || chain.length == 0 ? null : chain[0];
                accept = policy.CheckValidationResult(new ServicePoint(), leaf, new WebRequest(), problemCode);
            } else if (SmtpClient.TrustAllCertificates()) {
                accept = true;
            } else {
                accept = problemCode == 0;
            }
            if (!accept) {
                throw problem != null ? problem : new CertificateException("The remote certificate is invalid according to the validation procedure.");
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            CertificateException problem = null;
            try {
                delegate.checkServerTrusted(chain, authType, engine);
            } catch (CertificateException e) {
                problem = e;
            }
            decide(chain, problem);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            CertificateException problem = null;
            try {
                delegate.checkServerTrusted(chain, authType, socket);
            } catch (CertificateException e) {
                problem = e;
            }
            decide(chain, problem);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            CertificateException problem = null;
            try {
                delegate.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                problem = e;
            }
            decide(chain, problem);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            delegate.checkClientTrusted(chain, authType, engine);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            delegate.checkClientTrusted(chain, authType, socket);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return ((X509TrustManager) delegate).getAcceptedIssuers();
        }
    }
}
