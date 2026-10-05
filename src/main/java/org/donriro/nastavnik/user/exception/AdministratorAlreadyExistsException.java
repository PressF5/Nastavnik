package org.donriro.nastavnik.user.exception;

public class AdministratorAlreadyExistsException extends RuntimeException {

    public AdministratorAlreadyExistsException(String message) {
        super(message);
    }
}
