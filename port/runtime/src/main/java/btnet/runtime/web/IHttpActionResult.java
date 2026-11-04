package btnet.runtime.web;

import btnet.runtime.config.JsonConfig;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * Web API {@code System.Web.Http.IHttpActionResult}, the return type of the translated
 * actions. It is a Spring {@link ResponseEntity} (so a {@code @RestController} method may
 * return it directly) whose body is already-serialized bytes: JSON from
 * {@code JsonConfig.mapper()} with {@code Content-Type: application/json; charset=utf-8}
 * (regardless of {@code Accept}), or empty.
 */
public class IHttpActionResult extends ResponseEntity<Object> {
    private IHttpActionResult(Object body, HttpHeaders headers, int status) {
        super(body, headers, org.springframework.http.HttpStatusCode.valueOf(status));
    }

    /** An empty body with {@code status}. */
    public static IHttpActionResult empty(int status) {
        HttpHeaders h = new HttpHeaders();
        h.setContentLength(0);
        return new IHttpActionResult(new byte[0], h, status);
    }

    /** {@code value} serialized as JSON with {@code status}. */
    public static IHttpActionResult json(int status, Object value) {
        byte[] bytes = JsonConfig.mapper().writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8");
        h.setContentLength(bytes.length);
        return new IHttpActionResult(bytes, h, status);
    }
}
