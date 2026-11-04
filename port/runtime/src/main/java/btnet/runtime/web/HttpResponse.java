package btnet.runtime.web;

import btnet.runtime.ArgumentException;
import btnet.runtime.Encoding;
import btnet.runtime.FileNotFoundException;
import btnet.runtime.Strings;
import btnet.runtime.io.MemoryStream;
import btnet.runtime.io.Stream;

import jakarta.servlet.http.HttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * .NET {@code System.Web.HttpResponse} with ASP.NET's {@code Buffer=true}
 * (RULEBOOK §2B B2/B3, §3 [S1-21]): everything — text, bytes, headers, cookies, status —
 * is buffered and sent once at the end of the request by {@link Page} (or the runtime
 * filter), so headers set after writes still apply.
 *
 * <ul>
 * <li>{@link #Write(Object)} applies {@code Strings.cs} (null/DBNull → {@code ""}, .NET
 * {@code ToString} formatting); text is encoded with {@link #ContentEncoding()} (UTF-8).</li>
 * <li>{@link #End()} throws {@link ResponseEndException}; {@link #Redirect(String)} sets
 * 302 + {@code Location} (resolved to an absolute path) + the .NET "Object moved" body and
 * throws {@link RedirectException}; {@code Redirect(url, false)} does not end.</li>
 * <li>{@link #Clear()} discards the body only (headers, cookies, status kept).</li>
 * <li>{@code Content-Type} gets {@code ; charset=utf-8} when any text was written or the
 * encoding was set (as .NET), so pages answer {@code text/html; charset=utf-8}.</li>
 * <li>{@code new HttpResponse(Writer)} ({@code bug.cs:1159}, {@code send_email.aspx.cs:484})
 * writes text straight to that writer.</li>
 * </ul>
 */
public class HttpResponse {
    private static final DateTimeFormatter RFC1123 =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);

    private final Writer externalWriter;
    private final HttpRequest request; // for Redirect resolution and cookie sync; may be null
    private final ByteArrayOutputStream body = new ByteArrayOutputStream();

    private int status = 200;
    private String statusDescription;
    private String contentType = "text/html";
    private Encoding encoding = Encoding.UTF8;
    private boolean customEncoding;
    private boolean textWritten;
    private final List<String[]> headers = new ArrayList<>();
    private String cacheControl;
    private ZonedDateTime expiresAt;
    private HttpCookieCollection cookies;
    private String redirectLocation;
    private MemoryStream outputStream;
    private int drained;
    private char pendingHighSurrogate;
    private final HttpCachePolicy cachePolicy = new HttpCachePolicy();
    private final Writer textWriter = new BufferWriter();

    /** .NET {@code new HttpResponse(TextWriter)}: text goes to {@code writer}. */
    public HttpResponse(Writer writer) {
        this.externalWriter = writer;
        this.request = null;
    }

    /** A buffered response for a request (created by {@link HttpContext}). */
    HttpResponse(HttpRequest request) {
        this.externalWriter = null;
        this.request = request;
    }

    // ------------------------------------------------------------------ writing

    public void Write(String s) {
        if (s == null || s.isEmpty()) return;
        writeText(s);
    }

    /** .NET {@code Write(object)}: {@code Strings.cs(o)} (null/DBNull → nothing). */
    public void Write(Object o) {
        if (o instanceof String s) {
            Write(s);
        } else if (o instanceof IHtmlString h) {
            Write(h.ToHtmlString());
        } else {
            Write(Strings.cs(o));
        }
    }

    public void Write(char ch) {
        writeText(String.valueOf(ch));
    }

    public void Write(char[] buffer, int index, int count) {
        writeText(new String(buffer, index, count));
    }

    private void writeText(String s) {
        textWritten = true;
        if (externalWriter != null) {
            try {
                externalWriter.write(s);
            } catch (IOException e) {
                throw new HttpException(e.getMessage(), e);
            }
            return;
        }
        drain();
        if (pendingHighSurrogate != 0) {
            s = pendingHighSurrogate + s;
            pendingHighSurrogate = 0;
        }
        if (!s.isEmpty() && Character.isHighSurrogate(s.charAt(s.length() - 1))) {
            pendingHighSurrogate = s.charAt(s.length() - 1);
            s = s.substring(0, s.length() - 1);
        }
        if (!s.isEmpty()) {
            byte[] b = encoding.GetBytes(s);
            body.write(b, 0, b.length);
        }
    }

    private void flushPendingSurrogate() {
        if (pendingHighSurrogate != 0) {
            String s = String.valueOf(pendingHighSurrogate);
            pendingHighSurrogate = 0;
            byte[] b = encoding.GetBytes(s);
            body.write(b, 0, b.length);
        }
    }

    /** .NET {@code BinaryWrite}: appends the bytes unchanged. */
    public void BinaryWrite(byte[] buffer) {
        if (buffer == null) throw new btnet.runtime.ArgumentNullException("buffer");
        if (externalWriter != null) return;
        drain();
        flushPendingSurrogate();
        body.write(buffer, 0, buffer.length);
    }

    /**
     * .NET {@code WriteFile(filename)}: appends the file's bytes; a non-rooted name is
     * mapped with {@code Request.MapPath}; {@code \} → {@code /}.
     */
    public void WriteFile(String filename) {
        String p = normalize(filename);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(Paths.get(p));
        } catch (java.nio.file.NoSuchFileException e) {
            throw new FileNotFoundException("Could not find file '" + filename + "'.");
        } catch (IOException e) {
            throw new HttpException(e.getMessage(), e);
        }
        BinaryWrite(bytes);
    }

    /** .NET {@code TransmitFile}: same as {@link #WriteFile(String)} in the buffered model. */
    public void TransmitFile(String filename) {
        WriteFile(filename);
    }

    private String normalize(String filename) {
        if (filename == null) throw new btnet.runtime.ArgumentNullException("filename");
        String p = filename.replace('\\', '/');
        if (!Paths.get(p).isAbsolute()) {
            String dir = request == null ? "/" : HttpRequest.directoryOf(safePath());
            p = HttpServerUtility.mapPath(p, dir);
        }
        return p;
    }

    private String safePath() {
        try {
            return request.Path();
        } catch (RuntimeException e) {
            return "/";
        }
    }

    /** .NET {@code OutputStream}: a stream whose bytes are appended to the body in write order. */
    public Stream OutputStream() {
        if (outputStream == null) {
            flushPendingSurrogate();
            outputStream = new MemoryStream(new byte[0]);
            drained = 0;
        }
        return outputStream;
    }

    private void drain() {
        if (outputStream != null) {
            byte[] all = outputStream.ToArray();
            if (all.length > drained) {
                body.write(all, drained, all.length - drained);
                drained = all.length;
            }
        }
    }

    /** .NET {@code Output} (TextWriter): writes go through {@link #Write(String)}. Also the Thymeleaf writer (B2 step 7). */
    public Writer Output() {
        return textWriter;
    }

    private final class BufferWriter extends Writer {
        @Override
        public void write(char[] cbuf, int off, int len) {
            if (len > 0) writeText(new String(cbuf, off, len));
        }

        @Override
        public void write(String str) {
            if (str != null && !str.isEmpty()) writeText(str);
        }

        @Override
        public void write(String str, int off, int len) {
            if (len > 0) writeText(str.substring(off, off + len));
        }

        @Override
        public void write(int c) {
            writeText(String.valueOf((char) c));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    /** No-op: the whole response is buffered and sent at the end. */
    public void Flush() {
    }

    public boolean IsClientConnected() {
        return true;
    }

    public boolean Buffer() {
        return true;
    }

    public boolean BufferOutput() {
        return true;
    }

    /** .NET {@code Clear()} / {@code ClearContent()}: body only. */
    public void Clear() {
        body.reset();
        pendingHighSurrogate = 0;
        if (outputStream != null) {
            outputStream = null;
            drained = 0;
        }
    }

    public void ClearContent() {
        Clear();
    }

    public void ClearHeaders() {
        headers.clear();
        contentType = "text/html";
        cacheControl = null;
        expiresAt = null;
        redirectLocation = null;
        status = 200;
        statusDescription = null;
    }

    // ------------------------------------------------------------------ control flow

    /** .NET {@code End()}: throws {@link ResponseEndException}. */
    public void End() {
        throw new ResponseEndException();
    }

    public void Redirect(String url) {
        Redirect(url, true);
    }

    /**
     * .NET {@code Redirect(url, endResponse)}: {@code \n} in the url →
     * {@code ArgumentException("Redirect URI cannot contain newline characters.")}; the url
     * is resolved ({@code ~/x} → {@code /x}, relative → against the request directory),
     * path-encoded (spaces, non-ASCII), the body is replaced by .NET's "Object moved"
     * page, status 302; ends unless {@code endResponse} is false.
     */
    public void Redirect(String url, boolean endResponse) {
        if (url == null) throw new btnet.runtime.ArgumentNullException("url");
        if (url.indexOf('\n') >= 0) throw new ArgumentException("Redirect URI cannot contain newline characters.");
        String location = encodeRedirect(resolve(url));
        Clear();
        status = 302;
        statusDescription = null;
        redirectLocation = location;
        contentType = "text/html";
        Write("<html><head><title>Object moved</title></head><body>\r\n<h2>Object moved to <a href=\""
            + HttpUtility.HtmlAttributeEncode(location) + "\">here</a>.</h2>\r\n</body></html>\r\n");
        if (endResponse) throw new RedirectException(location);
    }

    private String resolve(String url) {
        if (url.startsWith("~/")) return "/" + url.substring(2);
        if (url.equals("~")) return "/";
        if (url.startsWith("/")) return url;
        int colon = url.indexOf(':');
        int slash = url.indexOf('/');
        int q = url.indexOf('?');
        if (colon > 0 && (slash < 0 || colon < slash) && (q < 0 || colon < q)) return url; // absolute URI
        String dir = request == null ? "/" : HttpRequest.directoryOf(safePath());
        return collapse(dir + url);
    }

    private static String collapse(String path) {
        int q = path.indexOf('?');
        String p = q < 0 ? path : path.substring(0, q);
        String rest = q < 0 ? "" : path.substring(q);
        List<String> parts = new ArrayList<>();
        boolean trailing = p.endsWith("/");
        for (String seg : p.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) continue;
            if (seg.equals("..")) {
                if (!parts.isEmpty()) parts.remove(parts.size() - 1);
            } else {
                parts.add(seg);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String seg : parts) sb.append('/').append(seg);
        if (sb.length() == 0 || trailing) sb.append('/');
        return sb + rest;
    }

    /** .NET {@code HttpResponse.UrlEncodeRedirect}. */
    static String encodeRedirect(String url) {
        int q = url.indexOf('?');
        if (q >= 0) {
            return HttpUtility.UrlEncodeNonAscii(url.substring(0, q)).replace(" ", "%20")
                + HttpUtility.UrlEncodeNonAscii(url.substring(q));
        }
        return HttpUtility.UrlEncodeNonAscii(url).replace(" ", "%20");
    }

    public String RedirectLocation() {
        return redirectLocation;
    }

    public void RedirectLocation(String v) {
        redirectLocation = v;
    }

    // ------------------------------------------------------------------ headers

    public String ContentType() {
        return contentType;
    }

    public void ContentType(String v) {
        contentType = v;
    }

    public Encoding ContentEncoding() {
        return encoding;
    }

    /** Encoding of later text writes and the {@code charset} of the content type. */
    public void ContentEncoding(Encoding e) {
        if (e == null) throw new btnet.runtime.ArgumentNullException("value");
        flushPendingSurrogate();
        encoding = e;
        customEncoding = true;
    }

    /** .NET {@code Charset}. */
    public String Charset() {
        return charsetName(encoding);
    }

    public void Charset(String v) {
        if (v != null && (v.equalsIgnoreCase("windows-1252") || v.equalsIgnoreCase("iso-8859-1"))) {
            ContentEncoding(Encoding.Default);
        } else {
            ContentEncoding(Encoding.UTF8);
        }
    }

    private static String charsetName(Encoding e) {
        if (e == Encoding.Default) return "windows-1252";
        return "utf-8";
    }

    public int StatusCode() {
        return status;
    }

    public void StatusCode(int v) {
        status = v;
    }

    public String StatusDescription() {
        return statusDescription;
    }

    public void StatusDescription(String v) {
        statusDescription = v;
    }

    /** .NET {@code AddHeader}/{@code AppendHeader}: appends a header. */
    public void AddHeader(String name, String value) {
        AppendHeader(name, value);
    }

    public void AppendHeader(String name, String value) {
        if (name == null) throw new btnet.runtime.ArgumentNullException("name");
        if (name.equalsIgnoreCase("Content-Type")) {
            contentType = value;
            return;
        }
        if (name.equalsIgnoreCase("Cache-Control")) {
            cacheControl = value;
            return;
        }
        headers.add(new String[] {name, value == null ? "" : value});
    }

    public String CacheControl() {
        return cacheControl == null ? "private" : cacheControl;
    }

    /** .NET {@code CacheControl = v}: {@code Cache-Control: v}. */
    public void CacheControl(String v) {
        cacheControl = v;
    }

    /** .NET {@code Expires = minutes}: {@code Expires} = now + minutes (RFC 1123, GMT; -1 → one minute ago). */
    public void Expires(int minutes) {
        expiresAt = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(minutes);
    }

    public void ExpiresAbsolute(LocalDateTime v) {
        expiresAt = v == null ? null : v.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC);
    }

    /** .NET {@code Response.Cache} ({@code view_attachment.aspx:127} {@code SetExpires}). */
    public HttpCachePolicy Cache() {
        return cachePolicy;
    }

    /** .NET {@code System.Web.HttpCachePolicy} (the members the source uses). */
    public final class HttpCachePolicy {
        private HttpCachePolicy() {
        }

        public void SetExpires(LocalDateTime date) {
            ExpiresAbsolute(date);
        }

        public void SetNoStore() {
            cacheControl = "no-store";
        }

        public void SetMaxAge(java.time.Duration d) {
            cacheControl = (cacheControl == null ? "private" : cacheControl) + ", max-age=" + d.getSeconds();
        }
    }

    public HttpCookieCollection Cookies() {
        if (cookies == null) cookies = new HttpCookieCollection(true, request);
        return cookies;
    }

    /** .NET {@code AppendCookie}/{@code SetCookie}. */
    public void AppendCookie(HttpCookie c) {
        Cookies().Add(c);
    }

    public void SetCookie(HttpCookie c) {
        Cookies().Set(c);
    }

    // ------------------------------------------------------------------ sending

    /** The buffered body bytes (runtime / tests). */
    public byte[] bodyBytes() {
        drain();
        flushPendingSurrogate();
        return body.toByteArray();
    }

    /** The {@code Content-Type} header value that will be sent. */
    public String effectiveContentType() {
        if (contentType == null || contentType.isEmpty()) return null;
        if ((textWritten || customEncoding) && !contentType.toLowerCase(Locale.ROOT).contains("charset=")) {
            return contentType + "; charset=" + charsetName(encoding);
        }
        return contentType;
    }

    /** Sends status, headers, cookies and the buffered body to {@code res} (once, at the end of the request). */
    public void flushTo(HttpServletResponse res, boolean headRequest) {
        byte[] bytes = bodyBytes();
        if (res.isCommitted()) return;
        res.setStatus(status);
        String ct = effectiveContentType();
        if (ct != null) res.setContentType(ct);
        res.setHeader("Cache-Control", CacheControl());
        if (expiresAt != null) res.setHeader("Expires", RFC1123.format(expiresAt));
        if (redirectLocation != null) res.setHeader("Location", redirectLocation);
        for (String[] h : headers) res.addHeader(h[0], h[1]);
        if (cookies != null) {
            for (HttpCookie c : cookies) res.addHeader("Set-Cookie", c.toSetCookieHeader());
        }
        res.setContentLength(headRequest ? 0 : bytes.length);
        if (!headRequest && bytes.length > 0) {
            try {
                OutputStream o = res.getOutputStream();
                o.write(bytes);
                o.flush();
            } catch (IOException e) {
                // client went away: nothing to do (IsClientConnected)
            }
        }
    }
}
