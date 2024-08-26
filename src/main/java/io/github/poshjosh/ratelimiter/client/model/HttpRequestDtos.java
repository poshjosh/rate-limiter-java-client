package io.github.poshjosh.ratelimiter.client.model;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.*;

public final class HttpRequestDtos {
    public static HttpRequestDto of(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        // Some expressions depend on sessionId, so we need to create a session if one doesn't exist
        final HttpSession session = request.getSession(true);

        // Headers
        Enumeration<String> headerNames = request.getHeaderNames();
        Map<String, List<String>> headers = headerNames.hasMoreElements() ? new HashMap<>() : null;
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            List<String> values = Collections.list(request.getHeaders(name));
            headers.put(name, values);
        }

        // Attributes
        Map<String, String> attributes = new HashMap<>();
        Enumeration<String> attributeNames = request.getAttributeNames();
        while (attributeNames.hasMoreElements()) {
            String name = attributeNames.nextElement();
            Object value = request.getAttribute(name);
            attributes.put(name, value == null ? null : value.toString());
        }

        // Cookies
        final boolean hasCookies = request.getCookies() != null && request.getCookies().length > 0;
        Map<String, String> cookies = hasCookies ? new HashMap<>() : null;
        if (hasCookies) {
            for (javax.servlet.http.Cookie cookie : request.getCookies()) {
                cookies.put(cookie.getName(), cookie.getValue());
            }
        }

        // Locales
        Enumeration<Locale> localesEnum = request.getLocales();
        List<String> locales = localesEnum.hasMoreElements() ? new ArrayList<>() : null;
        while (localesEnum.hasMoreElements()) {
            locales.add(localesEnum.nextElement().toString());
        }

        // Parameters
        Enumeration<String> parameterNames = request.getParameterNames();
        Map<String, List<String>> parameters = parameterNames.hasMoreElements() ? new HashMap<>() : null;
        while (parameterNames.hasMoreElements()) {
            String name = parameterNames.nextElement();
            List<String> values = Arrays.asList(request.getParameterValues(name));
            parameters.put(name, values);
        }

        return HttpRequestDto.builder()
                .method(request.getMethod())
                .headers(headers)
                .attributes(attributes)
                .authType(request.getAuthType())
                .characterEncoding(request.getCharacterEncoding())
                .contextPath(request.getContextPath())
                .cookies(cookies)
                .locales(locales)
                .parameters(parameters)
                .remoteAddr(request.getRemoteAddr())
                .requestUri(request.getRequestURI())
                .servletPath(request.getServletPath())
                .sessionId(session == null ? null : session.getId())
                .userPrincipal(request.getUserPrincipal() == null ?
                        null : request.getUserPrincipal().getName())
                // TODO - How do we get user roles? Do we just document our limitations?
                .userRoles(null)
                .build();
    }

    private HttpRequestDtos() { }
}
