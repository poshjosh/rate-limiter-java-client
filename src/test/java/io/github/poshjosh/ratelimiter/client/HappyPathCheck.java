package io.github.poshjosh.ratelimiter.client;

import java.io.IOException;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class HappyPathCheck {
    private static final String serviceUrl = "http://localhost:8080";

    public static void main(String... args) throws IOException, RateLimiterClient.ServerException {
        happyPath();
    }

    private static void happyPath() throws IOException, RateLimiterClient.ServerException {
        final RateLimiterClient client = new RateLimiterClient(serviceUrl);

        final String rateId = HappyPathCheck.class.getSimpleName();
        log("POST expected rate of: 1/s, result: " + client
                .postRates(Collections.singletonMap(rateId, "1/s"))
                .get(0).getRates().get(0).getRate());
        log(" GET expected rate of: 1/s, result: " + client
                .getRates(rateId)
                .getRates().get(0).getRate());
        log("Available expected: true, result: " + client.isPermitAvailable(rateId));
        log("  Acquire expected: true, result: " + client.tryToAcquirePermit(rateId));
        log("Available expected: false, result: " + client.isPermitAvailable(rateId));
        log("  Acquire expected: false, result: " + client.tryToAcquirePermit(rateId));
        client.deleteRates(rateId);
        log("Delete successful");
        final Map<String, Object> rateTree = new HashMap<>();
        rateTree.put("id", rateId);
        rateTree.put("rates", Collections.singletonList(Collections.singletonMap("rate", "3/s")));
        log("POST expected rate of: 3/s, result: " + client
                .postRateTree(rateTree)
                .get(0).getRates().get(0).getRate());
        client.deleteRates(rateId);
        log("Delete successful");

        final String rate = "2/m";
        log("isWithinLimit("+rate+") expected: true, result: " + client.isWithinLimit(null, rateId, rate));
        log("isWithinLimit("+rate+") expected: true, result: " + client.isWithinLimit(null, rateId, rate));
        log("isWithinLimit("+rate+") expected: false, result: " + client.isWithinLimit(null, rateId, rate));
    }

    private static void log(String message) {
        System.out.println(LocalTime.now() + " " + message);
    }
}
