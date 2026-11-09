package ma.aui.darija;

import ma.aui.darija.api.CorsFilter;
import ma.aui.darija.api.ExceptionMappers;
import ma.aui.darija.api.HealthResource;
import ma.aui.darija.api.TranslatorResource;
import ma.aui.darija.auth.BasicAuthFilter;
import ma.aui.darija.auth.UserStore;
import ma.aui.darija.config.AppConfig;
import ma.aui.darija.llm.LlmClient;
import ma.aui.darija.service.TranslationService;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

/** Wires the Jakarta REST application: resources, filters, mappers and injectable services. */
public class DarijaApplication extends ResourceConfig {

    public DarijaApplication(AppConfig config, LlmClient llm) {
        TranslationService service = new TranslationService(llm, config.maxTextLength());
        UserStore users = UserStore.singleUser(config.username(), config.password());

        register(TranslatorResource.class);
        register(HealthResource.class);

        register(new CorsFilter(config.corsAllowedOrigin()));
        register(BasicAuthFilter.class);
        register(RolesAllowedDynamicFeature.class);

        register(JacksonFeature.class);
        register(ExceptionMappers.Api.class);
        register(ExceptionMappers.Llm.class);
        register(ExceptionMappers.WebApplication.class);
        register(ExceptionMappers.JsonParse.class);
        register(ExceptionMappers.JsonMapping.class);
        register(ExceptionMappers.CatchAll.class);

        register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(service).to(TranslationService.class);
                bind(users).to(UserStore.class);
            }
        });
    }
}
