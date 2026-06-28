package run.halo.memos.finders;

import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;
import run.halo.memos.vo.MomentTagVo;
import run.halo.memos.vo.MomentVo;

/**
 * Theme-facing Finder for moments. Exposed to Thymeleaf as
 * {@code ${momentFinder}}. All methods fetch live from memos (no caching).
 * Shape mirrors the official plugin-moments finder so xingdu templates work
 * unchanged.
 */
public interface MomentFinder {

    Flux<MomentVo> listAll();

    Mono<ListResult<MomentVo>> list(Integer page, Integer size);

    Mono<ListResult<MomentVo>> list(Map<String, Object> params);

    Flux<MomentVo> listBy(String tag);

    Mono<MomentVo> get(String momentName);

    Flux<MomentTagVo> listAllTags();

    Mono<ListResult<MomentVo>> listByTag(int pageNum, Integer pageSize, String tagName);
}
