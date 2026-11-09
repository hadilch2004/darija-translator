package ma.aui.darija.auth;

import java.security.Principal;
import java.util.Set;

/** The caller identity attached to the request's {@code SecurityContext} after a successful login. */
public record AuthenticatedUser(String name, Set<String> roles) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
