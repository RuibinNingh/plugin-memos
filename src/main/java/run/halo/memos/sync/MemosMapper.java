package run.halo.memos.sync;

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
import run.halo.app.extension.Metadata;
import run.halo.memos.Moment;
import run.halo.memos.cache.ImageUrlSupport;
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
        // Tags are surfaced separately (spec.tags -> theme footer / filter nav),
        // so strip the inline "#tag" tokens out of the rendered body to avoid
        // showing each tag twice. raw is left untouched.
        content.setHtml(renderMarkdown(stripInlineTags(raw, memo.getTags())));
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
        return ImageUrlSupport.displayUrl(attachment);
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
        // Memos editor inserts a single '\n' for line breaks; commonmark's
        // default treats a single newline as a soft break (= space), so the
        // text collapses into one long line. Upgrade every '\n' that is NOT
        // a paragraph separator ("\n\n") into a hard break: commonmark turns
        // a trailing "  \n" into <br>. This keeps multi-line memos readable in
        // the theme's /moments page without pulling in commonmark-ext-gfm.
        return RENDERER.render(PARSER.parse(toHardBreaks(markdown)));
    }

    private String toHardBreaks(String markdown) {
        // Normalize CRLF/CR to '\n' first; then for every '\n' that is not
        // already part of a blank line (i.e. not preceded or followed by
        // another '\n'), append two spaces before it.
        String s = markdown.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                boolean prevIsNewline = (i == 0) || s.charAt(i - 1) == '\n';
                boolean nextIsNewline = (i + 1 < s.length()) && s.charAt(i + 1) == '\n';
                if (!prevIsNewline && !nextIsNewline) {
                    out.append("  \n");
                } else {
                    out.append('\n');
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Remove inline {@code #tag} tokens (memos hashtags) from the markdown so the
     * rendered body does not repeat tags that are already exposed via
     * {@code spec.tags}. Tags are stripped as literal {@code "#" + tag} matches
     * (longest first, so {@code #foo} does not eat into {@code #foobar}); this is
     * safe for CJK tags where {@code \w} word boundaries do not apply. Trailing
     * spaces and the blank lines left behind are cleaned up.
     */
    private String stripInlineTags(String markdown, List<String> tags) {
        if (!StringUtils.hasText(markdown) || tags == null || tags.isEmpty()) {
            return markdown;
        }
        List<String> sorted = new ArrayList<>(tags);
        sorted.sort((a, b) -> Integer.compare(length(b), length(a)));
        String s = markdown;
        for (String tag : sorted) {
            if (StringUtils.hasText(tag)) {
                s = s.replace("#" + tag, "");
            }
        }
        // Drop trailing whitespace per line, collapse 3+ blank lines, trim ends.
        String[] lines = s.split("\n", -1);
        StringBuilder out = new StringBuilder(s.length());
        for (String line : lines) {
            out.append(line.replaceAll("[ \\t]+$", "")).append('\n');
        }
        return out.toString().replaceAll("\n{3,}", "\n\n").strip();
    }

    private int length(String s) {
        return s == null ? 0 : s.length();
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
