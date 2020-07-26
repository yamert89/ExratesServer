package ru.exrates.entities.exchanges.rest

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.getBean
import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONObject
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.LimitType
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.secondary.Limit
import ru.exrates.func.RestCore
import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration
import javax.annotation.PostConstruct
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity

@Entity @DiscriminatorValue("binance")
class BinanceExchange(): RestExchange() {

    /*
    * ******************************************************************************************************************
    *       Initialization
    * ******************************************************************************************************************
    * */

    @PostConstruct
    override fun init() {
        super.init()
        initVars()
        restCore = applicationContext.getBean(RestCore::class, URL_ENDPOINT, banCode, limitCode, serverError)
        val entity = restCore.blockingStringRequest(URL_ENDPOINT + URL_INFO, JSONObject::class)
        limitsFill(entity)
        pairsFill(entity.getJSONArray("symbols"), "baseAsset", "quoteAsset", "symbol")
        temporary = false
        fillTop()
        logger.debug("exchange " + name + " initialized with " + pairs.size + " pairs")


    }

    override fun initVars() {
        exId = 1
        URL_ENDPOINT = "https://api.binance.com"
        URL_CURRENT_AVG_PRICE = "/api/v3/avgPrice" //todo /api/v3/ticker/price ?
        URL_INFO = "/api/v1/exchangeInfo"
        URL_PRICE_CHANGE = "/api/v1/klines"
        URL_PING = "/api/v1/ping"
        URL_ORDER = "/api/v3/depth"
        URL_TOP_STATISTIC = "/api/v3/ticker/24hr"
        TOP_COUNT_FIELD = "count"
        TOP_SYMBOL_FIELD = "symbol"
        limitCode = 429
        banCode = 418
        taskTimeOut = TimePeriod(Duration.ofMinutes(3), "binanceTaskTimeout")
        name = "binance"
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
        historyPeriods = changePeriods.map { it.name }

    }

    override fun limitsFill(entity: JSONObject) {
        super.limitsFill(entity)
        val array = entity.getJSONArray("rateLimits")
        limits.add(
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

    /*
    * ******************************************************************************************************************
    *       Update methods
    * ******************************************************************************************************************
    * */

    override fun currentPrice(pair: CurrencyPair, period: TimePeriod) {
        super.currentPrice(pair, period)
        val uri = "$URL_ENDPOINT$URL_CURRENT_AVG_PRICE?symbol=${pair.symbol}"
        val entity = restCore.blockingStringRequest(uri, JSONObject::class)
        if (stateChecker.checkEmptyJson(entity, exId)) return
        val price = entity.getString("price").toDouble()
        pair.price = price
        logger.trace("Price updated on ${pair.symbol} pair $name exch| = $price")
    }

    override fun priceChange(pair: CurrencyPair, interval: TimePeriod) {
        super.priceChange(pair, interval)
        val debMills = System.currentTimeMillis()
      runBlocking {
           val job = launch{
               changePeriods.forEach {
                   launch(taskHandler.getExecutorContext()){
                       //val mono = singlePriceChangeRequest(pair, it)
                       updateSinglePriceChange(pair, it)
                   }
               }
           }
           job.join()
        }

        logger.debug("price change ends with ${System.currentTimeMillis() - debMills}")
    }

    override fun priceHistory(pair: CurrencyPair, interval: String, limit: Int){
        super.priceHistory(pair, interval, limit)
        val symbol = "?symbol=" + pair.symbol
        val period = "&interval=$interval"
        val uri = "$URL_ENDPOINT$URL_PRICE_CHANGE$symbol$period&limit=$limit"
        val entity = restCore.blockingStringRequest(uri, JSONArray::class)
        if (stateChecker.checkEmptyJson(entity, exId)) return
        pair.priceHistory.clear()
        for (i in 0 until entity.length()){
            val array = entity.getJSONArray(i)
            pair.priceHistory.add((array.getDouble(2) + array.getDouble(3)) / 2)
        }
        logger.trace("price history updated on ${pair.symbol} pair $name exch")

    }

    /*
    * ******************************************************************************************************************
    *       Super class methods
    * ******************************************************************************************************************
    * */

    override fun updateSinglePriceChange(pair: CurrencyPair, period: TimePeriod){
        val uri = "$URL_ENDPOINT$URL_PRICE_CHANGE?symbol=${pair.symbol}&interval=${period.name}&limit=1"
        val stringResponse = restCore.stringRequest(uri)
        val curMills = System.currentTimeMillis()
        val res = stringResponse.block()
        logger.trace("Response of $uri \n$res")
        val entity = JSONArray(res)
        if (stateChecker.checkEmptyJson(entity, exId)) return
        val array = entity.getJSONArray(0)
        val oldVal = (array.getDouble(2) + array.getDouble(3)) / 2
        val changeVol = if (pair.price > oldVal) ((pair.price - oldVal) * 100) / pair.price else (((oldVal - pair.price) * 100) / oldVal) * -1 //fixme full logging
        logger.trace("single price change calculating: price: ${pair.price}, period: ${period.name}, oldVal: $oldVal, changeVol: $changeVol")
        pair.putInPriceChange(period, BigDecimal(changeVol, MathContext(2)).toDouble())
        logger.trace("Change period updated in ${System.currentTimeMillis() - curMills} ms on ${pair.symbol} pair $name exch, interval = ${period.name} | change = $changeVol")
    }

    /*override fun singlePriceChangeRequest(pair: CurrencyPair, interval: TimePeriod): Mono<String> {
        val uri = "$URL_ENDPOINT$URL_PRICE_CHANGE?symbol=${pair.symbol}&interval=${interval.name}&limit=1"
        return
    }*/

    /*
    * ******************************************************************************************************************
    *       Class methods
    * ******************************************************************************************************************
    * */




}
