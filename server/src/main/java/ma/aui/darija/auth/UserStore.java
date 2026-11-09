package ma.aui.darija.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory credential store. Passwords are kept as SHA-256 digests and compared in constant time,
 * and an unknown user costs the same work as a wrong password (no user enumeration by timing).
 */
public final class UserStore {

    public static final String ROLE_TRANSLATOR = "translator";

    private final Map<String, byte[]> passwordDigests;
    private final byte[] dummyDigest = digest("not-a-real-password");

    private UserStore(Map<String, byte[]> passwordDigests) {
        this.passwordDigests = passwordDigests;
    }

    /** A store with exactly one user holding the {@value #ROLE_TRANSLATOR} role. */
    public static UserStore singleUser(String username, String password) {
        return new UserStore(Map.of(username, digest(password)));
    }

    public Optional<AuthenticatedUser> authenticate(String username, String password) {
        byte[] expected = passwordDigests.get(username);
        boolean knownUser = expected != null;
        boolean matches = MessageDigest.isEqual(knownUser ? expected : dummyDigest, digest(password));
        if (knownUser && matches) {
            return Optional.of(new AuthenticatedUser(username, Set.of(ROLE_TRANSLATOR)));
        }
        return Optional.empty();
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
