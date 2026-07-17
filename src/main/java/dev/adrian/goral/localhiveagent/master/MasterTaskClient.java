package dev.adrian.goral.localhiveagent.master;

import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;
import dev.adrian.goral.localhiveagent.master.dto.ExecutionFailureRequest;
import dev.adrian.goral.localhiveagent.master.dto.ExecutionStatusResponse;
import dev.adrian.goral.localhiveagent.master.dto.LeaseRenewalResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class MasterTaskClient {

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String EXECUTION_LEASE_HEADER = "X-EXECUTION-LEASE";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final Duration requestTimeout;

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
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.requestTimeout = requestTimeout;
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
}
