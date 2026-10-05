package org.donriro.nastavnik.user.exception;

public class InvalidRegistrationRequestException extends RuntimeException {

    public InvalidRegistrationRequestException(String message) {
        super(message);
    }
}
