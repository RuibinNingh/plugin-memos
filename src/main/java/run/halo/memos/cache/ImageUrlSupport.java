package run.halo.memos.cache;

import java.nio.charset.StandardCharsets;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;
import run.halo.memos.client.MemosClient.AttachmentDto;

public final class ImageUrlSupport {

    public static final String PROXY_BASE = "/memos/proxy";
    private ImageUrlSupport() {
    }

    public static String displayUrl(AttachmentDto attachment) {
        if (StringUtils.hasText(attachment.getExternalLink())) {
            return attachment.getExternalLink();
        }
        if (shouldUseCachedImage(attachment)) {
            return optimizedUrl(attachment);
        }
        return originalUrl(attachment);
    }

    public static String optimizedUrl(AttachmentDto attachment) {
        return PROXY_BASE + "/image/" + attachmentPath(attachment);
    }

    public static String originalUrl(AttachmentDto attachment) {
        return PROXY_BASE + "/file/" + attachmentPath(attachment);
    }

    public static boolean shouldUseCachedImage(AttachmentDto attachment) {
        if (attachment == null || StringUtils.hasText(attachment.getExternalLink())) {
            return false;
        }
        String type = attachment.getType() == null ? "" : attachment.getType().toLowerCase();
        if (!("image/jpeg".equals(type) || "image/jpg".equals(type) || "image/png".equals(type))) {
            return false;
        }
        return true;
    }

    public static String attachmentPath(AttachmentDto attachment) {
        String uid = attachment.getName();
        if (uid != null && uid.startsWith("attachments/")) {
            uid = uid.substring("attachments/".length());
        }
        String filename = attachment.getFilename() == null ? "" : attachment.getFilename();
        return "attachments/" + pathSegment(uid) + "/" + pathSegment(filename);
    }

    private static String pathSegment(String value) {
        return UriUtils.encodePathSegment(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
