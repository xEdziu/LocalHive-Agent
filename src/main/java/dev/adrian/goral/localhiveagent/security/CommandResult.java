package dev.adrian.goral.localhiveagent.security;

record CommandResult(
        int exitCode,
        String standardOutput,
        String standardError
) {
}