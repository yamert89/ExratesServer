package ru.exrates.entities.exchanges

import org.apache.logging.log4j.Logger
import ru.exrates.entities.CurrencyPair
import java.time.Duration

class ExmoExchange() : BasicExchange(){
    init {
        URL_ENDPOINT = "https://api.exmo.com/v1"
        URL_INFO = "/pair_settings/"
        URL_PRICE_CHANGE = "/order_book/"
        URL_CURRENT_AVG_PRICE = "/ticker/"
    }


    override fun currentPrice(pair: CurrencyPair, timeout: Duration) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun priceChange(pair: CurrencyPair, timeout: Duration) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}