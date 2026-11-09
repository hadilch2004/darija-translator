package ma.aui.darija.api;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Minimal CORS support so browser-based clients (web pages, the extension's dev tools, Postman web)
 * can call the API. Pre-flight requests are answered before authentication, as the spec requires.
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private final String allowedOrigin;

    public CorsFilter(String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        boolean preflight = "OPTIONS".equals(request.getMethod())
                && request.getHeaderString("Access-Control-Request-Method") != null;
        if (preflight) {
            Response.ResponseBuilder response = Response.noContent();
            addHeaders(request.getHeaderString("Origin"), response);
            response.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Authorization, Content-Type");
            response.header("Access-Control-Max-Age", "86400");
            request.abortWith(response.build());
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (request.getHeaderString("Origin") != null) {
            MultivaluedMap<String, Object> headers = response.getHeaders();
            String origin = resolveOrigin(request.getHeaderString("Origin"));
            if (origin != null) {
                headers.putSingle("Access-Control-Allow-Origin", origin);
                if (!"*".equals(origin)) {
                    headers.add("Vary", "Origin");
                }
            }
        }
    }

    private void addHeaders(String requestOrigin, Response.ResponseBuilder response) {
        String origin = resolveOrigin(requestOrigin);
        if (origin != null) {
            response.header("Access-Control-Allow-Origin", origin);
            if (!"*".equals(origin)) {
                response.header("Vary", "Origin");
            }
        }
    }

    /** "*" when everything is allowed, the echoed origin when it matches the single allowed one. */
    private String resolveOrigin(String requestOrigin) {
        if ("*".equals(allowedOrigin)) {
            return "*";
        }
        return allowedOrigin.equals(requestOrigin) ? requestOrigin : null;
    }
}
