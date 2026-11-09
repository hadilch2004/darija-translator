package ma.aui.darija;

import java.io.IOException;
import java.net.URI;
import ma.aui.darija.config.AppConfig;
import ma.aui.darija.llm.LlmClient;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http2.Http2AddOn;
import org.glassfish.grizzly.http2.Http2Configuration;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;

/** Starts the embedded Grizzly container that hosts the Jakarta REST application under {@code /api}. */
public final class DarijaServer {

    public static final String API_PATH = "/api/";

    private DarijaServer() {
    }

    public static HttpServer start(AppConfig config, LlmClient llm) throws IOException {
        URI baseUri = URI.create("http://" + config.host() + ":" + config.port() + API_PATH);
        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseUri, new DarijaApplication(config, llm), false);

        // Without the HTTP/2 add-on Grizzly stalls on POST bodies from clients that ask to upgrade to
        // cleartext HTTP/2 (the JDK HttpClient does by default). With it, HTTP/1.1 and h2c both work.
        for (NetworkListener listener : server.getListeners()) {
            listener.registerAddOn(new Http2AddOn(Http2Configuration.builder().build()));
        }

        server.start();
        return server;
    }
}
