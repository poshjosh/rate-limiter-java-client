package io.github.poshjosh.ratelimiter.client;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class ServerException extends Exception {

    private final int responseCode;
    private final String responseBody;

    public ServerException() {
        this(null);
    }

    public ServerException(String message) {
        this(0, message, null);
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
