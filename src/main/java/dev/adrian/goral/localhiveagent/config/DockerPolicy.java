package dev.adrian.goral.localhiveagent.config;

import java.util.List;
import java.util.Objects;

public record DockerPolicy(
        Boolean enabled,
        List<String> allowedImages,
        Integer maxMemoryMb,
        Integer maxCpuCores,
        Boolean allowGpu
) {

    public static final boolean DEFAULT_ENABLED = true;
    public static final String DEFAULT_ALLOWED_IMAGE = "alpine:3.20";
    public static final int MIN_MAX_MEMORY_MB = 16;
    public static final int DEFAULT_MAX_MEMORY_MB = 4096;
    public static final int MIN_MAX_CPU_CORES = 1;
    public static final int DEFAULT_MAX_CPU_CORES = 8;
    public static final boolean DEFAULT_ALLOW_GPU = false;

    public DockerPolicy {
        enabled = enabled == null ? DEFAULT_ENABLED : enabled;
        allowedImages = allowedImages == null ? defaultAllowedImages() : normalizeAllowedImages(allowedImages);
        maxMemoryMb = maxMemoryMb == null ? DEFAULT_MAX_MEMORY_MB : maxMemoryMb;
        maxCpuCores = maxCpuCores == null ? DEFAULT_MAX_CPU_CORES : maxCpuCores;
        allowGpu = allowGpu == null ? DEFAULT_ALLOW_GPU : allowGpu;

        if (maxMemoryMb < MIN_MAX_MEMORY_MB) {
            throw new IllegalArgumentException("Docker maxMemoryMb must be at least " + MIN_MAX_MEMORY_MB + ".");
        }
        if (maxCpuCores < MIN_MAX_CPU_CORES) {
            throw new IllegalArgumentException("Docker maxCpuCores must be at least " + MIN_MAX_CPU_CORES + ".");
        }
    }

    public static DockerPolicy defaultPolicy() {
        return new DockerPolicy(
                DEFAULT_ENABLED,
                defaultAllowedImages(),
                DEFAULT_MAX_MEMORY_MB,
                DEFAULT_MAX_CPU_CORES,
                DEFAULT_ALLOW_GPU
        );
    }

    private static List<String> defaultAllowedImages() {
        return List.of(DEFAULT_ALLOWED_IMAGE);
    }

    private static List<String> normalizeAllowedImages(List<String> allowedImages) {
        Objects.requireNonNull(allowedImages, "allowedImages is required");
        return allowedImages.stream()
                .map(DockerPolicy::normalizeImage)
                .toList();
    }

    private static String normalizeImage(String image) {
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("Docker allowedImages elements must not be blank.");
        }
        return image.trim();
    }
}
