package io.github.poshjosh.ratelimiter.client.model;

import lombok.*;

import java.time.Duration;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
@ToString
public class RateDto {
    private static final Duration DEFAULT_DURATION = Duration.ofSeconds(1);

    @Builder.Default
    private String rate = "";

    private long permits;

    @Builder.Default
    private Duration duration = DEFAULT_DURATION;

    @Builder.Default
    private String when = "";

    @Builder.Default
    private String factoryClass = "";

    public void validate() {
        // TODO Use spring boot custom validation
        if ((rate == null || rate.isBlank()) && permits < 1) {
            throw new IllegalArgumentException(
                    "Specify either rate or permits.");
        }
        if ((rate != null && !rate.isBlank()) && permits > 0) {
            throw new IllegalArgumentException(
                    "Specify either rate or permits, not both.");
        }
        if (factoryClass != null && !factoryClass.isBlank()) {
            try {
                Class.forName(factoryClass);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Invalid factoryClass: " + factoryClass);
            }
        }
    }
}
