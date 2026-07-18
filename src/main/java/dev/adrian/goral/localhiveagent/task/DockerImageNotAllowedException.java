package dev.adrian.goral.localhiveagent.task;

public final class DockerImageNotAllowedException extends DockerWorkloadConfigurationException {

    public DockerImageNotAllowedException(String image) {
        super("Docker image is not allowlisted: " + image);
    }
}
