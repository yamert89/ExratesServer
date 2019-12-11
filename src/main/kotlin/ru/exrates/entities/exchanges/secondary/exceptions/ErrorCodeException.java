package ru.exrates.entities.exchanges.secondary.exceptions;

public class ErrorCodeException extends Exception{
    @Override
    public String getMessage() {
        return "Error code not defined";
    }
}
