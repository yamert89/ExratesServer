package ru.exrates.entities.exchanges

import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod
import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration
import java.util.*
import javax.annotation.PostConstruct
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import kotlin.Exception

@Entity
@DiscriminatorValue("p2pb2b")
class P2pb2bExchange: RestExchange() {

    @PostConstruct
    override fun init() {
        super.init()
        if (!temporary){
            webClient = WebClient.create(URL_ENDPOINT)
            return
        }
        initVars()
        webClient = WebClient.create(URL_ENDPOINT)
        val entity = JSONObject(stringResponse(URL_ENDPOINT + URL_INFO).block())
        pairsFill(entity, "result", "stock", "money", "name")
        temporary = false
        logger.debug("exchange " + name + " initialized with " + pairs.size + " pairs")

    }

    override fun initVars() {
        super.initVars()
        exId = 2
        delimiter = "_"
        URL_ENDPOINT = "https://api.p2pb2b.io"
        URL_CURRENT_AVG_PRICE = "/api/v2/public/ticker"
        URL_INFO = "/api/v2/public/markets"
        URL_PRICE_CHANGE = "/api/v2/public/market/kline"
        URL_PING = "/api/v2/public/ticker?market=ETH_BTC"
        URL_TOP_STATISTIC = ""
        TOP_COUNT_FIELD = ""
        TOP_SYMBOL_FIELD = ""
        limitCode = 0
        banCode = 0
        taskTimeOut = TimePeriod(Duration.ofMinutes(1), "p2pTaskTimeout")
        historyPeriods = changePeriods.map { it.name }
        name = "p2pb2b"
        changePeriods.addAll(listOf(
            TimePeriod(Duration.ofMinutes(1), "1m"),
            TimePeriod(Duration.ofHours(1), "1h"),
            TimePeriod(Duration.ofDays(1), "1d")
        ))
    }

    override fun currentPrice(pair: CurrencyPair, period: TimePeriod) {
        super.currentPrice(pair, period)
        val uri = "$URL_ENDPOINT$URL_CURRENT_AVG_PRICE?market=${pair.symbol}"
        val entity = JSONObject(stringResponse(uri).block())
        val result = entity.getJSONObject("result")
        val bid = result.getDouble("bid")
        val ask = result.getDouble("ask")
        pair.price = (ask + bid) / 2
        logger.trace("Price updated on ${pair.symbol} pair | = ${pair.price} ex = $name")

    }

    override fun priceChange(pair: CurrencyPair, interval: TimePeriod) {
        super.priceChange(pair, interval)
        val debMills = System.currentTimeMillis()
        try{
            val list = hashMapOf<TimePeriod, Mono<String>>()
            changePeriods.forEach {
                list[it] = singlePriceChangeRequest(pair, it)
            }
            list.forEach {
                updateSinglePriceChange(pair, it.key, it.value)
            }
        }catch (e: Exception){
            logger.error("Connect exception") //todo wrong operate
        }
        logger.debug("price change ends with ${System.currentTimeMillis() - debMills}")
    }

    override fun priceHistory(pair: CurrencyPair, interval: String, limit: Int) {
        super.priceHistory(pair, interval, limit)
        val lim = if (limit < 50) 50 else limit
        val uri = "$URL_ENDPOINT$URL_PRICE_CHANGE?market=${pair.symbol}&interval=$interval&limit=$lim"

        try{
            val array = JSONObject(stringResponse(uri).block()).getJSONArray("result")
            pair.priceHistory.clear()
            for (i in 0 until limit){
                val arr = array.getJSONArray(i)
                pair.priceHistory.add((arr.getDouble(1) + arr.getDouble(2)) / 2)
            }
            logger.trace("price history updated on ${pair.symbol} pair $name exch")
        }catch (e: Exception){logger.error("Connect exception")} //todo wrong operate

    }

    override fun updateSinglePriceChange(pair: CurrencyPair, period: TimePeriod, stringResponse: Mono<String>){
        val curMills = System.currentTimeMillis()
        val array = JSONObject(stringResponse.block()).getJSONArray("result")
        val array2 = array.getJSONArray(0)
        val oldVal = (array2.getDouble(1) + array2.getDouble(2)) / 2
        val changeVol = if (pair.price > oldVal) ((pair.price - oldVal) * 100) / pair.price else (((oldVal - pair.price) * 100) / oldVal) * -1
        pair.putInPriceChange(period, BigDecimal(changeVol, MathContext(2)).toDouble())
        logger.trace("Change period updated in ${System.currentTimeMillis() - curMills} ms on ${pair.symbol} pair $name exch, interval = ${period.name} | change = $changeVol")
    }

    override fun singlePriceChangeRequest(pair: CurrencyPair, interval: TimePeriod): Mono<String> {
        val uri = "$URL_ENDPOINT$URL_PRICE_CHANGE?market=${pair.symbol}&interval=${interval.name}&limit=50"
        return stringResponse(uri)
    }


}