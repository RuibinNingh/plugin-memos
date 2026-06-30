package run.halo.memos.cache;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.memos.client.MemosClient;
import run.halo.memos.client.MemosClient.AttachmentDto;

@Service
@RequiredArgsConstructor
public class ImageCacheWarmupService {

    private final MemosClient memosClient;
    private final ImageCacheService imageCacheService;
    private final ImageCacheSettings imageCacheSettings;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile WarmupStatus status = WarmupStatus.idle();

    @Scheduled(initialDelay = 60000L, fixedDelay = 1800000L)
    public void scheduledWarmup() {
        imageCacheSettings.get()
            .filter(ImageCacheProperties::warmupEnabled)
            .flatMap(this::refresh)
            .subscribe();
    }

    public Mono<WarmupStatus> refreshFromConsole() {
        return imageCacheSettings.get().flatMap(this::refresh);
    }

    public WarmupStatus status() {
        return status.withRunning(running.get());
    }

    private Mono<WarmupStatus> refresh(ImageCacheProperties properties) {
        if (!running.compareAndSet(false, true)) {
            return Mono.just(status.withRunning(true));
        }
        WarmupStatus started = status.withStarted(Instant.now());
        status = started.withRunning(true);

        AtomicLong scanned = new AtomicLong();
        AtomicLong created = new AtomicLong();
        AtomicLong hits = new AtomicLong();
        AtomicLong skipped = new AtomicLong();
        AtomicLong failed = new AtomicLong();

        return memosClient.listAll(
                properties.baseUrl(),
                properties.accessToken(),
                properties.normalizedWarmupPageSize(),
                properties.normalizedWarmupMaxPages())
            .flatMapIterable(memo -> memo.getAttachments() == null ? List.of() : memo.getAttachments())
            .filter(ImageUrlSupport::shouldUseCachedImage)
            .concatMap(attachment -> {
                scanned.incrementAndGet();
                return imageCacheService.warmup(ImageUrlSupport.attachmentPath((AttachmentDto) attachment), properties);
            })
            .doOnNext(result -> {
                if (result.created()) {
                    created.incrementAndGet();
                } else if (result.hit()) {
                    hits.incrementAndGet();
                } else if (result.skipped()) {
                    skipped.incrementAndGet();
                } else if (result.failed()) {
                    failed.incrementAndGet();
                }
            })
            .then(imageCacheService.cleanExpired(properties.normalizedMaxAgeDays()))
            .map(deleted -> new WarmupStatus(
                false,
                started.lastStartedAt(),
                Instant.now(),
                scanned.get(),
                created.get(),
                hits.get(),
                skipped.get(),
                failed.get(),
                deleted))
            .doOnNext(next -> status = next)
            .doFinally(signal -> running.set(false))
            .onErrorResume(error -> {
                WarmupStatus failedStatus = new WarmupStatus(
                    false,
                    started.lastStartedAt(),
                    Instant.now(),
                    scanned.get(),
                    created.get(),
                    hits.get(),
                    skipped.get(),
                    failed.incrementAndGet(),
                    0);
                status = failedStatus;
                running.set(false);
                return Mono.just(failedStatus);
            });
    }

    public record WarmupStatus(
        boolean running,
        Instant lastStartedAt,
        Instant lastCompletedAt,
        long scanned,
        long created,
        long hits,
        long skipped,
        long failed,
        long deletedExpired
    ) {
        static WarmupStatus idle() {
            return new WarmupStatus(false, null, null, 0, 0, 0, 0, 0, 0);
        }

        WarmupStatus withRunning(boolean running) {
            return new WarmupStatus(running, lastStartedAt, lastCompletedAt, scanned, created,
                hits, skipped, failed, deletedExpired);
        }

        WarmupStatus withStarted(Instant startedAt) {
            return new WarmupStatus(true, startedAt, lastCompletedAt, scanned, created, hits,
                skipped, failed, deletedExpired);
        }
    }
}
