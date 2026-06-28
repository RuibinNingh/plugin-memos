package run.halo.memos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    /**
     * A dedicated {@link WebClient} for talking to the memos server. A larger
     * in-memory buffer is configured because a single memos page may carry many
     * attachments.
     */
    @Bean
    public WebClient memosWebClient() {
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(java.time.Duration.ofSeconds(30));
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();
    }
}
