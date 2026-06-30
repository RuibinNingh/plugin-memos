package run.halo.memos.cache;

public record ImageCacheProperties(
    String baseUrl,
    String accessToken,
    boolean enabled,
    int width,
    int quality,
    long minSourceBytes,
    boolean warmupEnabled,
    int warmupPageSize,
    int warmupMaxPages,
    int maxAgeDays
) {

    static final String DEFAULT_BASE_URL = "http://127.0.0.1:5230";
    static final int DEFAULT_WIDTH = 1600;
    static final int DEFAULT_QUALITY = 82;
    static final long DEFAULT_MIN_SOURCE_BYTES = 300L * 1024L;
    static final int DEFAULT_WARMUP_PAGE_SIZE = 50;
    static final int DEFAULT_WARMUP_MAX_PAGES = 2;
    static final int DEFAULT_MAX_AGE_DAYS = 30;

    public int normalizedWidth() {
        return width <= 0 ? DEFAULT_WIDTH : Math.min(width, 2560);
    }

    public int normalizedQuality() {
        if (quality <= 0) {
            return DEFAULT_QUALITY;
        }
        return Math.max(50, Math.min(quality, 95));
    }

    public int normalizedWarmupPageSize() {
        return warmupPageSize <= 0 ? DEFAULT_WARMUP_PAGE_SIZE : Math.min(warmupPageSize, 100);
    }

    public int normalizedWarmupMaxPages() {
        return warmupMaxPages <= 0 ? DEFAULT_WARMUP_MAX_PAGES : Math.min(warmupMaxPages, 10);
    }

    public int normalizedMaxAgeDays() {
        return maxAgeDays <= 0 ? DEFAULT_MAX_AGE_DAYS : Math.min(maxAgeDays, 365);
    }
}
