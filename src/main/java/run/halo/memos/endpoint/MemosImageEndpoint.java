package run.halo.memos.endpoint;

import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.memos.cache.ImageCacheService;
import run.halo.memos.cache.ImageCacheSettings;

@Component
@RequiredArgsConstructor
public class MemosImageEndpoint {

    private static final String ROUTE_PREFIX = "/memos/proxy/image";

    private final ImageCacheService imageCacheService;
    private final ImageCacheSettings imageCacheSettings;

    @Bean
    RouterFunction<ServerResponse> publicMemosImageRouterFunction() {
        return org.springframework.web.reactive.function.server.RouterFunctions.route(
            org.springframework.web.reactive.function.server.RequestPredicates.GET(ROUTE_PREFIX + "/**"),
            this::image);
    }

    private Mono<ServerResponse> image(ServerRequest request) {
        String path = suffix(request.uri().getRawPath());
        return imageCacheSettings.get()
            .map(properties -> {
                int width = parseInt(request.queryParam("w").orElse(""), properties.width());
                int quality = parseInt(request.queryParam("q").orElse(""), properties.quality());
                return new run.halo.memos.cache.ImageCacheProperties(
                    properties.baseUrl(),
                    properties.accessToken(),
                    properties.enabled(),
                    width,
                    quality,
                    properties.minSourceBytes(),
                    properties.warmupEnabled(),
                    properties.warmupPageSize(),
                    properties.warmupMaxPages(),
                    properties.maxAgeDays());
            })
            .flatMap(properties -> imageCacheService.getOrCreate(path, properties))
            .flatMap(result -> {
                if (result.useOriginal()) {
                    return ServerResponse.temporaryRedirect(URI.create(result.originalUrl())).build();
                }
                FileSystemResource resource = new FileSystemResource(result.path());
                return ServerResponse.ok()
                    .contentType(MediaType.parseMediaType(result.contentType()))
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                    .header("X-Memos-Image-Cache", result.cacheHit() ? "HIT" : "MISS")
                    .body(BodyInserters.fromResource(resource));
            })
            .onErrorResume(error -> ServerResponse.temporaryRedirect(
                URI.create("/memos/proxy/file/" + path)).build());
    }

    private String suffix(String rawPath) {
        int idx = rawPath.indexOf(ROUTE_PREFIX);
        if (idx < 0) {
            return "";
        }
        String value = rawPath.substring(idx + ROUTE_PREFIX.length());
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
