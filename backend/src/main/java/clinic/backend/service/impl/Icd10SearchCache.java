package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import clinic.backend.repository.Icd10Repository;
import clinic.backend.utils.VietnameseText;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The selectable ICD-10 codes kept in memory, so a search reads no database. The list changes only when an
 * operator runs the icd10-import profile in another process, so it is reloaded after a TTL (default 1 hour);
 * restart the backend to see a new list at once. Each backend instance keeps its own copy.
 */
@Slf4j
@Component
public class Icd10SearchCache {

    static final int NO_MATCH = Integer.MAX_VALUE;
    private static final int RANKS = 5;
    /** When loading failed or found no codes (before the first import), try again soon instead of after the TTL. */
    private static final Duration RETRY_AFTER = Duration.ofMinutes(1);

    /** One selectable code with its search keys folded once at load time. */
    record Entry(String code, String name, String foldedCode, String compactCode, String foldedName,
                 LocalDate effectiveFrom, LocalDate effectiveUntil) {

        boolean usableOn(LocalDate day) {
            return !effectiveFrom.isAfter(day) && (effectiveUntil == null || !effectiveUntil.isBefore(day));
        }
    }

    /** Replaced as a whole, so a reader never sees a half-loaded list. */
    private record Snapshot(List<Entry> entries, Instant expiresAt) {
    }

    private final Icd10Repository repository;
    private final Clock clock;
    private final Duration ttl;
    private final boolean preload;
    private volatile Snapshot snapshot;

    public Icd10SearchCache(
            Icd10Repository repository,
            Clock clock,
            @Value("${clinic.icd10.search-cache.ttl:1h}") Duration ttl,
            @Value("${clinic.icd10.search-cache.preload:true}") boolean preload) {
        this.repository = repository;
        this.clock = clock;
        this.ttl = ttl;
        this.preload = preload;
    }

    /** Loads the list at startup so the first search is fast; a failure only delays loading to the first search. */
    @EventListener(ApplicationReadyEvent.class)
    public void preloadOnStartup() {
        if (!preload) return;
        try {
            entries();
        } catch (RuntimeException ex) {
            log.warn("ICD-10 search cache was not preloaded; the first search will load it", ex);
        }
    }

    /**
     * Codes usable on {@code day} that match {@code keyword} without accents or case, best first: the exact code,
     * a code starting with the keyword (with or without its dot), a name starting with it, a word of the name
     * starting with it, then a name containing it; ties by code.
     */
    public List<Entry> search(String keyword, LocalDate day, int limit) {
        String query = VietnameseText.fold(keyword);
        if (query == null || query.isEmpty() || limit <= 0) return List.of();
        String compactQuery = query.replace(".", "");
        // Entries are sorted by code, so one pass filling a bucket per rank keeps the order without sorting.
        List<List<Entry>> buckets = new ArrayList<>(RANKS);
        for (int i = 0; i < RANKS; i++) buckets.add(new ArrayList<>());
        for (Entry entry : entries()) {
            int rank = rank(entry, query, compactQuery);
            if (rank == NO_MATCH || buckets.get(rank).size() >= limit || !entry.usableOn(day)) continue;
            buckets.get(rank).add(entry);
        }
        List<Entry> result = new ArrayList<>(limit);
        for (List<Entry> bucket : buckets) {
            for (Entry entry : bucket) {
                if (result.size() == limit) return List.copyOf(result);
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Whether {@code text} names one ICD-10 disease usable on {@code day}: its code ("J02.9", "j029"), its name
     * ("Viêm họng cấp, không đặc hiệu") or both ("J02.9 Viêm họng cấp, không đặc hiệu", "J02.9 - ..."), without
     * accents or case. Several diseases are separated by ";" and each one must match.
     */
    public boolean namesIcd10Diseases(String text, LocalDate day) {
        if (text == null || text.isBlank()) return false;
        List<String> parts = new ArrayList<>();
        for (String part : text.split(";")) {
            String folded = VietnameseText.fold(part);
            if (!folded.isEmpty()) parts.add(folded);
        }
        if (parts.isEmpty()) return false;
        List<Entry> usable = entries().stream().filter(entry -> entry.usableOn(day)).toList();
        return parts.stream().allMatch(part -> usable.stream().anyMatch(entry -> names(entry, part)));
    }

    private static boolean names(Entry entry, String part) {
        if (part.equals(entry.foldedName()) || part.equals(entry.foldedCode())
                || part.replace(".", "").equals(entry.compactCode())) {
            return true;
        }
        if (!part.startsWith(entry.foldedCode())) return false;
        String rest = part.substring(entry.foldedCode().length()).strip();
        if (rest.startsWith("-") || rest.startsWith(":")) rest = rest.substring(1).strip();
        return rest.equals(entry.foldedName());
    }

    static int rank(Entry entry, String query, String compactQuery) {
        boolean codeQuery = !compactQuery.isEmpty();
        if (entry.foldedCode().equals(query) || (codeQuery && entry.compactCode().equals(compactQuery))) return 0;
        if (entry.foldedCode().startsWith(query) || (codeQuery && entry.compactCode().startsWith(compactQuery))) {
            return 1;
        }
        if (entry.foldedName().startsWith(query)) return 2;
        if (entry.foldedName().contains(" " + query)) return 3;
        if (entry.foldedName().contains(query)) return 4;
        return NO_MATCH;
    }

    List<Entry> entries() {
        Snapshot current = snapshot;
        if (current == null || isExpired(current)) {
            current = reload();
        }
        return current.entries();
    }

    /** One caller reloads; others that waited reuse its result. */
    private synchronized Snapshot reload() {
        Snapshot current = snapshot;
        if (current != null && !isExpired(current)) return current;
        Instant now = clock.instant();
        try {
            List<Entry> entries = repository.findSearchable().stream().map(Icd10SearchCache::toEntry)
                    .sorted(Comparator.comparing(Entry::code)).toList();
            Snapshot fresh = new Snapshot(entries, now.plus(entries.isEmpty() ? RETRY_AFTER : ttl));
            snapshot = fresh;
            log.info("Loaded ICD-10 search cache [codes={}]", entries.size());
            return fresh;
        } catch (RuntimeException ex) {
            if (current == null) throw ex;
            // Keep serving the previous list rather than failing every search while the database is unreachable.
            log.warn("Could not reload the ICD-10 search cache; keeping the previous list", ex);
            Snapshot stale = new Snapshot(current.entries(), now.plus(RETRY_AFTER));
            snapshot = stale;
            return stale;
        }
    }

    private boolean isExpired(Snapshot current) {
        return !clock.instant().isBefore(current.expiresAt());
    }

    private static Entry toEntry(Icd10Repository.SearchableCode code) {
        String foldedCode = code.getCode().toLowerCase(Locale.ROOT);
        return new Entry(code.getCode(), code.getName(), foldedCode, foldedCode.replace(".", ""),
                VietnameseText.fold(code.getName()), code.getEffectiveFrom(), code.getEffectiveUntil());
    }
}
