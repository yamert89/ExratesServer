package ru.exrates.entities.exchanges.rest

import org.springframework.http.HttpStatus
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.LimitType
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.secondary.*
import ru.exrates.utils.ClientCodes
import java.time.Duration
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity

@Entity @DiscriminatorValue("binance")
class BinanceExchange(): RestExchange() {

    /*
    * ******************************************************************************************************************
    *       Initialization
    * ******************************************************************************************************************
    * */

    override fun extractInfo() {
        val entity = restCore.blockingStringRequest(URL_ENDPOINT + URL_INFO, ExRJsonObject::class)
        if (entity.hasErrors()) throw IllegalStateException("Failed info initialization")
        limitsFill(entity.second)
        pairsFill(entity.second.getJSONArray("symbols"), "baseAsset", "quoteAsset", "symbol")
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

    override fun limitsFill(entity: ExRJsonObject) {
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


    override fun CurrencyPair.currentPriceExt() = RestCurPriceObject(
        "$URL_ENDPOINT$URL_CURRENT_AVG_PRICE?symbol=${symbol}",
        ExRJsonObject::class
    ) { jsonUnit ->  (jsonUnit as ExRJsonObject).getString("price").toDouble()}

    override fun priceHistory(pair: CurrencyPair, interval: String, limit: Int){
        super.priceHistory(pair, interval, limit)
        val symbol = "?symbol=" + pair.symbol
        val period = "&interval=$interval"
        val uri = "$URL_ENDPOINT$URL_PRICE_CHANGE$symbol$period&limit=$limit"
        val entity = restCore.blockingStringRequest(uri, ExRJsonArray::class)
        if (failHandle(entity, pair)) return
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
        val entity = restCore.blockingStringRequest(uri, ExRJsonArray::class)
        logger.trace("Response of $uri \n$entity")
        if (failHandle(entity, pair)) return
        val array = entity.second.getJSONArray(0)
        val oldVal = (array.getDouble(2) + array.getDouble(3)) / 2
        writePriceChange(pair, period, oldVal)
    }

    override fun <T : Any> Pair<HttpStatus, T>.getError(): Int {
        return when(first){
            HttpStatus.OK -> ClientCodes.SUCCESS
            HttpStatus.INTERNAL_SERVER_ERROR  -> ClientCodes.EXCHANGE_NOT_ACCESSIBLE
            HttpStatus.TOO_MANY_REQUESTS -> ClientCodes.EXCHANGE_NOT_ACCESSIBLE
            HttpStatus.I_AM_A_TEAPOT -> ClientCodes.EXCHANGE_NOT_ACCESSIBLE
            else -> {
                val status = (second as ExRJsonObject).getInt("code")
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
