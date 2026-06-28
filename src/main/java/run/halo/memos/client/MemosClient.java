package run.halo.memos.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Reactive client for the memos public API. Calls the configured memos server
 * live (no caching). Anonymous access already returns only PUBLIC memos; an
 * optional access token is sent as a Bearer header when configured.
 */
@Component
@RequiredArgsConstructor
public class MemosClient {

    private static final String MEMOS_PATH = "/api/v1/memos";

    private final WebClient memosWebClient;

    /**
     * Fetch a single page of memos.
     */
    public Mono<MemosPage> listPage(String baseUrl, String accessToken, int pageSize,
        String pageToken) {
        int size = pageSize <= 0 ? 20 : Math.min(pageSize, 500);
        WebClient.RequestHeadersSpec<?> request = client(baseUrl, accessToken)
            .get()
            .uri(builder -> {
                builder.path(MEMOS_PATH);
                builder.queryParam("pageSize", size);
                if (StringUtils.hasText(pageToken)) {
                    builder.queryParam("pageToken", pageToken);
                }
                return builder.build();
            });
        return request.retrieve()
            .bodyToMono(MemosPage.class)
            .timeout(Duration.ofSeconds(30))
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)));
    }

    /**
     * Fetch a single memo by its uid (the part after {@code memos/}).
     * Endpoint verified locally: {@code GET /api/v1/memos/{uid}} -> 200.
     */
    public Mono<MemoDto> getMemo(String baseUrl, String accessToken, String uid) {
        return client(baseUrl, accessToken)
            .get()
            .uri(builder -> builder.path(MEMOS_PATH + "/" + uid).build())
            .retrieve()
            .bodyToMono(MemoDto.class)
            .timeout(Duration.ofSeconds(30))
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)));
    }

    /**
     * Stream all memos across pages (bounded by maxPages to avoid runaway).
     */
    public Flux<MemoDto> listAll(String baseUrl, String accessToken, int pageSize, int maxPages) {
        return listPage(baseUrl, accessToken, pageSize, null)
            .expand(page -> StringUtils.hasText(page.getNextPageToken())
                ? listPage(baseUrl, accessToken, pageSize, page.getNextPageToken())
                : Mono.empty())
            .take(maxPages)
            .flatMapIterable(page -> page.getMemos() == null ? List.of() : page.getMemos());
    }

    private WebClient client(String baseUrl, String accessToken) {
        WebClient.Builder builder = memosWebClient.mutate().baseUrl(baseUrl);
        if (StringUtils.hasText(accessToken)) {
            builder.defaultHeader("Authorization", "Bearer " + accessToken);
        }
        return builder.build();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MemosPage {
        private List<MemoDto> memos;
        private String nextPageToken;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MemoDto {
        private String name;
        private String content;
        private String createTime;
        private String updateTime;
        private String visibility;
        private List<String> tags;
        private Boolean pinned;
        private List<AttachmentDto> attachments;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttachmentDto {
        private String name;
        private String filename;
        private String externalLink;
        private String type;
        private String size;
    }
}
