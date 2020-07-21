package ru.exrates.entities.exchanges

import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod

//https://huobiapi.github.io/docs/spot/v1/en/#preparation

class HuobiExchange: RestExchange() {
    override fun updateSinglePriceChange(pair: CurrencyPair, period: TimePeriod) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}