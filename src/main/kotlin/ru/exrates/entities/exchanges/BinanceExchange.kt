package ru.exrates.entities.exchanges

import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.LimitType
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.secondary.Limit
import java.math.BigDecimal
import java.math.MathContext
import java.net.ConnectException
import java.time.Duration
import javax.annotation.PostConstruct
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import kotlin.math.round

@Entity @DiscriminatorValue("binance")
class BinanceExchange(): RestExchange() {

    @PostConstruct
    override fun init() {
        super.init()
        initVars()
        webClient = WebClient.create(URL_ENDPOINT)
        val entity = JSONObject(stringResponse(URL_ENDPOINT + URL_INFO).block())
        limitsFill(entity)
        pairsFill(entity, "symbols", "baseAsset", "quoteAsset", "symbol")
        temporary = false
        logger.debug("exchange " + name + " initialized with " + pairs.size + " pairs")


    }

    override fun initVars() {
        super.initVars()
        exId = 1
        URL_ENDPOINT = "https://api.binance.com"
        URL_CURRENT_AVG_PRICE = "/api/v3/avgPrice" //todo /api/v3/ticker/price ?
        URL_INFO = "/api/v1/exchangeInfo"
        URL_PRICE_CHANGE = "/api/v1/klines"
        URL_PING = "/api/v1/ping"
        URL_ORDER = "/api/v3/depth"
        limitCode = 429
        banCode = 418
        historyPeriods = listOf("3m", "5m", "15m", "30m", "1h", "4h", "6h", "8h", "12h", "1d", "3d", "1w", "1M")
        if(!temporary) {
            super.init()
            return
        }

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

    }

    override fun limitsFill(entity: JSONObject) {
        super.limitsFill(entity)
        val array = entity.getJSONArray("rateLimits")
        limits.plus(
            Limit(
                "MINUTE",
                LimitType.WEIGHT,
                Duration.ofMinutes(1),
                1200
            )
        )
        for(i in 0 until array.length()){
            val ob = array.getJSONObject(i)
            limits.forEach {
                val name = ob.getString("interval")
                if(name == it.name) it.limitValue = ob.getInt("limit")
            }
        }
    }

    override fun currentPrice(pair: CurrencyPair, timeout: Duration) {
        super.currentPrice(pair, timeout)
        val uri = "$URL_ENDPOINT$URL_CURRENT_AVG_PRICE?symbol=${pair.symbol}"
        val entity = JSONObject(stringResponse(uri).block())
        val price = entity.getString("price").toDouble()
        pair.price = price
        logger.trace("Price updated on ${pair.symbol} pair $name exch| = $price")
    }

    override fun priceChange(pair: CurrencyPair, timeout: Duration, singlePeriod: String) {
        super.priceChange(pair, timeout, singlePeriod)
        val symbol = "?symbol=" + pair.symbol
        val period = "&interval="
        if (singlePeriod.isNotEmpty()) {
            val uri = "$URL_ENDPOINT$URL_PRICE_CHANGE$symbol$period${singlePeriod}&limit=1"
            updateSinglePriceChange(pair, this.changePeriods.find { it.name == singlePeriod }!!, stringResponse(uri))
        }
        val list = hashMapOf<TimePeriod, Mono<String>>()
        val debMills = System.currentTimeMillis()
        changePeriods.forEach {
            val uri = "$URL_ENDPOINT$URL_PRICE_CHANGE$symbol$period${it.name}&limit=1"
            list[it] = stringResponse(uri)
        }

        list.forEach { updateSinglePriceChange(pair, it.key, it.value)}
        logger.debug("price change ends with ${System.currentTimeMillis() - debMills}")
    }

    override fun priceHistory(pair: CurrencyPair, interval: String, limit: Int){
        super.priceHistory(pair, interval, limit)
        val symbol = "?symbol=" + pair.symbol
        val period = "&interval=$interval"
        val uri = "$URL_ENDPOINT$URL_PRICE_CHANGE$symbol$period&limit=$limit"
        val entity = JSONArray(stringResponse(uri).block())
        pair.priceHistory.clear()
        for (i in 0 until entity.length()){
            val array = entity.getJSONArray(i)
            pair.priceHistory.add((array.getDouble(2) + array.getDouble(3)) / 2)
        }
        logger.trace("price history updated on ${pair.symbol} pair $name exch")

    }

    override fun updateSinglePriceChange(pair: CurrencyPair, period: TimePeriod, stringResponse: Mono<String>){
        val curMills = System.currentTimeMillis()
        val res = stringResponse.block()
        val entity = JSONArray(res)
        val array = entity.getJSONArray(0)
        val oldVal = (array.getDouble(2) + array.getDouble(3)) / 2
        val changeVol = if (pair.price > oldVal) ((pair.price - oldVal) * 100) / pair.price else (((oldVal - pair.price) * 100) / oldVal) * -1
        pair.putInPriceChange(period, BigDecimal(changeVol, MathContext(2)).toDouble())
        logger.trace("Change period updated in ${System.currentTimeMillis() - curMills} ms on ${pair.symbol} pair $name exch, interval = ${period.name} | change = $changeVol")
    }


}

/*public void priceChange (CurrencyPair pair, Duration timeout, Map<String, String> uriVariables) //todo limit > 1 logic
            throws JSONException, LimitExceededException, ErrorCodeException, BanException{
        if (!dataElapsed(pair, timeout, 1)) return;
        for (TimePeriod per : changePeriods) {
            var entity = new JSONArray(stringResponse(URL_PRICE_CHANGE));
            var array = entity.getJSONArray(0);
            pair.putInPriceChange(per, (array.getDouble(2) + array.getDouble(3)) / 2);
        }
    }*/