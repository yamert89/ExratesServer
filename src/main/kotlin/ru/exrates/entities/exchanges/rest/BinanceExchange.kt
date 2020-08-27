package ru.exrates.entities.exchanges.rest

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.getBean
import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Mono
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.LimitType
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.secondary.Limit
import ru.exrates.func.RestCore
import ru.exrates.utils.ClientCodes
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
        val errorHandler: (ClientResponse) -> Mono<Throwable> = { resp ->
            // val errBody = JSONObject(resp.bodyToMono(String::class.java).block())

            Mono.error(Exception("exception with ${resp.statusCode()}"))
        }
        if (!temporary){
            restCore = applicationContext.getBean(RestCore::class.java, URL_ENDPOINT, errorHandler)
            fillTop()
            return
        }
        initVars()
        val entity = restCore.blockingStringRequest(URL_ENDPOINT + URL_INFO, JSONObject::class)
        if (entity.hasErrors()) throw IllegalStateException("Failed info initialization")
        limitsFill(entity.second)
        pairsFill(entity.second.getJSONArray("symbols"), "baseAsset", "quoteAsset", "symbol")
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
        URL_PING = "$URL_ENDPOINT/api/v1/ping"
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
        if (stateChecker.checkEmptyJson(entity, exId) || entity.operateError(pair)) return
        val price = entity.second.getString("price").toDouble()
        pair.price = price
        logger.trace("Price updated on ${pair.symbol} pair $name exch| = $price")
    }

    override fun priceHistory(pair: CurrencyPair, interval: String, limit: Int){
        super.priceHistory(pair, interval, limit)
        val symbol = "?symbol=" + pair.symbol
        val period = "&interval=$interval"
        val uri = "$URL_ENDPOINT$URL_PRICE_CHANGE$symbol$period&limit=$limit"
        val entity = restCore.blockingStringRequest(uri, JSONArray::class)
        if (stateChecker.checkEmptyJson(entity, exId) || entity.operateError(pair)) return
        pair.priceHistory.clear()
        for (i in 0 until entity.second.length()){
            val array = entity.second.getJSONArray(i)
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
        val entity = restCore.blockingStringRequest(uri, JSONArray::class)
        logger.trace("Response of $uri \n$entity")
        if (stateChecker.checkEmptyJson(entity, exId) || entity.operateError(pair)) return
        val array = entity.second.getJSONArray(0)
        val oldVal = (array.getDouble(2) + array.getDouble(3)) / 2
        writePriceChange(pair, period, oldVal)
    }

    override fun <T : Any> Pair<HttpStatus, T>.getError(): Int {
        logger.error("Request has error: $second")
        return when(first){
            HttpStatus.OK -> ClientCodes.SUCCESS
            HttpStatus.INTERNAL_SERVER_ERROR  -> ClientCodes.EXCHANGE_NOT_ACCESSIBLE
            HttpStatus.TOO_MANY_REQUESTS -> ClientCodes.EXCHANGE_NOT_ACCESSIBLE
            HttpStatus.I_AM_A_TEAPOT -> ClientCodes.EXCHANGE_NOT_ACCESSIBLE
            else -> {
                val status = (second as JSONObject).getInt("code")
                when(status){
                    -1000 -> ClientCodes.TEMPORARY_UNAVAILABLE
                    -1007 -> ClientCodes.EXCHANGE_NOT_ACCESSIBLE
                    -1016 -> {
                        ClientCodes.EXCHANGE_NOT_FOUND
                    }
                    else -> {
                        logger.error("Unknown remote server error: $status")
                        ClientCodes.TEMPORARY_UNAVAILABLE
                    }
                }
            }

        }
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
