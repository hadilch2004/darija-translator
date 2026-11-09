package ma.aui.darija.api;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import ma.aui.darija.auth.Secured;
import ma.aui.darija.auth.UserStore;
import ma.aui.darija.llm.LlmException;
import ma.aui.darija.model.TranslationRequest;
import ma.aui.darija.model.TranslationResponse;
import ma.aui.darija.service.ApiException;
import ma.aui.darija.service.TranslationService;

/**
 * Jakarta REST resource exposing the translation business method.
 *
 * <pre>
 * POST /api/translate   {"text": "Hello", "sourceLanguage": "English", "script": "arabic"}
 * GET  /api/translate?text=Hello&amp;source=English&amp;script=latin
 * </pre>
 *
 * Both require HTTP Basic credentials ({@link Secured}) and the {@value UserStore#ROLE_TRANSLATOR} role.
 */
@Path("/translate")
@Secured
@RolesAllowed(UserStore.ROLE_TRANSLATOR)
@Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
public class TranslatorResource {

    private final TranslationService service;

    @Inject
    public TranslatorResource(TranslationService service) {
        this.service = service;
    }

    /** Translates the JSON {@code text} into Moroccan Arabic. */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public TranslationResponse translate(TranslationRequest request) throws LlmException {
        if (request == null) {
            throw new ApiException(400, "invalid_request", "A JSON request body is required.");
        }
        return service.translate(request.text(), request.sourceLanguage(), request.script());
    }

    /** Convenience variant for cURL and browsers; prefer POST for long or sensitive text. */
    @GET
    public TranslationResponse translate(
            @QueryParam("text") String text,
            @QueryParam("source") String sourceLanguage,
            @QueryParam("script") String script) throws LlmException {
        return service.translate(text, sourceLanguage, script);
    }
}
