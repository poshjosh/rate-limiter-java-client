package io.github.poshjosh.ratelimiter.client.model;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
@ToString
public class RatesDto {

    private String parentId;

    private String id;

    @Builder.Default
    private Operator operator = Operator.NONE;

    private List<RateDto> rates;

    private String when;

    public void validate() {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("RatesDto##id is required.");
        }
        if ((operator == null || operator == Operator.NONE) && rates.size() > 1) {
            throw new IllegalArgumentException(
                    "RatesDto#operator is required, when there are more than one rates");
        }
        if ((rates == null || rates.isEmpty())) {
            throw new IllegalArgumentException("RatesDto#rate is required");
        }
        rates.forEach(RateDto::validate);
    }
}
