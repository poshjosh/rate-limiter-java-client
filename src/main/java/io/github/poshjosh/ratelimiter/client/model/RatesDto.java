package io.github.poshjosh.ratelimiter.client.model;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatesDto {

    private String parentId;

    private String id;

    @Builder.Default
    private Operator operator = Operator.NONE;

    private List<RateDto> rates;

    private String when;
}
