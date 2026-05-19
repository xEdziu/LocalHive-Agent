package dev.adrian.goral.localhiveagent.system;

public interface SystemInfoProvider {

    MachineSpec collectMachineSpec(int configuredSharedRamMb);
}