package run.halo.memos.endpoint;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.memos.cache.ImageCacheService;
import run.halo.memos.cache.ImageCacheWarmupService;

@Component
@RequiredArgsConstructor
public class MemosCacheEndpoint implements CustomEndpoint {

    private final ImageCacheService imageCacheService;
    private final ImageCacheWarmupService warmupService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return org.springframework.web.reactive.function.server.RouterFunctions.route()
            .GET("/cache/status", this::status)
            .POST("/cache/refresh", this::refresh)
            .POST("/cache/clear", this::clear)
            .build();
    }

    private Mono<ServerResponse> status(ServerRequest request) {
        return imageCacheService.stats()
            .flatMap(stats -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                    "cache", stats,
                    "warmup", warmupService.status())));
    }

    private Mono<ServerResponse> refresh(ServerRequest request) {
        return warmupService.refreshFromConsole()
            .flatMap(result -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result));
    }

    private Mono<ServerResponse> clear(ServerRequest request) {
        return imageCacheService.clear()
            .flatMap(deleted -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("deleted", deleted)));
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("console.api.memos.plugin.halo.run", "v1alpha1");
    }
}
