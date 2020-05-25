package ru.exrates.entities.exchanges

import org.springframework.stereotype.Component
import ru.exrates.entities.CurrencyPair

@Component
interface Exchange {
    fun insertPair(pair: CurrencyPair)
    fun getPair(c1: String, c2: String): CurrencyPair?
    fun getPair(pairName: String): CurrencyPair?
    fun fillTop()
}