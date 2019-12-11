package ru.exrates.entities.exchanges.secondary.exceptions;

import ru.exrates.entities.exchanges.secondary.LimitType;

public class LimitExceededException extends Exception{
    private LimitType type;

    public LimitExceededException(LimitType type) {
        this.type = type;
    }

    public LimitType getType() {
        return type;
    }

    @Override
    public String getMessage() {
        return String.format("%1$s %2$s %3$s", "Limit <", type, "> exceeded");
    }


}
