package ru.exrates.entities.exchanges

import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.web.reactive.function.client.WebClient
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod
import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration
import javax.annotation.PostConstruct
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity

@Entity
@DiscriminatorValue("p2pb2b")
class P2pb2bExchange: RestExchange() {

    @PostConstruct
    override fun init() {
        super.init()
        initVars()
        webClient = WebClient.create(URL_ENDPOINT)
        val entity = JSONObject(stringResponse(URL_ENDPOINT + URL_INFO))
        pairsFill(entity, "result", "stock", "money", "name")
        temporary = false
        logger.debug("exchange " + name + " initialized with " + pairs.size + " pairs")

    }

    override fun initVars() {
        super.initVars()
        exId = 2
        URL_ENDPOINT = "https://api.p2pb2b.io"
        URL_CURRENT_AVG_PRICE = "/api/v2/public/ticker"
        URL_INFO = "/api/v2/public/markets"
        URL_PRICE_CHANGE = "/api/v2/public/market/kline"
        URL_PING = "/api/v2/public/ticker?market=ETH_BTC"
        limitCode = 0
        banCode = 0
        changePeriods.addAll(listOf(
            TimePeriod(Duration.ofMinutes(3), "1m"),
            TimePeriod(Duration.ofHours(1), "1h"),
            TimePeriod(Duration.ofDays(1), "1d")
        ))
        historyPeriods = changePeriods.map { it.name }

        if(!temporary) {
            super.init()
            return
        }

        name = "p2pb2b"


    }

    override fun currentPrice(pair: CurrencyPair, timeout: Duration) {
        super.currentPrice(pair, timeout)
        val uri = "$URL_ENDPOINT$URL_CURRENT_AVG_PRICE?market=${pair.symbol}"
        val entity = JSONObject(stringResponse(uri))
        val result = entity.getJSONObject("result")
        val bid = result.getDouble("bid")
        val ask = result.getDouble("ask")
        pair.price = (ask + bid) / 2
        logger.trace("Price updated on ${pair.symbol} pair | = ${pair.price} ex = $name")

    }

    override fun priceChange(pair: CurrencyPair, timeout: Duration) {
        super.priceChange(pair, timeout)
        changePeriods.forEach {
            val uri = "$URL_ENDPOINT$URL_PRICE_CHANGE?market=${pair.symbol}&interval=${it.name}&limit=1"
            val array = JSONObject(stringResponse(uri)).getJSONArray("result")
            val array2 = array.getJSONArray(0)
            val oldVal = (array2.getDouble(1) + array2.getDouble(2)) / 2
            val changeVol = if (pair.price > oldVal) ((pair.price - oldVal) * 100) / pair.price else (((oldVal - pair.price) * 100) / oldVal) * -1
            pair.putInPriceChange(it, BigDecimal(changeVol, MathContext(2)).toDouble())
            logger.trace("Change period updated on ${pair.symbol} pair $name exch, interval = $it.name | change = $changeVol")

        }


    }

    override fun priceHistory(pair: CurrencyPair, interval: String, limit: Int) {
        val uri = "$URL_ENDPOINT$URL_PRICE_CHANGE?market=${pair.symbol}&interval=$interval&limit=$limit"
        val array = JSONObject(stringResponse(uri)).getJSONArray("result")
        pair.priceHistory.clear()
        for (i in 0 until array.length()){
            val arr = array.getJSONArray(i)
            pair.priceHistory.add((arr.getDouble(1) + arr.getDouble(2)) / 2)
        }
        logger.trace("price history updated on ${pair.symbol} pair $name exch")

    }


}