package ma.aui.darija;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import ma.aui.darija.config.AppConfig;
import ma.aui.darija.llm.LlmClient;
import ma.aui.darija.llm.LlmClientFactory;
import org.glassfish.grizzly.http.server.HttpServer;

/** Entry point: {@code java -jar darija-translator-server.jar}. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        AppConfig config;
        try {
            config = AppConfig.fromEnv();
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(2);
            return;
        }

        LlmClient llm = LlmClientFactory.create(config);
        HttpServer server = DarijaServer.start(config, llm);

        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdownNow();
            stopped.countDown();
        }, "shutdown"));

        System.out.println();
        System.out.println("Darija Translator is running");
        System.out.println("  Endpoint : http://" + displayHost(config) + ":" + config.port() + "/api/translate");
        System.out.println("  Health   : http://" + displayHost(config) + ":" + config.port() + "/api/health");
        System.out.println("  Provider : " + llm.provider() + " (model: " + llm.model() + ")");
        System.out.println("  Login    : " + config.username() + " / "
                + (config.passwordGenerated()
                        ? config.password() + "   <- generated for this run; set API_PASSWORD to choose your own"
                        : "(value of API_PASSWORD)"));
        System.out.println("Press Ctrl+C to stop.");
        System.out.println();

        stopped.await();
    }

    private static String displayHost(AppConfig config) {
        return config.host().equals("0.0.0.0") ? "localhost" : config.host();
    }
}
