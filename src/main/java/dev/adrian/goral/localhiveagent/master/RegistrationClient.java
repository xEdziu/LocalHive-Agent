package dev.adrian.goral.localhiveagent.master;

import dev.adrian.goral.localhiveagent.master.dto.*;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

public class RegistrationClient {

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final Duration requestTimeout;

    public RegistrationClient() {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                JsonMapper.builder().build(),
                DEFAULT_REQUEST_TIMEOUT
        );
    }

    public RegistrationClient(HttpClient httpClient, JsonMapper jsonMapper, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.requestTimeout = requestTimeout;
    }

    public WorkerRegistrationResponse register(String masterBaseUrl, WorkerRegistrationRequest request) {
        String requestBody = writeJson(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(buildUri(masterBaseUrl, "/api/workers/register"))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(httpRequest);

        if (response.statusCode() != 201) {
            throw new MasterClientException(
                    "Worker registration failed.",
                    response.statusCode(),
                    response.body()
            );
        }

        WorkerRegistrationResponse registrationResponse = readJson(response.body(), WorkerRegistrationResponse.class);

        if (registrationResponse.workerId() == null) {
            throw new MasterClientException("Registration response does not contain workerId.");
        }

        return registrationResponse;
    }

    public HeartbeatResponse sendHeartbeat(String masterBaseUrl,
                                           UUID workerId, String apiKey, HeartbeatRequest request) {
        validateWorkerIdentity(workerId, apiKey);

        String requestBody = writeJson(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(buildUri(masterBaseUrl, "/api/workers/" + workerId + "/heartbeat"))
                .timeout(requestTimeout)
                .header(API_KEY_HEADER, apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(httpRequest);

        if (response.statusCode() != 200) {
            throw new MasterClientException(
                    "Heartbeat failed.",
                    response.statusCode(),
                    response.body()
            );
        }

        if (response.body() == null || response.body().isBlank()) {
            return new HeartbeatResponse("success");
        }

        return readJson(response.body(), HeartbeatResponse.class);
    }

    public void updateAllocation(String masterBaseUrl, UUID workerId, String apiKey, int sharedRamMb) {
        validateWorkerIdentity(workerId, apiKey);

        if (sharedRamMb < 0) {
            throw new IllegalArgumentException("Shared RAM cannot be negative.");
        }

        WorkerAllocationUpdateRequest request = new WorkerAllocationUpdateRequest(sharedRamMb);
        String requestBody = writeJson(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(buildUri(masterBaseUrl, "/api/workers/" + workerId + "/allocation"))
                .timeout(requestTimeout)
                .header(API_KEY_HEADER, apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(httpRequest);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new MasterClientException(
                    "Allocation update failed.",
                    response.statusCode(),
                    response.body()
            );
        }
    }

    public void updateHardwareSpec(
            String masterBaseUrl,
            UUID workerId,
            String apiKey,
            WorkerHardwareUpdateRequest request
    ) {
        validateWorkerIdentity(workerId, apiKey);

        String requestBody = writeJson(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(buildUri(masterBaseUrl, "/api/workers/" + workerId + "/spec"))
                .timeout(requestTimeout)
                .header(API_KEY_HEADER, apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(httpRequest);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new MasterClientException(
                    "Hardware spec update failed.",
                    response.statusCode(),
                    response.body()
            );
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MasterClientException("HTTP request was interrupted.", exception);
        } catch (IOException exception) {
            throw new MasterClientException("HTTP request failed.", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new MasterClientException("Failed to serialize JSON request.", exception);
        }
    }

    private <T> T readJson(String responseBody, Class<T> responseType) {
        try {
            return jsonMapper.readValue(responseBody, responseType);
        } catch (RuntimeException exception) {
            throw new MasterClientException("Failed to deserialize JSON response.", exception);
        }
    }

    private static URI buildUri(String masterBaseUrl, String path) {
        if (masterBaseUrl == null || masterBaseUrl.isBlank()) {
            throw new IllegalArgumentException("Master base URL cannot be blank.");
        }

        String normalizedBaseUrl = removeTrailingSlashes(masterBaseUrl.trim());
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
}