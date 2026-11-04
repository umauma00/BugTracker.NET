package btnet.runtime.net;

/**
 * Stands for .NET {@code System.Net.ICertificatePolicy} [S1-4]. Installed
 * process-wide with {@link ServicePointManager#CertificatePolicy(ICertificatePolicy)};
 * consulted at every TLS handshake of a client built by {@link HttpClients#newBuilder()}.
 */
public interface ICertificatePolicy {
    /**
     * .NET {@code CheckValidationResult}.
     *
     * @param certificateProblem 0 when the JDK's PKIX validation and HTTPS host-name
     *                           check both passed, otherwise non-zero
     * @return true to accept the server certificate
     */
    boolean CheckValidationResult(ServicePoint srvPoint, java.security.cert.X509Certificate certificate,
                                  WebRequest request, int certificateProblem);
}
