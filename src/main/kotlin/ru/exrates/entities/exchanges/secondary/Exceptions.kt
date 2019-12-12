package ru.exrates.entities.exchanges.secondary

import ru.exrates.entities.LimitType

class BanException : Exception() {
    override val message: String
        get() = "You are banned"
}

class ErrorCodeException : Exception() {
    override val message: String
        get() = "Error code not defined"
}

class LimitExceededException(val type: LimitType) : Exception() {

    override val message: String
        get() = "Limit <$type> exceeded"

}