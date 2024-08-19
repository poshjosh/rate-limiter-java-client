package io.github.poshjosh.ratelimiter.client;

import java.io.IOException;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HappyPathCheck {
    private static final String baseUrl = "http://localhost:8080";

    public static void main(String... args) throws IOException, InterruptedException {
        happyPath();
    }

    private static void happyPath() throws IOException, InterruptedException {
        final RateLimiterServiceClient client = new RateLimiterServiceClient(baseUrl);
        final String rateId = "test-rate";
        log("POST expected rate of: 1/s, result: " + client
                .postRates(Collections.singletonMap(rateId, "1/s"))
                .get(0).getRates().get(0).getRate());
        log(" GET expected rate of: 1/s, result: " + client
                .getRates(rateId)
                .getRates().get(0).getRate());
        log("Available expected: true, result: " + client.isPermitAvailable(rateId));
        log("  Acquire expected: true, result: " +
                client.checkAndAsyncAcquire(rateId, null, 100, TimeUnit.MILLISECONDS));
        log("Available expected: false, result: " + client.isPermitAvailable(rateId));
        log("  Acquire expected: false, result: " + client.tryToAcquirePermit(rateId));
        client.deleteRates(rateId);
        log("Delete successful");
        final Map<String, Object> rateTree =
                Map.of("id", rateId, "rates", List.of(Map.of("rate", "3/s")));
        log("POST expected rate of: 3/s, result: " + client
                .postRateTree(rateTree)
                .get(0).getRates().get(0).getRate());
        client.deleteRates(rateId);
        log("Delete successful");
    }

    private static void log(String message) {
        System.out.println(LocalTime.now() + " " + message);
    }
}
