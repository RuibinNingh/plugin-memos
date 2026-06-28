package run.halo.memos;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.theme.router.PageUrlUtils;
import run.halo.app.theme.router.UrlContextListResult;
import run.halo.memos.finders.MomentFinder;
import run.halo.memos.vo.MomentVo;

/**
 * Renders the theme {@code moments.html}/{@code moment.html} templates for
 * {@code /moments} URLs. Data comes live from the memos server via
 * {@link MomentFinder} (no caching).
 */
@Component
@RequiredArgsConstructor
public class MomentRouter {

    private static final String TAG_PARAM = "tag";
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final MomentFinder momentFinder;
    private final ReactiveSettingFetcher settingFetcher;

    @Bean
    RouterFunction<ServerResponse> momentRouterFunction() {
        return route(GET("/moments").or(GET("/moments/page/{page:\\d+}")), handlerFunction())
            .andRoute(GET("/moments/{momentName:\\S+}"), handlerMomentDefault());
    }

    private HandlerFunction<ServerResponse> handlerMomentDefault() {
        return request -> {
            String momentName = request.pathVariable("momentName");
            return ServerResponse.ok().render("moment",
                Map.of("moment", momentFinder.get(momentName),
                    ModelConst.TEMPLATE_ID, "moment",
                    "title", getMomentTitle()));
        };
    }

    private HandlerFunction<ServerResponse> handlerFunction() {
        return request -> ServerResponse.ok().render("moments",
            Map.of("moments", momentList(request),
                ModelConst.TEMPLATE_ID, "moments",
                "tags", momentFinder.listAllTags(),
                "title", getMomentTitle()));
    }

    Mono<String> getMomentTitle() {
        return settingFetcher.get("base")
            .map(setting -> setting.path("title").asText("瞬间"))
            .defaultIfEmpty("瞬间");
    }

    private Mono<UrlContextListResult<MomentVo>> momentList(ServerRequest request) {
        String path = request.path();
        String tagVal = request.queryParam(TAG_PARAM).filter(StringUtils::hasText).orElse(null);
        int pageNum = PageUrlUtils.pageNum(request);
        return settingFetcher.get("base")
            .map(item -> item.path("pageSize").asInt(DEFAULT_PAGE_SIZE))
            .defaultIfEmpty(DEFAULT_PAGE_SIZE)
            .flatMap(pageSize -> momentFinder.listByTag(pageNum, pageSize, tagVal)
                .map(list -> new UrlContextListResult.Builder<MomentVo>()
                    .listResult(list)
                    .nextUrl(appendTagParam(
                        PageUrlUtils.nextPageUrl(path, PageUrlUtils.totalPage(list)), tagVal))
                    .prevUrl(appendTagParam(PageUrlUtils.prevPageUrl(path), tagVal))
                    .build()));
    }

    private String appendTagParam(String url, String tag) {
        if (tag == null || url == null) {
            return url;
        }
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "tag=" + tag;
    }
}
