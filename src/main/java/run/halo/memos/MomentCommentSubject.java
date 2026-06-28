package run.halo.memos;

import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.content.comment.CommentSubject;
import run.halo.app.extension.GroupVersionKind;
import run.halo.app.extension.Ref;
import run.halo.app.infra.ExternalLinkProcessor;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.memos.client.MemosClient;
import run.halo.memos.sync.MemosMapper;

/**
 * Lets Halo's comment system attach to memos-backed moments (kind=Moment,
 * group=moment.halo.run). {@code get(name)} resolves the subject live from
 * memos by reversing {@code memos-{uid}} -> {@code {uid}}.
 */
@Component
@RequiredArgsConstructor
public class MomentCommentSubject implements CommentSubject<Moment> {

    private static final int PREVIEW_MAX_LENGTH = 100;

    private final MemosClient memosClient;
    private final MemosMapper mapper;
    private final ReactiveSettingFetcher settingFetcher;
    private final ExternalLinkProcessor externalLinkProcessor;

    private final GroupVersionKind gvk = GroupVersionKind.fromExtension(Moment.class);

    @Override
    public Mono<Moment> get(String name) {
        return withConfig().flatMap(config -> {
            String uid = mapper.uidFromMomentName(name);
            return memosClient.getMemo(config.baseUrl(), config.accessToken(), uid)
                .map(memo -> mapper.toMoment(memo, config.baseUrl()));
        });
    }

    @Override
    public Mono<SubjectDisplay> getSubjectDisplay(String name) {
        return get(name).map(moment -> {
            String content = Optional.ofNullable(moment.getSpec())
                .map(Moment.MomentSpec::getContent)
                .map(Moment.MomentContent::getRaw)
                .map(raw -> Jsoup.clean(raw, Safelist.none()))
                .map(raw -> raw.length() > PREVIEW_MAX_LENGTH
                    ? raw.substring(0, PREVIEW_MAX_LENGTH) : raw)
                .orElse(name);
            String momentUrl = externalLinkProcessor.processLink("/moments/" + name);
            return new CommentSubject.SubjectDisplay(content, momentUrl, "瞬间");
        });
    }

    @Override
    public boolean supports(Ref ref) {
        if (ref == null) {
            return false;
        }
        return Objects.equals(gvk.group(), ref.getGroup())
            && Objects.equals(gvk.kind(), ref.getKind());
    }

    private Mono<Config> withConfig() {
        return settingFetcher.get("base")
            .map(node -> new Config(
                node.path("baseUrl").asText("http://127.0.0.1:5230"),
                node.path("accessToken").asText("")))
            .defaultIfEmpty(new Config("http://127.0.0.1:5230", ""));
    }

    private record Config(String baseUrl, String accessToken) {
    }
}
