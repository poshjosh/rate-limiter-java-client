package io.github.poshjosh.ratelimiter.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimitDto {
    @Builder.Default
    private int permits = 1;
    @Builder.Default
    private boolean async = false;
    private RatesDto limit;
    private HttpRequestDto request;
}
