package dev.adrian.goral.localhiveagent.task;

import dev.adrian.goral.localhiveagent.config.DockerPolicy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DockerWorkloadConfigParser {

    private static final int MIN_TIMEOUT_SECONDS = 1;
    private static final int MAX_TIMEOUT_SECONDS = 300;
    private static final int MIN_MEMORY_MB = 16;
    private static final int MIN_CPU_CORES = 1;

    public DockerWorkloadConfig parse(Map<String, Object> configuration) {
        return parse(configuration, DockerPolicy.defaultPolicy());
    }

    public DockerWorkloadConfig parse(Map<String, Object> configuration, DockerPolicy policy) {
        DockerPolicy dockerPolicy = policy == null ? DockerPolicy.defaultPolicy() : policy;
        if (configuration == null) {
            throw invalid("configuration is required.");
        }

        String image = requireImage(configuration.get("image"), dockerPolicy);
        List<String> command = requireCommand(configuration.get("command"));
        int timeoutSeconds = requireRange(
                configuration.get("timeoutSeconds"),
                "timeoutSeconds",
                MIN_TIMEOUT_SECONDS,
                MAX_TIMEOUT_SECONDS
        );
        Map<?, ?> resources = requireObject(configuration.get("resources"), "resources");
        int memoryMb = requireRange(
                resources.get("memoryMb"),
                "resources.memoryMb",
                MIN_MEMORY_MB,
                dockerPolicy.maxMemoryMb()
        );
        int cpuCores = requireRange(
                resources.get("cpuCores"),
                "resources.cpuCores",
                MIN_CPU_CORES,
                dockerPolicy.maxCpuCores()
        );
        Map<?, ?> gpu = requireObject(configuration.get("gpu"), "gpu");
        boolean gpuRequired = requireBoolean(gpu.get("required"), "gpu.required");
        if (gpuRequired && !dockerPolicy.allowGpu()) {
            throw invalid("gpu.required must be false by Docker policy.");
        }
        if (gpuRequired) {
            throw invalid("GPU Docker execution is not implemented yet.");
        }

        DockerWorkspaceConfig workspace = parseWorkspace(configuration.get("workspace"));

        return new DockerWorkloadConfig(image, command, timeoutSeconds, memoryMb, cpuCores, false, workspace);
    }

    private static String requireImage(Object value, DockerPolicy policy) {
        if (!(value instanceof String image) || image.isBlank()) {
            throw invalid("image is required.");
        }

        String normalized = image.trim();
        if (!policy.allowedImages().contains(normalized)) {
            throw new DockerImageNotAllowedException(normalized);
        }
        return normalized;
    }

    private static List<String> requireCommand(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw invalid("command must be a non-empty array.");
        }

        return list.stream()
                .map(DockerWorkloadConfigParser::requireCommandElement)
                .toList();
    }

    private static String requireCommandElement(Object value) {
        if (!(value instanceof String element) || element.isBlank()) {
            throw invalid("command elements must not be blank.");
        }
        return element.trim();
    }

    private static Map<?, ?> requireObject(Object value, String fieldName) {
        if (!(value instanceof Map<?, ?> map)) {
            throw invalid(fieldName + " is required.");
        }
        return map;
    }

    private static int requireRange(Object value, String fieldName, int min, int max) {
        int number = requireInteger(value, fieldName);
        if (number < min || number > max) {
            throw invalid(fieldName + " must be between " + min + " and " + max + ".");
        }
        return number;
    }

    private static int requireInteger(Object value, String fieldName) {
        if (!(value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long)) {
            throw invalid(fieldName + " is required.");
        }

        long longValue = ((Number) value).longValue();
        if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
            throw invalid(fieldName + " is outside supported integer range.");
        }

        return (int) longValue;
    }

    private static boolean requireBoolean(Object value, String fieldName) {
        if (!(value instanceof Boolean bool)) {
            throw invalid(fieldName + " is required.");
        }
        return bool;
    }

    private static DockerWorkspaceConfig parseWorkspace(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> workspace)) {
            throw invalid("workspace must be an object.");
        }

        return new DockerWorkspaceConfig(
                requireUuid(workspace.get("artifactId"), "workspace.artifactId"),
                requireExactString(workspace.get("mountPath"), "workspace.mountPath", "/workspace"),
                requireTrue(workspace.get("readOnly"), "workspace.readOnly")
        );
    }

    private static UUID requireUuid(Object value, String fieldName) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalid(fieldName + " is required.");
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException exception) {
            throw invalid(fieldName + " must be a valid UUID.");
        }
    }

    private static String requireExactString(Object value, String fieldName, String expected) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalid(fieldName + " is required.");
        }
        String normalized = text.trim();
        if (!expected.equals(normalized)) {
            throw invalid(fieldName + " must be " + expected + ".");
        }
        return normalized;
    }

    private static boolean requireTrue(Object value, String fieldName) {
        if (!(value instanceof Boolean bool)) {
            throw invalid(fieldName + " is required.");
        }
        if (!bool) {
            throw invalid(fieldName + " must be true.");
        }
        return true;
    }

    private static DockerWorkloadConfigurationException invalid(String message) {
        return new DockerWorkloadConfigurationException(message);
    }
}
