package run.halo.memos.finders.impl;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.theme.finders.Finder;
import run.halo.memos.client.MemosClient;
import run.halo.memos.client.MemosClient.MemoDto;
import run.halo.memos.finders.MomentFinder;
import run.halo.memos.sync.MemosMapper;
import run.halo.memos.vo.ContributorVo;
import run.halo.memos.vo.MomentTagVo;
import run.halo.memos.vo.MomentVo;
import run.halo.memos.vo.Stats;

/**
 * Real-time {@code momentFinder}. Fetches memos live via {@link MemosClient} on
 * each call and maps to {@link MomentVo}. No persistence, no caching.
 * {@code @Finder} is meta-annotated {@code @Service}, so no {@code @Component}.
 */
@Finder("momentFinder")
@RequiredArgsConstructor
public class MomentFinderImpl implements MomentFinder {

    private static final int DEFAULT_SIZE = 10;
    private static final int TAG_AGGREGATE_LIMIT = 100;
    private static final int LIST_ALL_MAX_PAGES = 50;

    private final MemosClient memosClient;
    private final MemosMapper mapper;
    private final ReactiveExtensionClient client;
    private final ReactiveSettingFetcher settingFetcher;

    @Override
    public Flux<MomentVo> listAll() {
        return withConfig().flatMapMany(config ->
            memosClient.listAll(config.baseUrl(), config.accessToken(), TAG_AGGREGATE_LIMIT,
                    LIST_ALL_MAX_PAGES)
                .map(memo -> toVo(memo, config))
                .flatMapSequential(vo -> fillOwner(vo, config)));
    }

    @Override
    public Mono<ListResult<MomentVo>> list(Integer page, Integer size) {
        return listByTag(normalizePage(page), size, null);
    }

    @Override
    public Mono<ListResult<MomentVo>> list(Map<String, Object> params) {
        int page = parseInt(params.get("page"), 1);
        int size = parseInt(params.get("size"), DEFAULT_SIZE);
        Object tag = params.get("tagName");
        String tagName = tag == null ? null : String.valueOf(tag);
        return listByTag(page, size, tagName);
    }

    @Override
    public Flux<MomentVo> listBy(String tag) {
        return withConfig().flatMapMany(config ->
            memosClient.listAll(config.baseUrl(), config.accessToken(), TAG_AGGREGATE_LIMIT,
                    LIST_ALL_MAX_PAGES)
                .filter(memo -> !StringUtils.hasText(tag) || hasTag(memo, tag))
                .map(memo -> toVo(memo, config))
                .flatMapSequential(vo -> fillOwner(vo, config)));
    }

    @Override
    public Mono<MomentVo> get(String momentName) {
        return withConfig().flatMap(config -> {
            String uid = mapper.uidFromMomentName(momentName);
            return memosClient.getMemo(config.baseUrl(), config.accessToken(), uid)
                .map(memo -> toVo(memo, config))
                .flatMap(vo -> fillOwner(vo, config));
        });
    }

    @Override
    public Flux<MomentTagVo> listAllTags() {
        return withConfig().flatMapMany(config ->
            memosClient.listAll(config.baseUrl(), config.accessToken(), TAG_AGGREGATE_LIMIT,
                    LIST_ALL_MAX_PAGES)
                .map(memo -> memo.getTags() == null ? List.<String>of() : memo.getTags())
                .flatMapIterable(tags -> tags)
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()))
                .flatMapMany(counts -> Flux.fromIterable(counts.entrySet()))
                .map(entry -> MomentTagVo.builder()
                    .name(entry.getKey())
                    .permalink("/moments?tag=" + URLEncoder.encode(entry.getKey(),
                        StandardCharsets.UTF_8))
                    .momentCount(entry.getValue().intValue())
                    .build()));
    }

    @Override
    public Mono<ListResult<MomentVo>> listByTag(int pageNum, Integer pageSize, String tagName) {
        int page = Math.max(1, pageNum);
        int size = normalizeSize(pageSize);
        return withConfig().flatMap(config -> walkToPage(config, page, size, tagName)
            .flatMap(pageResult -> {
                List<MemoDto> dtos = pageResult.memos();
                boolean hasNext = StringUtils.hasText(pageResult.nextPageToken());
                return Flux.fromIterable(dtos)
                    .map(memo -> toVo(memo, config))
                    .flatMapSequential(vo -> fillOwner(vo, config))
                    .collectList()
                    .map(vos -> {
                        long total = hasNext
                            ? (long) page * size + 1
                            : (long) (page - 1) * size + vos.size();
                        return new ListResult<>(page, size, total, vos);
                    });
            }));
    }

    /**
     * Walks memos page tokens until reaching the requested page (1-based).
     * memos uses opaque nextPageToken, so deeper pages require walking.
     */
    private Mono<PageResult> walkToPage(Config config, int page, int size, String tagName) {
        return fetchPage(config, size, null, tagName)
            .expand(prev -> StringUtils.hasText(prev.nextPageToken())
                ? fetchPage(config, size, prev.nextPageToken(), tagName)
                : Mono.empty())
            .take(page)
            .last()
            .onErrorResume(error -> Mono.just(new PageResult(List.of(), null)));
    }

    private Mono<PageResult> fetchPage(Config config, int size, String pageToken, String tagName) {
        return memosClient.listPage(config.baseUrl(), config.accessToken(), size, pageToken)
            .map(p -> {
                List<MemoDto> memos = p.getMemos() == null ? List.of() : p.getMemos();
                if (StringUtils.hasText(tagName)) {
                    memos = memos.stream().filter(m -> hasTag(m, tagName)).toList();
                }
                return new PageResult(memos, p.getNextPageToken());
            });
    }

    // --- helpers ---

    private boolean hasTag(MemoDto memo, String tag) {
        return memo.getTags() != null && memo.getTags().contains(tag);
    }

    private MomentVo toVo(MemoDto memo, Config config) {
        var moment = mapper.toMoment(memo, config.baseUrl());
        return MomentVo.builder()
            .metadata(moment.getMetadata())
            .spec(moment.getSpec())
            .stats(Stats.empty())
            .build();
    }

    private Mono<MomentVo> fillOwner(MomentVo vo, Config config) {
        String ownerName = config.owner();
        if (!StringUtils.hasText(ownerName)) {
            return Mono.just(vo);
        }
        return client.fetch(User.class, ownerName)
            .map(ContributorVo::from)
            .doOnNext(vo::setOwner)
            .thenReturn(vo)
            .defaultIfEmpty(vo);
    }

    private Mono<Config> withConfig() {
        return settingFetcher.get("base")
            .map(node -> new Config(
                node.path("baseUrl").asText("http://127.0.0.1:5230"),
                node.path("accessToken").asText(""),
                node.path("owner").asText("admin")))
            .defaultIfEmpty(new Config("http://127.0.0.1:5230", "", "admin"));
    }

    private static int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private static int normalizeSize(Integer size) {
        return size == null || size <= 0 ? DEFAULT_SIZE : size;
    }

    private static int parseInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record Config(String baseUrl, String accessToken, String owner) {
    }

    private record PageResult(List<MemoDto> memos, String nextPageToken) {
    }
}
