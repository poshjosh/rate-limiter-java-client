package io.github.poshjosh.ratelimiter.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.poshjosh.ratelimiter.client.model.HttpRequestDto;
import io.github.poshjosh.ratelimiter.client.model.RateDto;
import io.github.poshjosh.ratelimiter.client.model.RatesDto;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RateLimiterServiceClient {
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final HttpRequest.BodyPublisher emptyBodyPublisher =
            HttpRequest.BodyPublishers.noBody();
    private final HttpResponse.BodyHandler<String> responseBodyHandler =
            HttpResponse.BodyHandlers.ofString();

    public RateLimiterServiceClient(String baseUrl) {
        this(baseUrl,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build(),
                new ObjectMapper().findAndRegisterModules());
    }
    public RateLimiterServiceClient(
            String baseUrl, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = Objects.requireNonNull(baseUrl);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public RatesDto getRates(String id) throws IOException, InterruptedException {
        HttpRequest request = request("/rates/" + id).GET().build();
        String responseBodyStr = httpClient.send(request, responseBodyHandler).body();
        return objectMapper.readValue(responseBodyStr, RatesDto.class);
    }

    /**
     * Posts rate limits to the remote service.
     * Example input:
     * <pre>
     *     JSONObject limits = JSONParser.parse("{
     *         "id": "parent",
     *         "rate": "99/s" ,
     *         "login": { "rate": "5/m" },
     *         "search": { "rate": "20/s", "when": "web.request.user.role=GUEST" }
     *         "search": { "rate": "20/s", "when": "jvm.memory.available < 1gb" },
     *     }");
     * </pre>
     * <ul>
     *     <li>'5/m' means 5 permits per minute.</li>
     *     <li>'99/s' means 99 permits per second.</li>
     *     <li>s = second, m = minute, h = hour, d = day</li>
     * </ul>
     * @param rateTree The JSON hierarchy of the limits to post.
     * @return The posted rate limits for the provided mappings.
     */
    public List<RatesDto> postRateTree(Map<String, Object> rateTree)
            throws IOException, InterruptedException {
        HttpRequest request = request("/rates/tree")
                .POST(requestBody(rateTree)).build();
        String responseBodyStr = httpClient.send(request, responseBodyHandler).body();
        return objectMapper.readValue(responseBodyStr, new TypeReference<>() { });
    }

    /**
     * Posts rate limits to the remote service.
     * Example input:
     * <pre>
     *     Map limits = Map.of("login", "5/m", "home", "99/s");
     * </pre>
     * <ul>
     *     <li>'5/m' means 5 permits per minute.</li>
     *     <li>'99/s' means 99 permits per second.</li>
     *     <li>s = second, m = minute, h = hour, d = day</li>
     * </ul>
     * @param limitMappings The key-value pairs of limit id and rate limit.
     * @return The posted rate limits for the provided mappings.
     */
    public List<RatesDto> postRates(Map<String, String> limitMappings)
            throws IOException, InterruptedException {
        List<RatesDto> ratesDtos = limitMappings.entrySet().stream()
                .map(entry -> RatesDto.builder()
                        .id(entry.getKey())
                        .rates(List.of(RateDto.builder().rate(entry.getValue()).build())).build())
                .collect(Collectors.toList());
        return postRates(ratesDtos);
    }

    public List<RatesDto> postRates(List<RatesDto> ratesDtos)
            throws IOException, InterruptedException {
        final List<RatesDto> result = new ArrayList<>(ratesDtos.size());
        for (RatesDto rates : ratesDtos) {
            result.add(postRate(rates));
        }
        return Collections.unmodifiableList(result);
    }

    public RatesDto postRate(RatesDto ratesDto) throws IOException, InterruptedException {
        HttpRequest request = request("/rates").POST(requestBody(ratesDto)).build();
        String responseBodyStr = httpClient.send(request, responseBodyHandler).body();
        return objectMapper.readValue(responseBodyStr, RatesDto.class);
    }

    public void deleteRates(String id) throws IOException, InterruptedException {
        httpClient.send(
                request("/rates/" + id).DELETE().build(),
                HttpResponse.BodyHandlers.discarding());
    }

    public Boolean checkAndAsyncAcquire(
            String rateId, /* Nullable */ HttpRequestDto request,
            int timeout, TimeUnit timeUnit) {
        return checkAndAsyncAcquire(
                rateId, 1, request, timeout, timeUnit, Throwable::printStackTrace);
    }

    public Boolean checkAndAsyncAcquire(
            String rateId, int permits, /* Nullable */ HttpRequestDto request,
            long timeout, TimeUnit timeUnit, Consumer<Exception> onException) {
        try {
            return tryToAcquirePermits(rateId, permits, true, request, timeout, timeUnit);
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            Thread.currentThread().interrupt();
            onException.accept(e);
            return Boolean.TRUE;
        }
    }

    public Boolean isPermitAvailable(String rateId) throws IOException, InterruptedException {
        return isPermitAvailable(rateId, null);
    }

    public Boolean isPermitAvailable(String rateId, /* Nullable */ HttpRequestDto request)
            throws IOException, InterruptedException {
        final String path = "/permits?rateId=" + rateId;
        final HttpRequest.BodyPublisher requestBody = requestBody(request);
        final String responseBodyStr = httpClient.send(
                request(path).method("GET", requestBody).build(),
                responseBodyHandler).body();
        return Boolean.valueOf(responseBodyStr);
    }

    public Boolean isPermitAvailable(
            String rateId, /* Nullable */ HttpRequestDto request,
            long timeout, TimeUnit timeUnit)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final String path = "/permits?rateId=" + rateId;
        final String responseBodyStr = httpClient.sendAsync(
                request(path).method("GET", requestBody(request)).build(),
                responseBodyHandler).get(timeout, timeUnit).body();
        return Boolean.valueOf(responseBodyStr);
    }

    public Boolean tryToAcquirePermit(String rateId)
            throws IOException, InterruptedException {
        return tryToAcquirePermits(rateId, 1, false, null);
    }

    public Boolean tryToAcquirePermits(
            String rateId, int permits, boolean async, /* Nullable */ HttpRequestDto request)
            throws IOException, InterruptedException {
        final String path = path(rateId, permits, async);
        final String responseBodyStr = httpClient.send(
                request(path).PUT(requestBody(request)).build(),
                responseBodyHandler).body();
        return Boolean.valueOf(responseBodyStr);
    }

    public Boolean tryToAcquirePermits(
            String rateId, int permits, boolean async, /* Nullable */ HttpRequestDto request,
            long timeout, TimeUnit timeUnit)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final String path = path(rateId, permits, async);
        final String responseBodyStr = httpClient.sendAsync(
                request(path).PUT(requestBody(request)).build(),
                responseBodyHandler)
                .get(timeout, timeUnit).body();
        return Boolean.valueOf(responseBodyStr);
    }

    private String path(String rateId, int permits, boolean async) {
        return String.format("/permits?rateId=%s&permits=%d&async=%s", rateId, permits, async);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url(path)))
                .header("Content-Type", "application/json");
    }

    private HttpRequest.BodyPublisher requestBody(Object body) throws JsonProcessingException {
        if (body == null) {
            return emptyBodyPublisher;
        }
        String requestBodyStr = objectMapper.writeValueAsString(body);
        return HttpRequest.BodyPublishers.ofString(requestBodyStr);
    }

    private String url(String path) {
        return baseUrl + path;
    }
}
