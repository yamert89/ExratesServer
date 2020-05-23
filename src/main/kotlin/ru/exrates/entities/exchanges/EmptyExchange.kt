package ru.exrates.entities.exchanges

import ru.exrates.entities.CurrencyPair
import java.time.Duration

class EmptyExchange: BasicExchange() {
    override fun currentPrice(pair: CurrencyPair, timeout: Duration) {

    }

    override fun priceChange(pair: CurrencyPair, timeout: Duration, singlePeriod: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    override fun priceHistory(pair: CurrencyPair, interval: String, limit: Int) {

    }
}
