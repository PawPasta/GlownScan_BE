package com.pawpasta.glowscan_be.shared.handler;


import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ApiExceptionFactory {

    private ApiExceptionFactory() {
    }

    public static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public static ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    public static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    public static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public static ResponseStatusException locked(String message) {
        return new ResponseStatusException(HttpStatus.LOCKED, message);
    }

    public static ResponseStatusException notAcceptable(String message) {
        return new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, message);
    }

    public static ResponseStatusException tooManyRequests(String message) {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    public static ResponseStatusException internalServerError(String message) {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public static ResponseStatusException serviceUnavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

}
