package ru.exrates.entities.exchanges

import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod
import ru.exrates.func.RestCore
import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration
import javax.annotation.PostConstruct
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import kotlin.Exception

@Entity
@DiscriminatorValue("p2pb2b")
class P2pb2bExchange: RestExchange() {

    /*
    * ******************************************************************************************************************
    *       Initialization
    * ******************************************************************************************************************
    * */

    @PostConstruct
    override fun init() {
        super.init()
        if (!temporary){
            restCore = RestCore(URL_ENDPOINT, banCode, limitCode, serverError)
            fillTop()
            return
        }
        initVars()
        restCore = RestCore(URL_ENDPOINT, banCode, limitCode, serverError)
        val entity = restCore.blockingStringRequest(URL_ENDPOINT + URL_INFO, JSONObject::class)
        pairsFill(entity, "result", "stock", "money", "name", "_")
        temporary = false
        fillTop()
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
        name = "p2pb2b"
        changePeriods.addAll(listOf(
            TimePeriod(Duration.ofMinutes(1), "1m"),
            TimePeriod(Duration.ofHours(1), "1h"),
            TimePeriod(Duration.ofDays(1), "1d")
        ))
        historyPeriods = changePeriods.map { it.name }
    }

    override fun fillTop() {
        topPairs.addAll(listOf("ETH_BTC", "BNB_BTC", "DASH_BTC", "NEO_BTC", "BCH_BTC", "ETC_BTC", "BTG_BTC",
            "LTC_BTC", "XLM_BTC", "WAVES_BTC", "WTC_BTC", "GAS_BTC", "YAP_BTC", "DOGE_BTC", "ENJ_BTC", "HNC_BTC"))
    }

    /*
    * ******************************************************************************************************************
    *       Update methods
    * ******************************************************************************************************************
    * */

    override fun currentPrice(pair: CurrencyPair, period: TimePeriod) {
        super.currentPrice(pair, period)
        val uri = "$URL_ENDPOINT$URL_CURRENT_AVG_PRICE?market=${pair.symbol}"
        val entity = restCore.blockingStringRequest(uri, JSONObject::class)
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
            val array = restCore.blockingStringRequest(uri, JSONObject::class).getJSONArray("result")
            pair.priceHistory.clear()
            for (i in 0 until limit){
                val arr = array.getJSONArray(i)
                pair.priceHistory.add((arr.getDouble(1) + arr.getDouble(2)) / 2)
            }
            logger.trace("price history updated on ${pair.symbol} pair $name exch")
        }catch (e: Exception){logger.error("Connect exception")} //todo wrong operate

    }

    /*
    * ******************************************************************************************************************
    *       Super class methods
    * ******************************************************************************************************************
    * */

    override fun updateSinglePriceChange(pair: CurrencyPair, period: TimePeriod, stringResponse: Mono<String>){
        val curMills = System.currentTimeMillis()
        val response = stringResponse.block()
        val array = JSONObject(response).getJSONArray("result")
        if(array.length() == 0){
            pair.putInPriceChange(period, Double.MAX_VALUE)
            return
        }
        try {
            val array2 = array.getJSONArray(0)
            val oldVal = (array2.getDouble(1) + array2.getDouble(2)) / 2
            val changeVol =
                if (pair.price > oldVal) ((pair.price - oldVal) * 100) / pair.price else (((oldVal - pair.price) * 100) / oldVal) * -1
            pair.putInPriceChange(period, BigDecimal(changeVol, MathContext(2)).toDouble())
            logger.trace("Change period updated in ${System.currentTimeMillis() - curMills} ms on ${pair.symbol} pair $name exch, interval = ${period.name} | change = $changeVol")
        }catch (e: Exception){
            logger.error(e)
            logger.error("Response: $response")
        }
    }

    override fun singlePriceChangeRequest(pair: CurrencyPair, interval: TimePeriod): Mono<String> {
        val uri = "$URL_ENDPOINT$URL_PRICE_CHANGE?market=${pair.symbol}&interval=${interval.name}&limit=50"
        return restCore.stringRequest(uri)
    }


}