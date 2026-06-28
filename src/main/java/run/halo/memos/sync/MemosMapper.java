package run.halo.memos.sync;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;
import run.halo.app.extension.Metadata;
import run.halo.memos.Moment;
import run.halo.memos.client.MemosClient.AttachmentDto;
import run.halo.memos.client.MemosClient.MemoDto;

/**
 * Maps a memos {@link MemoDto} into a transient {@link Moment} (and its VO).
 * Renders markdown to HTML and resolves attachment URLs. Uid case is preserved
 * (memos uids are case-sensitive) so {@code get(name)} can reverse the name.
 */
@Component
public class MemosMapper {

    public static final String ANNO_EXCERPT = "thyuu_post_excerpt";
    public static final String ANNO_PINNED = "memos.plugin.halo.run/pinned";
    public static final String PROXY_BASE = "/memos/proxy";
    private static final int EXCERPT_MAX = 100;

    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().build();

    /**
     * Build a transient {@link Moment} (with {@code metadata.name =
     * "memos-" + uid}) carrying spec + annotations. Owner is filled later by
     * the finder.
     */
    public Moment toMoment(MemoDto memo, String baseUrl) {
        Moment moment = new Moment();
        Metadata metadata = new Metadata();
        metadata.setName(toMomentName(memo.getName()));
        metadata.setAnnotations(buildAnnotations(memo));
        moment.setMetadata(metadata);
        moment.setSpec(buildSpec(memo));
        return moment;
    }

    private Moment.MomentSpec buildSpec(MemoDto memo) {
        Moment.MomentSpec spec = new Moment.MomentSpec();
        spec.setVisible(Moment.MomentVisible.PUBLIC);
        spec.setApproved(true);
        spec.setReleaseTime(parseInstant(memo.getCreateTime()));

        Moment.MomentContent content = new Moment.MomentContent();
        String raw = memo.getContent() == null ? "" : memo.getContent();
        content.setRaw(raw);
        content.setHtml(renderMarkdown(raw));
        content.setMedium(toMedium(memo.getAttachments()));
        spec.setContent(content);

        spec.setTags(toTags(memo.getTags()));
        return spec;
    }

    private Map<String, String> buildAnnotations(MemoDto memo) {
        Map<String, String> annotations = new LinkedHashMap<>();
        annotations.put(ANNO_EXCERPT, excerptOf(memo.getContent()));
        annotations.put(ANNO_PINNED, String.valueOf(Boolean.TRUE.equals(memo.getPinned())));
        return annotations;
    }

    private String excerptOf(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String plain = content.replaceAll("[#*`>\\[\\]()!~-]", "").trim();
        return plain.length() > EXCERPT_MAX ? plain.substring(0, EXCERPT_MAX) + "…" : plain;
    }

    private List<Moment.MomentMedia> toMedium(List<AttachmentDto> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        List<Moment.MomentMedia> medium = new ArrayList<>();
        for (AttachmentDto attachment : attachments) {
            Moment.MomentMedia media = new Moment.MomentMedia();
            media.setType(classify(attachment.getType()));
            media.setUrl(resolveUrl(attachment));
            media.setOriginType(attachment.getType());
            medium.add(media);
        }
        return medium;
    }

    private Moment.MomentMediaType classify(String mime) {
        if (mime == null) {
            return Moment.MomentMediaType.PHOTO;
        }
        if (mime.startsWith("image/")) {
            return Moment.MomentMediaType.PHOTO;
        }
        if (mime.startsWith("video/")) {
            return Moment.MomentMediaType.VIDEO;
        }
        if (mime.startsWith("audio/")) {
            return Moment.MomentMediaType.AUDIO;
        }
        return Moment.MomentMediaType.PHOTO;
    }

    private String resolveUrl(AttachmentDto attachment) {
        if (StringUtils.hasText(attachment.getExternalLink())) {
            return attachment.getExternalLink();
        }
        String uid = attachment.getName();
        if (uid != null && uid.startsWith("attachments/")) {
            uid = uid.substring("attachments/".length());
        }
        String filename = attachment.getFilename() == null ? "" : attachment.getFilename();
        return PROXY_BASE + "/file/attachments/" + pathSegment(uid) + "/"
            + pathSegment(filename);
    }

    private String pathSegment(String value) {
        return UriUtils.encodePathSegment(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private Set<String> toTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(tags);
    }

    private String renderMarkdown(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return "";
        }
        return RENDERER.render(PARSER.parse(markdown));
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    /**
     * {@code memos/{uid}} -> {@code memos-{uid}} (case preserved).
     */
    public String toMomentName(String memosName) {
        String uid = uidOf(memosName);
        return "memos-" + uid;
    }

    /**
     * {@code memos-{uid}} -> {@code {uid}}.
     */
    public String uidFromMomentName(String momentName) {
        if (momentName == null) {
            return "";
        }
        int idx = momentName.indexOf("memos-");
        return idx >= 0 ? momentName.substring(idx + "memos-".length()) : momentName;
    }

    private String uidOf(String memosName) {
        if (memosName == null) {
            return "";
        }
        int idx = memosName.lastIndexOf('/');
        return idx >= 0 ? memosName.substring(idx + 1) : memosName;
    }
}
