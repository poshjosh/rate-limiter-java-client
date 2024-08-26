package io.github.poshjosh.ratelimiter.client.model;

import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpRequestDto {
    private String authType;
    private String characterEncoding;
    @NonNull // TODO - Add validation. Why the warning? // Must not be null
    private String contextPath;
    private Map<String, String> cookies;
    private Map<String, List<String>> headers;
    private Map<String, String> attributes;
    @NonNull // TODO - Add validation. Why the warning? // Must not be null or blank
    private String method;
    private Map<String, List<String>> parameters;
    private String remoteAddr;
    private List<String> locales;
    private List<String> userRoles;
    private String userPrincipal;
    @NonNull // TODO - Add validation. Why the warning? // Must not be null or blank
    private String requestUri;
    @NonNull // TODO - Add validation. Why the warning? // Must not be null
    private String servletPath;
    private String sessionId;

    @Override
    public String toString() {
        return "HttpRequestDto{" + method + ' ' + requestUri + "?sessionId=" + sessionId +
                ", headers=" + headers + ", cookies=" + cookies + ", locales" + locales + "}";
    }
}
