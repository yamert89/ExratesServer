package ru.exrates.entities.exchanges.secondary

import org.apache.logging.log4j.Logger
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.LimitType
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.entities.exchanges.BinanceExchange
import java.net.ConnectException
import java.time.Duration
import javax.annotation.PostConstruct
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity

@Entity @DiscriminatorValue("binance")
class BinanceExchange(private val logger: Logger): BasicExchange(logger) {
    init {
        URL_ENDPOINT = "https://api.binance.com"
        URL_CURRENT_AVG_PRICE = "/api/v3/avgPrice" //todo /api/v3/ticker/price ?
        URL_INFO = "/api/v1/exchangeInfo"
        URL_PRICE_CHANGE = "/api/v1/klines"
        URL_PING = "/api/v1/ping"
        URL_ORDER = "/api/v3/depth"
    }

    @PostConstruct
    override fun init() {
        limitCode = 429
        banCode = 418
        webClient = WebClient.create(URL_ENDPOINT)
        if(!temporary) return
        name = "binanceExchange"
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
            TimePeriod(Duration.ofDays(30), "1M"))
        )
        val entity = JSONObject(stringResponse(URL_ENDPOINT + URL_INFO))
        val array = entity.getJSONArray("rateLimits")
        limits.plus(Limit("MINUTE", LimitType.WEIGHT, Duration.ofMinutes(1), 1200))
        for(i in 0..array.length()){
            val ob = array.getJSONObject(i)
            limits.forEach {
                val name = ob.getString("interval")
                if(name == it.name) it.limitValue = ob.getInt("limit")
            }
        }
        val symbols = entity.getJSONArray("symbols")
        for(i in 0..symbols.length()){
            pairs.plus(CurrencyPair(symbols.getJSONObject(i).getString("symbol"), this))
        }

        temporary = false
        logger.debug("exchange " + name + "initialized with " + pairs.size + " pairs")
        //todo needs exceptions?
        super.init()
    }

    override fun task() {
        if(id == 0) return
        webClient.get().uri(URL_ENDPOINT + URL_PING).retrieve().onStatus(HttpStatus::isError){
            Mono.error(ConnectException("Ping $URL_PING failed"))
        }.bodyToMono(String::class.java).block()
        super.task()
    }

    private fun stringResponse(uri: String) = super.request(uri, String::class)





    override fun currentPrice(pair: CurrencyPair, timeout: Duration) {
        if(!dataElasped(pair, timeout, 0)){
            logger.debug("current price $pair.symbol req skipped")
            return
        }
        val uri = URL_ENDPOINT + URL_CURRENT_AVG_PRICE + "?symbol=" + pair.symbol
        val entity = JSONObject(stringResponse(uri))
        val price = entity.getString("price").toDouble()
        pair.price = price
        logger.debug("Price updated on ${pair.symbol} pair | = $price")
    }



    override fun priceChange(pair: CurrencyPair, timeout: Duration) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}