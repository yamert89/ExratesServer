package ru.exrates.entities.exchanges

import org.springframework.stereotype.Component
import ru.exrates.entities.Currency
import ru.exrates.entities.CurrencyPair

@Component
interface Exchange {
    fun insertPair(pair: CurrencyPair)
    fun getPair(c1: Currency, c2: Currency): CurrencyPair
    fun getPair(pairName: String): CurrencyPair
}