package ma.aui.darija.auth;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;
import java.util.Optional;
import ma.aui.darija.model.ErrorResponse;

/**
 * HTTP Basic authentication (RFC 7617) for every resource annotated with {@link Secured}.
 *
 * <p>On success the authenticated {@link AuthenticatedUser} becomes the request's
 * {@link SecurityContext}, so standard {@code @RolesAllowed} checks (enforced by Jersey's
 * {@code RolesAllowedDynamicFeature}) work. On failure the request is aborted with
 * {@code 401 Unauthorized} and a {@code WWW-Authenticate} challenge.
 */
@Secured
@Provider
@Priority(Priorities.AUTHENTICATION)
public class BasicAuthFilter implements ContainerRequestFilter {

    public static final String REALM = "Darija Translator";
    private static final String SCHEME = "Basic";

    private final UserStore users;

    @Inject
    public BasicAuthFilter(UserStore users) {
        this.users = users;
    }

    @Override
    public void filter(ContainerRequestContext context) {
        // CORS preflight requests carry no credentials by design.
        if ("OPTIONS".equals(context.getMethod())) {
            return;
        }

        Optional<AuthenticatedUser> user = credentials(context.getHeaderString(HttpHeaders.AUTHORIZATION))
                .flatMap(c -> users.authenticate(c[0], c[1]));
        if (user.isEmpty()) {
            context.abortWith(unauthorized());
            return;
        }

        SecurityContext original = context.getSecurityContext();
        AuthenticatedUser principal = user.get();
        context.setSecurityContext(new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return principal;
            }

            @Override
            public boolean isUserInRole(String role) {
                return principal.roles().contains(role);
            }

            @Override
            public boolean isSecure() {
                return original != null && original.isSecure();
            }

            @Override
            public String getAuthenticationScheme() {
                return BASIC_AUTH;
            }
        });
    }

    /** Parses {@code Authorization: Basic base64(user:password)}; empty if absent or malformed. */
    static Optional<String[]> credentials(String header) {
        if (header == null || !header.regionMatches(true, 0, SCHEME + " ", 0, SCHEME.length() + 1)) {
            return Optional.empty();
        }
        try {
            String decoded = new String(
                    Base64.getDecoder().decode(header.substring(SCHEME.length() + 1).trim()),
                    StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon <= 0) {
                return Optional.empty();
            }
            return Optional.of(new String[] {decoded.substring(0, colon), decoded.substring(colon + 1)});
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, SCHEME + " realm=\"" + REALM + "\", charset=\"UTF-8\"")
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorResponse("unauthorized", "Valid credentials are required."))
                .build();
    }
}
