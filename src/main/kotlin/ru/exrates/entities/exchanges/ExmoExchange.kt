package ru.exrates.entities.exchanges

import org.apache.logging.log4j.Logger
import org.springframework.web.reactive.function.client.WebClient
import ru.exrates.configs.Properties
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod
import java.time.Duration
import javax.annotation.PostConstruct
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity

@Entity
@DiscriminatorValue("exmo")
class ExmoExchange() : BasicExchange(){
    init {
        URL_ENDPOINT = "https://api.exmo.com/v1"
        URL_INFO = "/pair_settings/"
        URL_PRICE_CHANGE = "/order_book/"
        URL_CURRENT_AVG_PRICE = "/ticker/"
       updatePeriod = Duration.ofMillis(1000)
       URL_ORDER = ""
       URL_PING = ""
        props = Properties()
        name = "ExcmoExchange"
        webClient = WebClient.create()


    }

    @PostConstruct
    override fun init() {
        changePeriods.addAll(listOf(
            TimePeriod(Duration.ofMinutes(3), "3m"),
            TimePeriod(Duration.ofMinutes(5), "5m"),
            TimePeriod(Duration.ofMinutes(15), "15m"),
            TimePeriod(Duration.ofMinutes(30), "30m"),
            TimePeriod(Duration.ofHours(1), "1h"),
            TimePeriod(Duration.ofHours(4), "4h"),
            TimePeriod(Duration.ofHours(6), "6h"),
            TimePeriod(Duration.ofHours(8), "8h"),
            TimePeriod(Duration.ofHours(12), "12h"),
            TimePeriod(Duration.ofDays(1), "1d"),
            TimePeriod(Duration.ofDays(3), "3d"),
            TimePeriod(Duration.ofDays(7), "1w"),
            TimePeriod(Duration.ofDays(30), "1M")
        )
        )

        pairs.add(CurrencyPair("VENBTC", this))
        super.init()

    }


    override fun currentPrice(pair: CurrencyPair, timeout: Duration) {

    }

    override fun priceChange(pair: CurrencyPair, timeout: Duration) {

    }

}