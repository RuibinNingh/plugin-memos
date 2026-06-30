package run.halo.memos.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.ReactiveSettingFetcher;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ImageCacheSettings {

    private final ReactiveSettingFetcher settingFetcher;

    public Mono<ImageCacheProperties> get() {
        return settingFetcher.get("base")
            .map(node -> new ImageCacheProperties(
                node.path("baseUrl").asText(ImageCacheProperties.DEFAULT_BASE_URL),
                node.path("accessToken").asText(""),
                node.path("imageCacheEnabled").asBoolean(true),
                node.path("imageCacheWidth").asInt(ImageCacheProperties.DEFAULT_WIDTH),
                node.path("imageCacheQuality").asInt(ImageCacheProperties.DEFAULT_QUALITY),
                node.path("imageCacheMinBytes").asLong(ImageCacheProperties.DEFAULT_MIN_SOURCE_BYTES),
                node.path("imageCacheWarmupEnabled").asBoolean(true),
                node.path("imageCacheWarmupPageSize").asInt(ImageCacheProperties.DEFAULT_WARMUP_PAGE_SIZE),
                node.path("imageCacheWarmupMaxPages").asInt(ImageCacheProperties.DEFAULT_WARMUP_MAX_PAGES),
                node.path("imageCacheMaxAgeDays").asInt(ImageCacheProperties.DEFAULT_MAX_AGE_DAYS)))
            .defaultIfEmpty(new ImageCacheProperties(
                ImageCacheProperties.DEFAULT_BASE_URL,
                "",
                true,
                ImageCacheProperties.DEFAULT_WIDTH,
                ImageCacheProperties.DEFAULT_QUALITY,
                ImageCacheProperties.DEFAULT_MIN_SOURCE_BYTES,
                true,
                ImageCacheProperties.DEFAULT_WARMUP_PAGE_SIZE,
                ImageCacheProperties.DEFAULT_WARMUP_MAX_PAGES,
                ImageCacheProperties.DEFAULT_MAX_AGE_DAYS));
    }
}
