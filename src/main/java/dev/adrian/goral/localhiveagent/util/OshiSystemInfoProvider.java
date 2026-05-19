package dev.adrian.goral.localhiveagent.system;

import dev.adrian.goral.localhiveagent.util.NetworkUtils;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OperatingSystem;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.util.Comparator;
import java.util.List;

public class OshiSystemInfoProvider implements SystemInfoProvider {

    private static final int MAX_HOSTNAME_LENGTH = 63;
    private static final int MAX_OS_TYPE_LENGTH = 50;
    private static final int MAX_GPU_NAME_LENGTH = 120;
    private static final int MAX_RAM_MB = 10_485_760;
    private static final int MAX_CPU_CORES = 1024;

    private final SystemInfo systemInfo;

    public OshiSystemInfoProvider() {
        this.systemInfo = new SystemInfo();
    }

    @Override
    public MachineSpec collectMachineSpec(int configuredSharedRamMb) {
        HardwareAbstractionLayer hardware = systemInfo.getHardware();

        String osUsername = System.getProperty("user.home").substring(
                System.getProperty("user.home").lastIndexOf(FileSystems.getDefault().getSeparator()) + 1
        );

        String hostname = sanitizeText(resolveHostname(), MAX_HOSTNAME_LENGTH, osUsername);
        String ipAddress = NetworkUtils.findPrimaryIpv4Address();
        String osType = sanitizeText(resolveOsType(systemInfo.getOperatingSystem()), MAX_OS_TYPE_LENGTH, "Unknown OS");

        int totalRamMb = resolveTotalRamMb(hardware.getMemory());
        int sharedRamMb = clampSharedRamMb(configuredSharedRamMb, totalRamMb);

        int cpuCores = resolveCpuCores(hardware.getProcessor());
        String gpuName = sanitizeOptionalText(resolveGpuName(hardware.getGraphicsCards()), MAX_GPU_NAME_LENGTH);

        return new MachineSpec(
                hostname,
                ipAddress,
                osType,
                totalRamMb,
                sharedRamMb,
                cpuCores,
                gpuName
        );
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return "unknown-host";
        }
    }

    private static String resolveOsType(OperatingSystem operatingSystem) {
        String family = operatingSystem.getFamily();

        if (family == null || family.isBlank()) {
            return operatingSystem.toString();
        }

        if (!family.equalsIgnoreCase("Windows")) {
            return family.trim();
        }

        String buildNumber = operatingSystem.getVersionInfo().getBuildNumber();
        int build = parseBuildNumber(buildNumber);

        if (build >= 22000) {
            return "Windows 11";
        }

        if (build >= 10240) {
            return "Windows 10";
        }

        return "Windows";
    }

    private static int parseBuildNumber(String buildNumber) {
        if (buildNumber == null || buildNumber.isBlank()) {
            return -1;
        }

        try {
            return Integer.parseInt(buildNumber.trim());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static int resolveTotalRamMb(GlobalMemory memory) {
        long totalMemoryMb = memory.getTotal() / 1024 / 1024;

        if (totalMemoryMb < 1) {
            return 1;
        }

        if (totalMemoryMb > MAX_RAM_MB) {
            return MAX_RAM_MB;
        }

        return Math.toIntExact(totalMemoryMb);
    }

    private static int clampSharedRamMb(int configuredSharedRamMb, int totalRamMb) {
        if (configuredSharedRamMb < 0) {
            return 0;
        }

        return Math.min(configuredSharedRamMb, totalRamMb);
    }

    private static int resolveCpuCores(CentralProcessor processor) {
        int logicalProcessorCount = processor.getLogicalProcessorCount();

        if (logicalProcessorCount < 1) {
            return 1;
        }

        return Math.min(logicalProcessorCount, MAX_CPU_CORES);
    }

    private static String resolveGpuName(List<GraphicsCard> graphicsCards) {
        return graphicsCards.stream()
                .map(GraphicsCard::getName)
                .filter(name -> name != null && !name.isBlank())
                .max(Comparator.comparingInt(String::length))
                .orElse("");
    }

    private static String sanitizeText(String value, int maxLength, String fallbackValue) {
        String normalizedValue = value == null || value.isBlank()
                ? fallbackValue
                : value.trim();

        String safeValue = normalizedValue.replaceAll("[\\r\\n\\t]", " ");

        if (safeValue.length() <= maxLength) {
            return safeValue;
        }

        return safeValue.substring(0, maxLength);
    }

    private static String sanitizeOptionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return sanitizeText(value, maxLength, "");
    }
}