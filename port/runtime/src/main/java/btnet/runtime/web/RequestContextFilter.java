package btnet.runtime.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Binds {@link HttpContext#Current()} for every request (RULEBOOK §2A
 * {@code HttpContext.Current}, §3 {@code BtnetApplication} "filters"). Register it with the
 * highest precedence (before Spring Security), for the REQUEST dispatcher type.
 *
 * <p>For a non-multipart request with a body it first reads the whole body (up to the
 * 50 MB {@code maxRequestLength}) so that {@code Request.Form} can be parsed exactly as
 * ASP.NET does and {@code Request.InputStream} replays it; downstream code sees a wrapper
 * whose {@code getInputStream()} replays the body and whose {@code getParameter*} merges the
 * query string and the url-encoded form. It also runs {@link HttpApplication#ensureStarted()}
 * before the first request.
 */
public class RequestContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        HttpApplication.ensureStarted();
        HttpServletRequest r = request;
        byte[] body = null;
        boolean tooLarge = false;
        String ct = request.getContentType() == null ? "" : request.getContentType().toLowerCase(Locale.ROOT);
        boolean hasBody = request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null;
        if (hasBody && !ct.startsWith("multipart/")) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            try (InputStream in = request.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (o.size() + n > HttpRequest.MAX_REQUEST_BYTES) {
                        tooLarge = true;
                        break;
                    }
                    o.write(buf, 0, n);
                }
            }
            body = tooLarge ? new byte[0] : o.toByteArray();
            r = new BodyReplayRequest(request, body, ct.startsWith("application/x-www-form-urlencoded"));
        }
        HttpContext ctx = HttpContext.forRequest(r, response, body, tooLarge);
        HttpContext previous = HttpContext.bind(ctx);
        try {
            chain.doFilter(r, response);
        } finally {
            HttpContext.restore(previous);
        }
    }

    /** Replays a captured body; merges url-encoded form fields into the servlet parameters. */
    static final class BodyReplayRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private final boolean formEncoded;
        private Map<String, String[]> params;

        BodyReplayRequest(HttpServletRequest request, byte[] body, boolean formEncoded) {
            super(request);
            this.body = body;
            this.formEncoded = formEncoded;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String enc = getCharacterEncoding();
            java.nio.charset.Charset cs = enc == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(enc);
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), cs));
        }

        private Map<String, String[]> params() {
            if (params == null) {
                Map<String, List<String>> m = new LinkedHashMap<>();
                addPairs(m, getQueryString());
                if (formEncoded) addPairs(m, new String(body, StandardCharsets.UTF_8));
                Map<String, String[]> out = new LinkedHashMap<>();
                for (Map.Entry<String, List<String>> e : m.entrySet()) out.put(e.getKey(), e.getValue().toArray(new String[0]));
                params = Collections.unmodifiableMap(out);
            }
            return params;
        }

        private static void addPairs(Map<String, List<String>> m, String s) {
            if (s == null || s.isEmpty()) return;
            for (String pair : s.split("&")) {
                if (pair.isEmpty()) continue;
                int eq = pair.indexOf('=');
                String k = HttpUtility.UrlDecode(eq < 0 ? pair : pair.substring(0, eq));
                String v = eq < 0 ? "" : HttpUtility.UrlDecode(pair.substring(eq + 1));
                m.computeIfAbsent(k, x -> new ArrayList<>()).add(v);
            }
        }

        @Override
        public String getParameter(String name) {
            String[] v = params().get(name);
            return v == null || v.length == 0 ? null : v[0];
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return params();
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(params().keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] v = params().get(name);
            return v == null ? null : v.clone();
        }
    }
}
