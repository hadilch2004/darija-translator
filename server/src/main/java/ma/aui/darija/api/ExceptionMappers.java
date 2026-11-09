package ma.aui.darija.api;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;
import ma.aui.darija.llm.LlmException;
import ma.aui.darija.model.ErrorResponse;
import ma.aui.darija.service.ApiException;

/** Every failure leaves the service as a JSON {@link ErrorResponse}, never an HTML stack trace. */
public final class ExceptionMappers {

    private static final Logger LOG = Logger.getLogger(ExceptionMappers.class.getName());

    private ExceptionMappers() {
    }

    private static Response json(int status, String code, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE.withCharset("utf-8"))
                .entity(new ErrorResponse(code, message))
                .build();
    }

    /** Validation problems raised by the service layer. */
    @Provider
    public static class Api implements ExceptionMapper<ApiException> {
        @Override
        public Response toResponse(ApiException e) {
            return json(e.status(), e.code(), e.getMessage());
        }
    }

    /** Problems talking to Gemini / Ollama. */
    @Provider
    public static class Llm implements ExceptionMapper<LlmException> {
        @Override
        public Response toResponse(LlmException e) {
            LOG.log(Level.WARNING, "LLM call failed: {0} (upstream status {1})",
                    new Object[] {e.kind(), e.upstreamStatus()});
            return switch (e.kind()) {
                case TIMEOUT -> json(504, "llm_timeout", e.getMessage());
                case NETWORK -> json(502, "llm_unreachable", e.getMessage());
                case BLOCKED -> json(422, "llm_blocked", e.getMessage());
                case EMPTY -> json(502, "llm_empty_answer", e.getMessage());
                case UPSTREAM -> switch (e.upstreamStatus()) {
                    case 429 -> json(429, "llm_rate_limited",
                            "The LLM provider's rate limit or quota was reached. Try again shortly. " + e.getMessage());
                    case 400, 401, 403 -> json(502, "llm_rejected_request",
                            "The LLM provider rejected the server's request (check the API key and model name). "
                                    + e.getMessage());
                    case 404 -> json(502, "llm_model_not_found",
                            "The configured model was not found at the LLM provider. " + e.getMessage());
                    default -> json(502, "llm_unavailable", e.getMessage());
                };
            };
        }
    }

    /** Framework-raised HTTP errors (401, 403, 404, 405, 415, ...). */
    @Provider
    public static class WebApplication implements ExceptionMapper<WebApplicationException> {
        @Override
        public Response toResponse(WebApplicationException e) {
            Response original = e.getResponse();
            int status = original.getStatus();
            Response.ResponseBuilder builder = Response.fromResponse(original)
                    .type(MediaType.APPLICATION_JSON_TYPE.withCharset("utf-8"))
                    .entity(new ErrorResponse(codeFor(status), messageFor(status, e)));
            return builder.build();
        }

        private static String codeFor(int status) {
            return switch (status) {
                case 400 -> "invalid_request";
                case 401 -> "unauthorized";
                case 403 -> "forbidden";
                case 404 -> "not_found";
                case 405 -> "method_not_allowed";
                case 406 -> "not_acceptable";
                case 415 -> "unsupported_media_type";
                default -> "http_" + status;
            };
        }

        private static String messageFor(int status, WebApplicationException e) {
            return switch (status) {
                case 404 -> "No such resource.";
                case 405 -> "This HTTP method is not allowed here.";
                case 415 -> "Send the request body as application/json.";
                default -> e.getMessage() == null ? "HTTP " + status : e.getMessage();
            };
        }
    }

    /** Malformed JSON body. */
    @Provider
    public static class JsonParse implements ExceptionMapper<JsonParseException> {
        @Override
        public Response toResponse(JsonParseException e) {
            return json(400, "invalid_json", "The request body is not valid JSON.");
        }
    }

    /** JSON that does not match the expected shape. */
    @Provider
    public static class JsonMapping implements ExceptionMapper<JsonMappingException> {
        @Override
        public Response toResponse(JsonMappingException e) {
            return json(400, "invalid_json", "The request body does not have the expected fields.");
        }
    }

    /** Last resort: log the details server-side, tell the client nothing sensitive. */
    @Provider
    public static class CatchAll implements ExceptionMapper<Throwable> {
        @Override
        public Response toResponse(Throwable t) {
            LOG.log(Level.SEVERE, "Unhandled error", t);
            return json(500, "internal_error", "Unexpected server error.");
        }
    }
}
