package dev.adrian.goral.localhiveagent.master;

import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;
import dev.adrian.goral.localhiveagent.master.dto.ExecutionFailureRequest;
import dev.adrian.goral.localhiveagent.master.dto.ExecutionStatusResponse;
import dev.adrian.goral.localhiveagent.master.dto.LeaseRenewalResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public class MasterTaskClient {

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String EXECUTION_LEASE_HEADER = "X-EXECUTION-LEASE";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern WINDOWS_DRIVE_PATH = Pattern.compile("^[A-Za-z]:.*");
    public static final long MAX_ARTIFACT_DOWNLOAD_BYTES = 50L * 1024L * 1024L;

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final Duration requestTimeout;
    private final long maxArtifactDownloadBytes;

    public MasterTaskClient() {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                JsonMapper.builder().build(),
                DEFAULT_REQUEST_TIMEOUT
        );
    }

    public MasterTaskClient(HttpClient httpClient, JsonMapper jsonMapper, Duration requestTimeout) {
        this(httpClient, jsonMapper, requestTimeout, MAX_ARTIFACT_DOWNLOAD_BYTES);
    }

    MasterTaskClient(HttpClient httpClient,
                     JsonMapper jsonMapper,
                     Duration requestTimeout,
                     long maxArtifactDownloadBytes) {
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.requestTimeout = requestTimeout;
        this.maxArtifactDownloadBytes = maxArtifactDownloadBytes;
    }

    public Optional<ClaimedExecutionPayload> claimNext(String masterBaseUrl, UUID workerId, String apiKey) {
        validateWorkerIdentity(workerId, apiKey);

        HttpRequest request = baseRequest(masterBaseUrl, "/api/workers/" + workerId + "/assigned-executions/claim-next")
                .header(API_KEY_HEADER, apiKey)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() == 204) {
            return Optional.empty();
        }

        ensureSuccessfulResponse(response, 200, "Claim next execution");

        return Optional.of(readJson(response.body(), ClaimedExecutionPayload.class));
    }

    public void reportRunning(String masterBaseUrl,
                              UUID workerId,
                              UUID executionId,
                              String apiKey,
                              String leaseToken) {
        sendExecutionStatus(masterBaseUrl, workerId, executionId, apiKey, leaseToken, "running", "Report running");
    }

    public void reportSucceeded(String masterBaseUrl,
                                UUID workerId,
                                UUID executionId,
                                String apiKey,
                                String leaseToken) {
        sendExecutionStatus(masterBaseUrl, workerId, executionId, apiKey, leaseToken, "succeeded", "Report succeeded");
    }

    public void reportFailed(String masterBaseUrl,
                             UUID workerId,
                             UUID executionId,
                             String apiKey,
                             String leaseToken,
                             String failureCode,
                             String failureMessage) {
        validateExecutionIdentity(workerId, executionId, apiKey, leaseToken);
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("failureCode cannot be blank.");
        }

        String requestBody = writeJson(new ExecutionFailureRequest(failureCode, failureMessage));
        HttpRequest request = baseRequest(masterBaseUrl, executionPath(workerId, executionId, "failed"))
                .header(API_KEY_HEADER, apiKey)
                .header(EXECUTION_LEASE_HEADER, leaseToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(request);
        ensureSuccessfulResponse(response, 200, "Report failed");
        readStatusResponseIfPresent(response);
    }

    public LocalDateTime renewLease(String masterBaseUrl,
                                    UUID workerId,
                                    UUID executionId,
                                    String apiKey,
                                    String leaseToken) {
        validateExecutionIdentity(workerId, executionId, apiKey, leaseToken);

        HttpRequest request = baseRequest(masterBaseUrl, executionPath(workerId, executionId, "lease/renew"))
                .header(API_KEY_HEADER, apiKey)
                .header(EXECUTION_LEASE_HEADER, leaseToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = send(request);
        ensureSuccessfulResponse(response, 200, "Renew execution lease");

        return readJson(response.body(), LeaseRenewalResponse.class).leaseExpiresAtDateTime();
    }

    public void downloadExecutionArtifact(String masterBaseUrl,
                                          UUID workerId,
                                          UUID executionId,
                                          UUID artifactId,
                                          String apiKey,
                                          String leaseToken,
                                          Path targetFile) {
        validateExecutionIdentity(workerId, executionId, apiKey, leaseToken);
        if (artifactId == null) {
            throw new IllegalArgumentException("Artifact ID cannot be null.");
        }
        if (targetFile == null) {
            throw new IllegalArgumentException("Target file cannot be null.");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(buildUri(masterBaseUrl, executionPath(workerId, executionId, "artifacts/" + artifactId + "/download")))
                .timeout(requestTimeout)
                .header("Accept", "application/octet-stream")
                .header(API_KEY_HEADER, apiKey)
                .header(EXECUTION_LEASE_HEADER, leaseToken)
                .GET()
                .build();

        HttpResponse<InputStream> response = sendDownload(request);
        ensureDownloadSuccessful(response);
        writeDownload(response.body(), targetFile);
    }

    public void uploadExecutionOutputArtifact(String masterBaseUrl,
                                              UUID workerId,
                                              UUID executionId,
                                              String apiKey,
                                              String leaseToken,
                                              Path file,
                                              String relativePath) {
        validateExecutionIdentity(workerId, executionId, apiKey, leaseToken);
        Path source = requireRegularFile(file);
        String uploadRelativePath = requireUploadRelativePath(relativePath);
        String boundary = "LocalHiveBoundary" + UUID.randomUUID().toString().replace("-", "");

        HttpRequest request = baseRequest(masterBaseUrl, executionPath(workerId, executionId, "artifacts/output"))
                .header(API_KEY_HEADER, apiKey)
                .header(EXECUTION_LEASE_HEADER, leaseToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(multipartUploadBody(source, uploadRelativePath, boundary))
                .build();

        HttpResponse<Void> response = sendDiscarding(request);
        ensureUploadSuccessful(response);
    }

    private void sendExecutionStatus(String masterBaseUrl,
                                     UUID workerId,
                                     UUID executionId,
                                     String apiKey,
                                     String leaseToken,
                                     String statusPath,
                                     String operation) {
        validateExecutionIdentity(workerId, executionId, apiKey, leaseToken);

        HttpRequest request = baseRequest(masterBaseUrl, executionPath(workerId, executionId, statusPath))
                .header(API_KEY_HEADER, apiKey)
                .header(EXECUTION_LEASE_HEADER, leaseToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = send(request);
        ensureSuccessfulResponse(response, 200, operation);
        readStatusResponseIfPresent(response);
    }

    private HttpRequest.Builder baseRequest(String masterBaseUrl, String path) {
        return HttpRequest.newBuilder()
                .uri(buildUri(masterBaseUrl, path))
                .timeout(requestTimeout)
                .header("Accept", "application/json");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MasterClientException(
                    "HTTP request was interrupted.",
                    "HTTP request was interrupted.",
                    exception
            );
        } catch (IOException exception) {
            String userMessage = MasterClientErrorMapper.mapConnectionError("Master task communication", exception);

            throw new MasterClientException(
                    "HTTP request failed.",
                    userMessage,
                    exception
            );
        }
    }

    private HttpResponse<InputStream> sendDownload(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MasterClientException(
                    "Artifact download request was interrupted.",
                    "Artifact download request was interrupted.",
                    exception
            );
        } catch (IOException exception) {
            String userMessage = MasterClientErrorMapper.mapConnectionError("Download execution artifact", exception);

            throw new MasterClientException(
                    "Artifact download request failed.",
                    userMessage,
                    exception
            );
        }
    }

    private HttpResponse<Void> sendDiscarding(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MasterClientException(
                    "Output artifact upload request was interrupted.",
                    "Output artifact upload request was interrupted.",
                    exception
            );
        } catch (IOException exception) {
            String userMessage = MasterClientErrorMapper.mapConnectionError("Upload execution output artifact", exception);

            throw new MasterClientException(
                    "Output artifact upload request failed.",
                    userMessage,
                    exception
            );
        }
    }

    private void ensureDownloadSuccessful(HttpResponse<InputStream> response) {
        if (response.statusCode() == 200) {
            return;
        }

        closeQuietly(response.body());
        throw new MasterClientException(
                "Download execution artifact failed.",
                "Download execution artifact: Master rejected artifact download.",
                response.statusCode(),
            ""
        );
    }

    private void ensureUploadSuccessful(HttpResponse<Void> response) {
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            return;
        }

        throw new MasterClientException(
                "Upload execution output artifact failed.",
                "Upload execution output artifact: Master rejected output artifact upload.",
                response.statusCode(),
                ""
        );
    }

    private void writeDownload(InputStream input, Path targetFile) {
        Path target = targetFile.toAbsolutePath().normalize();
        try (InputStream body = input) {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(
                    target,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            )) {
                copyWithLimit(body, output);
            }
        } catch (IOException exception) {
            deletePartialDownload(target);
            throw new MasterClientException(
                    "Artifact download failed.",
                    "Download execution artifact: Failed to write workspace package.",
                    exception
            );
        } catch (MasterClientException exception) {
            deletePartialDownload(target);
            throw exception;
        }
    }

    private void copyWithLimit(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        long totalBytes = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            totalBytes += read;
            if (totalBytes > maxArtifactDownloadBytes) {
                throw new MasterClientException(
                        "Artifact download exceeded maximum size.",
                        "Download execution artifact: Workspace package exceeds 50 MB.",
                        -1,
                        ""
                );
            }
            output.write(buffer, 0, read);
        }
    }

    private static void deletePartialDownload(Path targetFile) {
        try {
            Files.deleteIfExists(targetFile);
        } catch (IOException ignored) {
            // Best effort cleanup; the original download failure is more important.
        }
    }

    private static void closeQuietly(InputStream input) {
        if (input == null) {
            return;
        }
        try {
            input.close();
        } catch (IOException ignored) {
            // Nothing useful to report when closing an unused error body.
        }
    }

    private static HttpRequest.BodyPublisher multipartUploadBody(Path file,
                                                                 String relativePath,
                                                                 String boundary) {
        byte[] fileHeader = (
                "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\""
                        + multipartFilename(file)
                        + "\"\r\n"
                        + "Content-Type: application/octet-stream\r\n"
                        + "\r\n"
        ).getBytes(StandardCharsets.UTF_8);
        byte[] metadataAndClosing = (
                "\r\n"
                        + "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"relativePath\"\r\n"
                        + "\r\n"
                        + relativePath
                        + "\r\n"
                        + "--" + boundary + "--\r\n"
        ).getBytes(StandardCharsets.UTF_8);

        return HttpRequest.BodyPublishers.ofInputStream(() -> {
            try {
                return new SequenceInputStream(Collections.enumeration(List.of(
                        new ByteArrayInputStream(fileHeader),
                        Files.newInputStream(file, StandardOpenOption.READ),
                        new ByteArrayInputStream(metadataAndClosing)
                )));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    private static String multipartFilename(Path file) {
        Path filename = file.getFileName();
        String value = filename == null ? "artifact" : filename.toString().trim();
        if (value.isBlank()) {
            return "artifact";
        }

        String sanitized = value
                .replace("\0", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace("\\", "_")
                .replace("/", "_")
                .replace("\"", "'");
        return sanitized.isBlank() ? "artifact" : sanitized;
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new MasterClientException(
                    "Failed to serialize JSON request.",
                    "Agent failed to prepare the task request payload.",
                    exception
            );
        }
    }

    private <T> T readJson(String responseBody, Class<T> responseType) {
        try {
            return jsonMapper.readValue(responseBody, responseType);
        } catch (RuntimeException exception) {
            throw new MasterClientException(
                    "Failed to deserialize JSON response.",
                    "Master returned a task response that the Agent could not understand.",
                    exception
            );
        }
    }

    private void readStatusResponseIfPresent(HttpResponse<String> response) {
        if (response.body() == null || response.body().isBlank()) {
            return;
        }

        readJson(response.body(), ExecutionStatusResponse.class);
    }

    private void ensureSuccessfulResponse(HttpResponse<String> response, int expectedStatusCode, String operation) {
        if (response.statusCode() == expectedStatusCode) {
            return;
        }

        throwHttpError(response, operation);
    }

    private void throwHttpError(HttpResponse<String> response, String operation) {
        String userMessage = MasterClientErrorMapper.mapHttpError(
                operation,
                response.statusCode(),
                response.body(),
                jsonMapper
        );

        throw new MasterClientException(
                operation + " failed.",
                userMessage,
                response.statusCode(),
                response.body()
        );
    }

    private static String executionPath(UUID workerId, UUID executionId, String action) {
        return "/api/workers/" + workerId + "/executions/" + executionId + "/" + action;
    }

    private static URI buildUri(String masterBaseUrl, String path) {
        if (masterBaseUrl == null || masterBaseUrl.isBlank()) {
            throw new IllegalArgumentException("Master base URL cannot be blank.");
        }

        String normalizedBaseUrl = masterBaseUrl.trim();
        if (!normalizedBaseUrl.contains("://")) {
            normalizedBaseUrl = "http://" + normalizedBaseUrl;
        }

        normalizedBaseUrl = removeTrailingSlashes(normalizedBaseUrl);
        String normalizedPath = path.startsWith("/") ? path : "/" + path;

        return URI.create(normalizedBaseUrl + normalizedPath);
    }

    private static String removeTrailingSlashes(String value) {
        String result = value;

        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }

    private static void validateWorkerIdentity(UUID workerId, String apiKey) {
        if (workerId == null) {
            throw new IllegalArgumentException("Worker ID cannot be null.");
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key cannot be blank.");
        }
    }

    private static void validateExecutionIdentity(UUID workerId,
                                                  UUID executionId,
                                                  String apiKey,
                                                  String leaseToken) {
        validateWorkerIdentity(workerId, apiKey);

        if (executionId == null) {
            throw new IllegalArgumentException("Execution ID cannot be null.");
        }

        if (leaseToken == null || leaseToken.isBlank()) {
            throw new IllegalArgumentException("Execution lease token cannot be blank.");
        }
    }

    private static Path requireRegularFile(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("Output artifact file cannot be null.");
        }

        Path source = file.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Output artifact file must be a regular file.");
        }
        return source;
    }

    private static String requireUploadRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Output artifact relative path cannot be blank.");
        }

        String normalized = relativePath.trim().replace('\\', '/');
        if (normalized.length() > 1024) {
            throw new IllegalArgumentException("Output artifact relative path must be at most 1024 characters.");
        }
        if (normalized.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Output artifact relative path cannot contain a null byte.");
        }
        if (normalized.startsWith("/") || WINDOWS_DRIVE_PATH.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Output artifact relative path must be relative.");
        }

        for (String segment : normalized.split("/", -1)) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException("Output artifact relative path cannot contain blank segments.");
            }
            if (".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Output artifact relative path cannot contain traversal segments.");
            }
        }

        return normalized;
    }
}
