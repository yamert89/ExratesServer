package ru.exrates.entities.exchanges.secondary.exceptions;

public class BanException extends Exception {

    @Override
    public String getMessage() {
        return "You are banned";
    }
}
