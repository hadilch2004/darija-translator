package ma.aui.darija.llm;

/** Failure while talking to an LLM provider. Messages never contain credentials. */
public class LlmException extends Exception {

    public enum Kind {
        /** The provider answered with a non-2xx HTTP status. */
        UPSTREAM,
        /** The provider did not answer in time. */
        TIMEOUT,
        /** The provider could not be reached at all. */
        NETWORK,
        /** The provider refused to translate (safety filters). */
        BLOCKED,
        /** The provider answered successfully but with no usable text. */
        EMPTY
    }

    private final Kind kind;
    private final int upstreamStatus;

    private LlmException(Kind kind, int upstreamStatus, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.upstreamStatus = upstreamStatus;
    }

    public static LlmException upstream(int status, String message) {
        return new LlmException(Kind.UPSTREAM, status, message, null);
    }

    public static LlmException timeout(Throwable cause) {
        return new LlmException(Kind.TIMEOUT, 0, "The LLM provider did not respond in time.", cause);
    }

    public static LlmException network(Throwable cause) {
        return new LlmException(Kind.NETWORK, 0, "Could not reach the LLM provider.", cause);
    }

    public static LlmException blocked(String reason) {
        return new LlmException(Kind.BLOCKED, 0, "The LLM provider refused the request: " + reason, null);
    }

    public static LlmException empty() {
        return new LlmException(Kind.EMPTY, 0, "The LLM provider returned an empty answer.", null);
    }

    public Kind kind() {
        return kind;
    }

    /** HTTP status returned by the provider, or 0 when there was none. */
    public int upstreamStatus() {
        return upstreamStatus;
    }
}
