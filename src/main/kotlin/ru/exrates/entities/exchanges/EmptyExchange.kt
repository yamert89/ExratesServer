package ru.exrates.entities.exchanges

import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.http.HttpStatus
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod

class EmptyExchange(): BasicExchange() {
    override var name = ""

    override fun currentPrice(pair: CurrencyPair, period: TimePeriod) {
    }

    override fun priceChange(pair: CurrencyPair, interval: TimePeriod) {
    }

    override fun priceHistory(pair: CurrencyPair, interval: String, limit: Int) {
    }

    fun fillTop(getArrayFunc: () -> Pair<HttpStatus, JSONArray>) {

    }
}
