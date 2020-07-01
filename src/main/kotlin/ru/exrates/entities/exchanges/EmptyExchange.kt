package ru.exrates.entities.exchanges

import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod
import java.time.Duration

class EmptyExchange(): BasicExchange() {

    override fun currentPrice(pair: CurrencyPair, period: TimePeriod) {
    }

    override fun priceChange(pair: CurrencyPair, interval: TimePeriod) {
    }

    override fun priceHistory(pair: CurrencyPair, interval: String, limit: Int) {
    }

    override fun fillTop() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
