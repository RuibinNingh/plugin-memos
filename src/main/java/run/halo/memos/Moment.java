package run.halo.memos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * Transient Moment type carrying only the GVK ({@code moment.halo.run/Moment})
 * and a {@code metadata.name} so Halo's comment system can attach comments via
 * {@link MomentCommentSubject}. It is <strong>not</strong> registered in the
 * SchemeManager and never persisted — instances are built live from memos on
 * each request.
 */
@GVK(group = "moment.halo.run", version = "v1alpha1", kind = "Moment",
    plural = "moments", singular = "moment")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Moment extends AbstractExtension {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private MomentSpec spec;

    @Data
    @Schema(name = "MomentSpec")
    public static class MomentSpec {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private MomentContent content;

        @Schema(description = "Release timestamp")
        private Instant releaseTime;

        @Schema(defaultValue = "PUBLIC")
        private MomentVisible visible;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String owner;

        @Schema(description = "Tags of the moment")
        private Set<String> tags;

        @Schema(defaultValue = "false")
        private Boolean approved;
    }

    @Data
    @Schema(name = "MomentContent")
    public static class MomentContent {
        private String raw;
        private String html;
        private List<MomentMedia> medium;
    }

    @Data
    @Schema(name = "MomentMedia")
    public static class MomentMedia {
        private MomentMediaType type;
        private String url;
        private String originType;
    }

    public enum MomentMediaType {
        PHOTO,
        VIDEO,
        POST,
        AUDIO,
    }

    public enum MomentVisible {
        PUBLIC,
        PRIVATE
    }
}
