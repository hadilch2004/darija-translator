package ma.aui.darija.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import ma.aui.darija.service.TranslationService;

/** Unauthenticated liveness probe: {@code GET /api/health}. */
@Path("/health")
@Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
public class HealthResource {

    private final TranslationService service;

    @Inject
    public HealthResource(TranslationService service) {
        this.service = service;
    }

    @GET
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "provider", service.llm().provider(),
                "model", service.llm().model());
    }
}
