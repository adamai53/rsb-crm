package dj.redseabunkering.crm.common;

import java.util.List;

/** Page de résultats au format JSON stable (indépendant de Spring Data). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(0, page);
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        int pages = (int) Math.ceil(all.size() / (double) safeSize);
        return new PageResponse<>(List.copyOf(all.subList(from, to)), safePage, safeSize, all.size(), pages);
    }
}
