package io.github.poshjosh.ratelimiter.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.poshjosh.ratelimiter.client.model.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import okhttp3.*;
import okio.BufferedSink;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RateLimiterClient {
    private static final Logger LOGGER = Logger.getLogger(RateLimiterClient.class.getName());
    private static final MediaType applicationJson = MediaType.parse("application/json");
    private static final RequestBody emptyRequestBody = new RequestBody() {
        @Override public MediaType contentType() { return applicationJson; }
        @Override public void writeTo(BufferedSink bufferedSink) { /* Nothing to write */ }
    };

    private final String serverBaseUrl;
    private final Charset charset;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Set<String> existingRateIds;

    public RateLimiterClient(String serverBaseUrl) {
        this(serverBaseUrl, StandardCharsets.ISO_8859_1,
                new OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build(),
                new ObjectMapper().findAndRegisterModules(), new HashSet<>());
    }

    public RateLimiterClient(
            String serverBaseUrl, Charset charset,
            OkHttpClient httpClient, ObjectMapper objectMapper,
            Set<String> existingRateIds) {
        this.serverBaseUrl = Objects.requireNonNull(serverBaseUrl);
        this.charset = Objects.requireNonNull(charset);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.existingRateIds = Objects.requireNonNull(existingRateIds);
    }

    public RateLimiterClient withTimeout(long timeout, TimeUnit timeUnit) {
        OkHttpClient newHttpClient = httpClient.newBuilder()
                .connectTimeout(timeout, timeUnit)
                .readTimeout(timeout, timeUnit)
                .build();
        return new RateLimiterClient(
                serverBaseUrl, charset, newHttpClient, objectMapper, existingRateIds);
    }

    /**
     * Checks if the request is within the specified rate limit.
     * @param request The request to check
     * @param rateId An identifier for the rate limit to apply
     * @param rate The rate of the rate limit to apply.
     *             (used to initialize the limit if not already initialize)
     * @return True if the request is within the rate limit, false otherwise.
     * @see #isWithinLimit(HttpServletRequest, String, String, String)
     */
    public boolean isWithinLimit(HttpServletRequest request, String rateId, String rate) {
        return isWithinLimit(request, rateId, rate, null);
    }

    /**
     * Checks if the request is within the specified rate limit.
     * @param request The request to check
     * @param rateId An identifier for the rate limit to apply
     * @param rate The rate of the rate limit to apply.
     *             (used to initialize the limit if not already initialize)
     * @param condition The condition of the rate limit to apply.
     *                  E.g. "jvm.memory.available<1GB"
     * @return True if the request is within the rate limit, false otherwise.
     */

    public boolean isWithinLimit(
            /* Nullable */ HttpServletRequest request, String rateId, String rate, String condition) {
        if (!existingRateIds.contains(rateId)) {
            try {
                return postRateThenTryToAcquirePermit(request, rateId, rate, condition);
            } catch (IOException | ServerException e) {
                return onError("Post rate and acquire permit", e, rateId, request);
            }
        } else {
            try {
                return tryToAcquirePermits(request, rateId, 1, false);
            } catch (IOException | ServerException e) {
                return onError("Acquire permit", e, rateId, request);
            }
        }
    }

    public RatesDto getRates(String id) throws IOException, ServerException {
        final Request request = request("/rates/" + id).get().build();
        final String responseBodyStr = sendForResponseBodyString(request);
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
            throws IOException, ServerException {
        final Request request = request("/rates/tree").post(requestBody(rateTree)).build();
        final String responseBodyStr = sendForResponseBodyString(request);
        final List<RatesDto> result = objectMapper
                .readValue(responseBodyStr, new TypeReference<List<RatesDto>>() { });
        result.stream().map(RatesDto::getId).forEach(existingRateIds::add);
        return result;
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
            throws IOException, ServerException {
        List<RatesDto> ratesDtos = limitMappings.entrySet().stream()
                .map(entry -> RatesDto.builder()
                        .id(entry.getKey())
                        .rates(Collections.singletonList(RateDto.builder().rate(entry.getValue()).build())).build())
                .collect(Collectors.toList());
        return postRates(ratesDtos);
    }

    public List<RatesDto> postRates(List<RatesDto> ratesDtos) throws IOException, ServerException {
        final List<RatesDto> result = new ArrayList<>(ratesDtos.size());
        for (RatesDto rates : ratesDtos) {
            result.add(postRate(rates));
        }
        return Collections.unmodifiableList(result);
    }

    public RatesDto postRate(String rateId, String rate) throws IOException, ServerException {
        return postRate(rateId, rate, null);
    }

    public RatesDto postRate(String rateId, String rate, String condition) throws IOException, ServerException {
        RateDto rateDto = RateDto.builder().rate(rate).when(condition).build();
        RatesDto ratesDto = RatesDto.builder()
                .id(rateId).rates(Collections.singletonList(rateDto)).build();
        return postRate(ratesDto);
    }

    public RatesDto postRate(RatesDto ratesDto) throws IOException, ServerException {
        final Request request = request("/rates").post(requestBody(ratesDto)).build();
        final String responseBodyStr = sendForResponseBodyString(request);
        final RatesDto result = objectMapper.readValue(responseBodyStr, RatesDto.class);
        existingRateIds.add(result.getId());
        return result;
    }

    public void deleteRates(String id) throws IOException, ServerException {
        final Request request = request("/rates/" + id).delete().build();
        sendForNoResponseBody(request);
    }

    public boolean isPermitAvailable(String rateId) throws IOException, ServerException {
        return isPermitAvailable((HttpRequestDto)null, rateId);
    }

    public boolean isPermitAvailable(/* Nullable */ HttpServletRequest request, String rateId)
            throws IOException, ServerException {
        return isPermitAvailable(HttpRequestDtos.of(request), rateId);
    }

    protected boolean isPermitAvailable(/* Nullable */ HttpRequestDto requestDto, String rateId)
            throws IOException, ServerException {
        final String path = "/permits/available?rateId=" + rateId;
        final RequestBody requestBody = requestBody(requestDto);
        final Request request = request(path).patch(requestBody).build();
        final String responseBodyStr = sendForResponseBodyString(request);
        return Boolean.parseBoolean(responseBodyStr);
    }

    /**
     * Tries to acquire a single permit.
     * @param rateId The id of the rate to acquire permits from.
     * @return True if permits are acquired, false otherwise.
     * @throws IOException If there was an error communicating with the server.
     * @throws ServerException If the server returned an error response.
     * @see #tryToAcquirePermits(HttpRequestDto, String, int, boolean)
     */
    public boolean tryToAcquirePermit(String rateId) throws IOException, ServerException {
        return tryToAcquirePermits((HttpRequestDto)null, rateId, 1, false);
    }

    protected boolean onError(
            String action, Exception exception,
            String rateId, /* Nullable */ HttpServletRequest request) {
        if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.warning(action + " error. Rate: "+rateId+" for: "+request+". "+exception);
        }
        return true;
    }

    /**
     * Add the specified rates (if not already added) then try to acquire 1 permit.
     * @param request The HttpServletRequest to acquire permits for.
     * @param rateId The id of the rate to acquire permits from.
     * @return True if permits are available, false otherwise.
     * @throws IOException If there was an error communicating with the server.
     * @throws ServerException If the server returned an error response.
     * @see #postRatesThenTryToAcquirePermits(LimitDto)
     */
    public boolean postRateThenTryToAcquirePermit(
            /* Nullable */ HttpServletRequest request, String rateId, String rate, String condition)
            throws IOException, ServerException {
        RateDto rateDto = RateDto.builder().rate(rate).when(condition).build();
        RatesDto ratesDto = RatesDto.builder()
                .id(rateId).rates(Collections.singletonList(rateDto)).build();
        return postRatesThenTryToAcquirePermits(
                LimitDto.builder().limit(ratesDto).request(HttpRequestDtos.of(request)).build());
    }

    /**
     * Add the specified rates (if not already added) then try to acquire 1 permit.
     * <p>
     * If async is true, the async part is done on the server. The server
     * checks if permits are available and return true if it is, otherwise
     * it returns false. Before returning, the server starts an async
     * process to acquire the permits.
     * </p>
     * @param limitDto An object encapsulating request data, to add rates and acquire permits for.
     * @return True if permits are available, false otherwise.
     * @throws IOException If there was an error communicating with the server.
     * @throws ServerException If the server returned an error response.
     */
    protected boolean postRatesThenTryToAcquirePermits(LimitDto limitDto)
            throws IOException, ServerException {
        final Request request = request("/permits/limit").patch(requestBody(limitDto)).build();
        return sendForResponseCode(request) != 429;
    }


    /**
     * Try to acquire the specified number of permits.
     * @param rateId The id of the rate to acquire permits from.
     * @param permits The number of permits to acquire.
     * @param async Whether to acquire the permits asynchronously on the server.
     * @param request The HttpServletRequest to acquire permits for.
     * @return True if permits are available, false otherwise.
     * @throws IOException If there was an error communicating with the server.
     * @throws ServerException If the server returned an error response.
     * @see #tryToAcquirePermits(HttpRequestDto, String, int, boolean)
     */
    public boolean tryToAcquirePermits(
            /* Nullable */ HttpServletRequest request, String rateId, int permits, boolean async)
            throws IOException, ServerException {
        return tryToAcquirePermits(HttpRequestDtos.of(request), rateId, permits, async);
    }

    /**
     * Try to acquire the specified number of permits.
     * <p>
     * If async is true, the async part is done on the server. The server
     * checks if permits are available and return true if it is, otherwise
     * it returns false. Before returning, the server starts an async
     * process to acquire the permits.
     * </p>
     * @param requestDto An object encapsulating request data, to acquire permits for.
     * @param rateId The id of the rate to acquire permits from.
     * @param permits The number of permits to acquire.
     * @param async Whether to acquire the permits asynchronously on the server.
     * @return True if permits are available, false otherwise.
     * @throws IOException If there was an error communicating with the server.
     * @throws ServerException If the server returned an error response.
     */
    protected boolean tryToAcquirePermits(
            /* Nullable */ HttpRequestDto requestDto, String rateId, int permits, boolean async)
            throws IOException, ServerException {
        final String path = String.format(
                "/permits/acquire?rateId=%s&permits=%d&async=%s", rateId, permits, async);
        final Request request = request(path).patch(requestBody(requestDto)).build();
        return sendForResponseCode(request) != 429;
    }

    private Request.Builder request(String path) {
        return new Request.Builder()
                .url(url(path))
                .header("Content-Type", "application/json");
    }

    private RequestBody requestBody(Object body) throws JsonProcessingException {
        if (body == null) {
            return emptyRequestBody;
        }
        String json = objectMapper.writeValueAsString(body);
        return RequestBody.create(applicationJson, json.getBytes(charset));
    }

    private void sendForNoResponseBody(Request request) throws IOException, ServerException {
        try(Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                complain(response);
            }
        }
    }

    private String sendForResponseBodyString(Request request) throws IOException, ServerException {
        try(Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                complain(response);
            }
            String responseBodyStr = responseBodyStr(response);
            if (responseBodyStr == null || responseBodyStr.isEmpty()) {
                complain(response);
            }
            return responseBodyStr;
        }
    }

    private int sendForResponseCode(Request request)
            throws IOException, ServerException {
        try(Response response = httpClient.newCall(request).execute()) {
            final int responseCode = response.code();
            if (responseCode != 429 && !response.isSuccessful()) {
                complain(response);
            }
            return response.code();
        }
    }

    private void complain(Response response) throws IOException, ServerException {
        throw new ServerException(response.code(), response.message(), responseBodyStr(response));
    }

    private String responseBodyStr(Response response) throws IOException {
        ResponseBody responseBody = response.body();
        return responseBody == null ? null : responseBody.string();
    }

    private String url(String path) {
        return serverBaseUrl + path;
    }

    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class ServerException extends Exception {

        private final int responseCode;
        private final String responseBody;

        public ServerException() {
            this(0, null, null);
        }

        public ServerException(int code, String message, String body) {
            super(message);
            this.responseCode = code;
            this.responseBody = body;
        }

        @Override
        public String toString() {
            return "ServerException{" + "responseCode=" + getResponseCode()
                    + ", responseMessage='" + getLocalizedMessage() + '\''
                    + ", responseBody='" + getResponseBody() + '\'' + '}';
        }
    }
}
