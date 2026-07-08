package com.codex.refactor.history;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class HistoryAnalysis {
    private final boolean enabled;
    private final String status;
    private final Path repositoryRoot;
    private final int commitsScanned;
    private final int shotgunSurgeryClusterCount;
    private final Map<String, List<ShotgunSurgeryHistoryEvidence>> shotgunSurgeryByPath;
    private final List<String> warnings;

    public HistoryAnalysis(
            boolean enabled,
            String status,
            Path repositoryRoot,
            int commitsScanned,
            int shotgunSurgeryClusterCount,
            Map<String, List<ShotgunSurgeryHistoryEvidence>> shotgunSurgeryByPath,
            List<String> warnings
    ) {
        this.enabled = enabled;
        this.status = status;
        this.repositoryRoot = repositoryRoot;
        this.commitsScanned = commitsScanned;
        this.shotgunSurgeryClusterCount = shotgunSurgeryClusterCount;
        this.shotgunSurgeryByPath = Map.copyOf(shotgunSurgeryByPath);
        this.warnings = List.copyOf(warnings);
    }

    public static HistoryAnalysis off() {
        return new HistoryAnalysis(false, "off", null, 0, 0, Map.of(), List.of());
    }

    public static HistoryAnalysis skipped(String warning) {
        return new HistoryAnalysis(true, "skipped", null, 0, 0, Map.of(), List.of(warning));
    }

    public boolean enabled() {
        return enabled;
    }

    public String status() {
        return status;
    }

    public List<ShotgunSurgeryHistoryEvidence> shotgunSurgeryFor(Path path) {
        Optional<String> relativePath = relativePath(path);
        if (relativePath.isEmpty()) {
            return List.of();
        }
        return shotgunSurgeryByPath.getOrDefault(relativePath.get(), List.of());
    }

    public Optional<String> relativePath(Path path) {
        if (repositoryRoot == null) {
            return Optional.empty();
        }
        Path absolute = path.toAbsolutePath().normalize();
        if (!absolute.startsWith(repositoryRoot)) {
            return Optional.empty();
        }
        return Optional.of(normalizePath(repositoryRoot.relativize(absolute).toString()));
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("enabled", enabled);
        json.put("status", status);
        json.put("repository_root", repositoryRoot == null ? null : repositoryRoot.toString());
        json.put("commits_scanned", commitsScanned);
        json.put("shotgun_surgery_clusters", shotgunSurgeryClusterCount);
        json.put("warnings", warnings);
        return json;
    }

    static String normalizePath(String path) {
        return path.replace('\\', '/');
    }
}
