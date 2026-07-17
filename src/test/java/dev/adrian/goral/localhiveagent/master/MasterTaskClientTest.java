package dev.adrian.goral.localhiveagent.master;

import dev.adrian.goral.localhiveagent.master.dto.ClaimedExecutionPayload;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterTaskClientTest {

    private static final UUID WORKER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID EXECUTION_ID = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
    private static final String MASTER_BASE_URL = "http://localhost:8080/";
    private static final String API_KEY = "worker-api-key";
    private static final String LEASE_TOKEN = "lease-token";

    @Test
    void shouldClaimAssignedExecution() {
        FakeHttpClient httpClient = new FakeHttpClient();
        httpClient.enqueue(200, """
                {
                  "executionId": "223e4567-e89b-12d3-a456-426614174000",
                  "executorId": "localhive.no-op",
                  "executorContractVersion": 1,
                  "configuration": {"message": "hello"},
                  "requiredRamMb": 128,
                  "requiredCpuCores": 1,
                  "gpuRequired": false,
                  "leaseToken": "lease-token",
                  "leaseExpiresAt": "2026-07-17T12:10:00"
                }
                """);
        MasterTaskClient client = createClient(httpClient);

        Optional<ClaimedExecutionPayload> response = client.claimNext(MASTER_BASE_URL, WORKER_ID, API_KEY);

        assertTrue(response.isPresent());
        assertEquals(EXECUTION_ID, response.get().executionId());
        assertEquals("localhive.no-op", response.get().executorId());
        assertEquals(1, response.get().executorContractVersion());
        assertEquals("hello", response.get().configuration().get("message"));
        assertEquals(128, response.get().requiredRamMb());
        assertEquals(1, response.get().requiredCpuCores());
        assertFalse(response.get().gpuRequired());
        assertEquals(LEASE_TOKEN, response.get().leaseToken());
        assertEquals(LocalDateTime.parse("2026-07-17T12:10:00"), response.get().leaseExpiresAtDateTime());

        RecordedRequest request = httpClient.requests.getFirst();
        assertEquals("POST", request.method());
        assertEquals("/api/workers/" + WORKER_ID + "/assigned-executions/claim-next", request.path());
        assertEquals(API_KEY, request.header("X-API-KEY"));
        assertEquals("", request.body());
    }

    @Test
    void shouldReturnEmptyWhenNoExecutionIsAssigned() {
        FakeHttpClient httpClient = new FakeHttpClient();
        httpClient.enqueue(204, "");
        MasterTaskClient client = createClient(httpClient);

        Optional<ClaimedExecutionPayload> response = client.claimNext(MASTER_BASE_URL, WORKER_ID, API_KEY);

        assertTrue(response.isEmpty());
    }

    @Test
    void shouldReportRunningSucceededFailedAndRenewWithLeaseHeader() {
        FakeHttpClient httpClient = new FakeHttpClient();
        httpClient.enqueue(200, "{\"status\":\"RUNNING\"}");
        httpClient.enqueue(200, "{\"status\":\"SUCCEEDED\"}");
        httpClient.enqueue(200, "{\"status\":\"FAILED\"}");
        httpClient.enqueue(200, "{\"leaseExpiresAt\":\"2026-07-17T12:30:00\"}");
        MasterTaskClient client = createClient(httpClient);

        client.reportRunning(MASTER_BASE_URL, WORKER_ID, EXECUTION_ID, API_KEY, LEASE_TOKEN);
        client.reportSucceeded(MASTER_BASE_URL, WORKER_ID, EXECUTION_ID, API_KEY, LEASE_TOKEN);
        client.reportFailed(MASTER_BASE_URL, WORKER_ID, EXECUTION_ID, API_KEY, LEASE_TOKEN, "TEST_FAILED", "boom");
        LocalDateTime renewedLease = client.renewLease(MASTER_BASE_URL, WORKER_ID, EXECUTION_ID, API_KEY, LEASE_TOKEN);

        assertEquals(LocalDateTime.parse("2026-07-17T12:30:00"), renewedLease);

        assertExecutionRequest(httpClient.requests.get(0), "running");
        assertExecutionRequest(httpClient.requests.get(1), "succeeded");
        assertExecutionRequest(httpClient.requests.get(2), "failed");
        assertTrue(httpClient.requests.get(2).body().contains("\"failureCode\":\"TEST_FAILED\""));
        assertTrue(httpClient.requests.get(2).body().contains("\"failureMessage\":\"boom\""));
        assertExecutionRequest(httpClient.requests.get(3), "lease/renew");
    }

    @Test
    void shouldMapHttpErrors() {
        FakeHttpClient httpClient = new FakeHttpClient();
        httpClient.enqueue(403, "{\"status\":\"error\",\"message\":\"denied\"}");
        MasterTaskClient client = createClient(httpClient);

        MasterClientException exception = assertThrows(
                MasterClientException.class,
                () -> client.claimNext(MASTER_BASE_URL, WORKER_ID, API_KEY)
        );

        assertEquals(403, exception.statusCode());
        assertTrue(exception.getMessage().contains("Access denied"));
    }

    @Test
    void shouldMapIoErrors() {
        FakeHttpClient httpClient = new FakeHttpClient();
        httpClient.throwIo = true;
        MasterTaskClient client = createClient(httpClient);

        MasterClientException exception = assertThrows(
                MasterClientException.class,
                () -> client.claimNext(MASTER_BASE_URL, WORKER_ID, API_KEY)
        );

        assertTrue(exception.getMessage().contains("Master task communication"));
    }

    private static MasterTaskClient createClient(FakeHttpClient httpClient) {
        return new MasterTaskClient(httpClient, JsonMapper.builder().build(), Duration.ofSeconds(1));
    }

    private static void assertExecutionRequest(RecordedRequest request, String action) {
        assertEquals("POST", request.method());
        assertEquals("/api/workers/" + WORKER_ID + "/executions/" + EXECUTION_ID + "/" + action, request.path());
        assertEquals(API_KEY, request.header("X-API-KEY"));
        assertEquals(LEASE_TOKEN, request.header("X-EXECUTION-LEASE"));
    }

    private record RecordedRequest(
            String method,
            String path,
            HttpHeaders headers,
            String body
    ) {

        private String header(String name) {
            return headers.firstValue(name).orElse("");
        }
    }

    private static final class FakeHttpClient extends HttpClient {

        private final ArrayDeque<FakeHttpResponse<String>> responses = new ArrayDeque<>();
        private final List<RecordedRequest> requests = new ArrayList<>();
        private boolean throwIo;

        private void enqueue(int statusCode, String body) {
            responses.add(new FakeHttpResponse<>(statusCode, body));
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            if (throwIo) {
                throw new IOException("Connection refused");
            }

            requests.add(new RecordedRequest(
                    request.method(),
                    request.uri().getPath(),
                    request.headers(),
                    readBody(request)
            ));
            return (HttpResponse<T>) responses.removeFirst();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeHttpResponse<T> implements HttpResponse<T> {

        private final int statusCode;
        private final T body;

        private FakeHttpResponse(int statusCode, T body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("http://localhost");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    private static String readBody(HttpRequest request) {
        Optional<HttpRequest.BodyPublisher> publisher = request.bodyPublisher();
        if (publisher.isEmpty()) {
            return "";
        }

        BodyCaptureSubscriber subscriber = new BodyCaptureSubscriber();
        publisher.get().subscribe(subscriber);
        return subscriber.body();
    }

    private static final class BodyCaptureSubscriber implements Flow.Subscriber<ByteBuffer> {

        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            output.write(bytes, 0, bytes.length);
        }

        @Override
        public void onError(Throwable throwable) {
            throw new IllegalStateException(throwable);
        }

        @Override
        public void onComplete() {
        }

        private String body() {
            return output.toString(StandardCharsets.UTF_8);
        }
    }
}
