package com.pawpasta.glowscan_be.shared.handler;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


public class ExceptionHandler {

    public static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    public static ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    public static ResponseStatusException internalServerError(String message) {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public static ResponseStatusException locked(String message) {
        return new ResponseStatusException(HttpStatus.LOCKED, message);
    }

    public static ResponseStatusException notAcceptable(String message) {
        return new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, message);
    }

}
