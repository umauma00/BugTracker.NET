package btnet.runtime.net;

/**
 * Stands for .NET {@code System.Net.ServicePointManager} [S1-4, D43]:
 * the static, process-wide, sticky {@code CertificatePolicy}.
 * {@code ServicePointManager.CertificatePolicy = p} →
 * {@code ServicePointManager.CertificatePolicy(p)}. {@code null} = none.
 */
public final class ServicePointManager {
    private static volatile ICertificatePolicy certificatePolicy;

    private ServicePointManager() {
    }

    /** .NET setter {@code ServicePointManager.CertificatePolicy = p}. */
    public static void CertificatePolicy(ICertificatePolicy p) {
        certificatePolicy = p;
    }

    /** .NET getter {@code ServicePointManager.CertificatePolicy}. */
    public static ICertificatePolicy CertificatePolicy() {
        return certificatePolicy;
    }
}
