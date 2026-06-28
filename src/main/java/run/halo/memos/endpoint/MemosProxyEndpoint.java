package run.halo.memos.endpoint;

import java.net.URI;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.plugin.ReactiveSettingFetcher;

/**
 * Reverse-proxies the configured memos server so both the Console page and
 * theme-side image rendering can fetch memos (JSON list + binary attachments)
 * through Halo without caching.
 *
 * <p>Mounted for Console/API use at
 * {@code /apis/api.memos.plugin.halo.run/v1alpha1/proxy/**}. Theme-side file
 * rendering uses the public {@code /memos/proxy/file/**} route. Only GET is
 * forwarded; the upstream {@code baseUrl} is trusted (configured by a
 * super-admin), so the SSRF surface is bounded to paths under that one host.</p>
 */
@Component
@RequiredArgsConstructor
public class MemosProxyEndpoint implements CustomEndpoint {

    private static final String ROUTE_PREFIX = "/proxy";
    private static final String PUBLIC_FILE_ROUTE_PREFIX = "/memos/proxy/file";
    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:5230";

    private final WebClient memosWebClient;
    private final ReactiveSettingFetcher settingFetcher;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return org.springframework.web.reactive.function.server.RouterFunctions.route(
            org.springframework.web.reactive.function.server.RequestPredicates.GET(ROUTE_PREFIX + "/**"),
            this::proxy);
    }

    @Bean
    RouterFunction<ServerResponse> publicMemosFileProxyRouterFunction() {
        return org.springframework.web.reactive.function.server.RouterFunctions.route(
            org.springframework.web.reactive.function.server.RequestPredicates.GET(
                PUBLIC_FILE_ROUTE_PREFIX + "/**"),
            this::proxy);
    }

    private Mono<ServerResponse> proxy(ServerRequest request) {
        return loadConfig().flatMap(config -> {
            String suffix = suffixAfterPrefix(request.path());
            String base = StringUtils.trimTrailingCharacter(config.baseUrl(), '/');
            StringBuilder target = new StringBuilder(base).append(suffix);
            String query = request.uri().getRawQuery();
            if (StringUtils.hasText(query)) {
                target.append('?').append(query);
            }

            WebClient.RequestHeadersSpec<?> spec =
                memosWebClient.get().uri(URI.create(target.toString()));
            if (StringUtils.hasText(config.accessToken())) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + config.accessToken());
            }

            // exchangeToMono releases the upstream ClientResponse as soon as the
            // returned Mono emits, so a lazily-streamed body Flux would be cut
            // off (empty body). Collecting the buffers inside the lambda keeps
            // the response open while reading, then hands the buffers to the
            // writer. The client's maxInMemorySize (16 MB) bounds this.
            return spec.exchangeToMono(upstream -> {
                HttpHeaders httpHeaders = upstream.headers().asHttpHeaders();
                ServerResponse.BodyBuilder builder = ServerResponse.status(upstream.statusCode());
                MediaType contentType = httpHeaders.getContentType();
                if (contentType != null) {
                    builder.contentType(contentType);
                }
                if (httpHeaders.getETag() != null) {
                    builder.eTag(httpHeaders.getETag());
                }
                return upstream.bodyToFlux(DataBuffer.class)
                    .collectList()
                    .flatMap(buffers -> builder.body(
                        Flux.fromIterable(buffers), DataBuffer.class));
            });
        }).onErrorResume(error -> ServerResponse.badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("error", error.getMessage() == null ? "proxy error" : error.getMessage())));
    }

    /**
     * Extracts the part of the request path after the {@code /proxy} route
     * prefix, e.g. {@code /api/v1/memos} or {@code /file/attachments/x/y.png}.
     */
    private String suffixAfterPrefix(String fullPath) {
        int idx = fullPath.indexOf(ROUTE_PREFIX);
        if (idx < 0) {
            return "";
        }
        return fullPath.substring(idx + ROUTE_PREFIX.length());
    }

    private Mono<Config> loadConfig() {
        return settingFetcher.get("base")
            .map(node -> new Config(
                node.path("baseUrl").asText(DEFAULT_BASE_URL),
                node.path("accessToken").asText("")))
            .defaultIfEmpty(new Config(DEFAULT_BASE_URL, ""));
    }

    private record Config(String baseUrl, String accessToken) {
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("api.memos.plugin.halo.run", "v1alpha1");
    }
}
