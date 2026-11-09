package ma.aui.darija.model;

/**
 * Uniform JSON error body.
 *
 * @param error   short machine-readable code, e.g. "invalid_request"
 * @param message human-readable explanation
 */
public record ErrorResponse(String error, String message) {
}
