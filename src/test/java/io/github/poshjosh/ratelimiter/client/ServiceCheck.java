package io.github.poshjosh.ratelimiter.client;

import io.github.poshjosh.ratelimiter.client.model.HttpRequestDto;

import java.io.IOException;
import java.util.Collections;

public class ServiceCheck {
    private static final String serviceUrl = "http://localhost:8080";
    
    public static void main(String... args) throws IOException, ServerException {
        final RateLimiterServiceClient client = new RateLimiterServiceClient(serviceUrl);
        final String rateId = ServiceCheck.class.getSimpleName();

        long startTime = System.currentTimeMillis();
        client.postRate(rateId, "1/s", "web.request.header[X-SAMPLE-TRIGGER] = true");
        // average 140 milliseconds
        System.out.println("Add limit, time spent: " + (System.currentTimeMillis() - startTime));

        startTime = System.currentTimeMillis();
        client.tryToAcquirePermits(rateId, 1, false, givenNoRequest());
        // average without request: 10 millisecond
        System.out.println("Acquire permit, time spent: " + (System.currentTimeMillis() - startTime));

        startTime = System.currentTimeMillis();
        client.tryToAcquirePermits(rateId, 1, false, givenRequest());
        // average with request: 50 milliseconds
        System.out.println("Acquire permit, time spent: " + (System.currentTimeMillis() - startTime));

        startTime = System.currentTimeMillis();
        client.deleteRates(rateId);
        System.out.println("Delete limit, time spent: " + (System.currentTimeMillis() - startTime));
    }

    private static HttpRequestDto givenNoRequest() { return null; }

    private static HttpRequestDto givenRequest() {
        return HttpRequestDto.builder()
                .characterEncoding("UTF-8")
                .headers(Collections.singletonMap("Content-Type", Collections.singletonList("application/json")))
                .contextPath("")
                .method("GET")
                .requestUri("http://localhost:8081/basket")
                .servletPath("/checkout")
                .build();
    }
}
