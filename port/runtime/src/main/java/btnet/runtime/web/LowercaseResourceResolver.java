package btnet.runtime.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.util.List;
import java.util.Locale;

/**
 * Static assets are copied to {@code static/} with lower-cased paths (RULEBOOK §2B B8); this
 * resolver lower-cases every lookup so {@code Scripts/x.js} and {@code scripts/x.js} resolve
 * alike, as IIS did. Registered by {@code BtnetApplication} on the static resource handler.
 */
public class LowercaseResourceResolver implements ResourceResolver {

    @Override
    public Resource resolveResource(HttpServletRequest request, String requestPath,
                                    List<? extends Resource> locations, ResourceResolverChain chain) {
        Resource r = chain.resolveResource(request, lower(requestPath), locations);
        if (r == null && !requestPath.equals(lower(requestPath))) {
            r = chain.resolveResource(request, requestPath, locations);
        }
        return r;
    }

    @Override
    public String resolveUrlPath(String resourcePath, List<? extends Resource> locations, ResourceResolverChain chain) {
        return chain.resolveUrlPath(lower(resourcePath), locations);
    }

    static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
