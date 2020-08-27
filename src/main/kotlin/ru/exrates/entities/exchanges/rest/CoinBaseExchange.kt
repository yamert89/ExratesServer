package ru.exrates.entities.exchanges.rest

import org.springframework.beans.factory.getBean
import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.data.mapping.PreferredConstructor
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Mono
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.LimitType
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.entities.exchanges.secondary.Limit
import ru.exrates.func.RestCore
import ru.exrates.utils.ClientCodes
import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration
import java.time.Instant
import javax.annotation.PostConstruct
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity

//https://docs.pro.coinbase.com/#get-trades
//76019C0m0YLw0511 - coin base
//fixme 3 request per second !!! limit requests

@Entity
@DiscriminatorValue("coinbase")
class CoinBaseExchange: RestExchange() {
    private val pathId = "<product-id>"

    /*
   * ******************************************************************************************************************
   *       Initialization
   * ******************************************************************************************************************
   * */

    @PostConstruct
    override fun init() {
        super.init()
        val errorHandler: (ClientResponse) -> Mono<Throwable> = { resp ->
            //val errBody = JSONObject(resp.bodyToMono(String::class.java).block())

            Mono.error(Exception("exception with ${resp.statusCode()}"))
        }
        if (!temporary){
            restCore = applicationContext.getBean(RestCore::class.java, URL_ENDPOINT, errorHandler)
            fillTop()
            return
        }
        initVars()
        val entity = restCore.blockingStringRequest(URL_ENDPOINT + URL_INFO, JSONArray::class)
        if (entity.hasErrors()) throw IllegalStateException("Failed info initialization")
        pairsFill(entity.second, "base_currency", "quote_currency", "id")
        temporary = false
        fillTop()
        logger.debug("exchange " + name + " initialized with " + pairs.size + " pairs")
    }

    override fun initVars() {
       exId = 3
        URL_ENDPOINT = "https://api.pro.coinbase.com"
        URL_PING = "/time"
        URL_CURRENT_AVG_PRICE = "/products/<product-id>/book"
        URL_INFO = "/products"
        URL_PRICE_CHANGE = "/products/<product-id>/candles"
        URL_TOP_STATISTIC = "/products/<product-id>/stats"
        TOP_COUNT_FIELD = "volume"
        name = "coinbase"
        changePeriods.addAll(listOf(
            TimePeriod(Duration.ofSeconds(60), "1m"),
            TimePeriod(Duration.ofSeconds(300), "5m"),
            TimePeriod(Duration.ofSeconds(900), "15m"),
            TimePeriod(Duration.ofSeconds(3600), "1h"),
            TimePeriod(Duration.ofSeconds(21600), "6h"),
            TimePeriod(Duration.ofSeconds(86400), "1d")
        ))
        historyPeriods = changePeriods.map { it.name }

    }

    override fun fillTop() {
        if (props.skipTop()) return
        val list = pairs.map { it.symbol }
        /*val reqs = mutableMapOf<String, Mono<String>>()
        pairs.forEach {
            reqs[it.symbol] = restCore.stringRequest("$URL_ENDPOINT${URL_TOP_STATISTIC.replace(pathId, it.symbol)}")
        }
        val topSize = if(props.maxSize() < pairs.size) props.maxSize() else pairs.size
        topPairs.addAll(reqs.mapValues { JSONObject(it.value.block()).getDouble(TOP_COUNT_FIELD) }
            .entries.sortedByDescending { it.value }.map { it.key }.subList(0, topSize))*/
        val topSize = if(props.maxSize() < pairs.size) props.maxSize() else pairs.size
        val urls = pairs.map { "$URL_ENDPOINT${URL_TOP_STATISTIC.replace(pathId, it.symbol)}" }

        val flux = restCore.patchStringRequests(urls)
        flux.subscribe()
        val vList = flux.collectList().block().map {JSONObject(it).getDouble(TOP_COUNT_FIELD)  }
        val map = mutableMapOf<String, Double>()
        for(i in list.indices){
            map[list[i]] = vList[i]
        }
       topPairs.addAll(map.entries.sortedByDescending { it.value }.map { it.key }.subList(0, topSize))

    }

    override fun limitsFill(entity: JSONObject) {
        super.limitsFill(entity)
        limits.add(
            Limit("SECOND", LimitType.REQUEST, Duration.ofSeconds(1), 3)
        )
    }

    override fun currentPrice(pair: CurrencyPair, period: TimePeriod) {
        super.currentPrice(pair, period)
        val uri = "$URL_ENDPOINT${URL_CURRENT_AVG_PRICE.replace(pathId, pair.symbol)}?level=1"
        val entity = restCore.blockingStringRequest(uri, JSONObject::class)
        if (stateChecker.checkEmptyJson(entity, exId) || entity.operateError(pair)) return
        val bidPrice = entity.second.getJSONArray("bids").getJSONArray(0)[0].toString().toDouble()
        val asksPrice = entity.second.getJSONArray("asks").getJSONArray(0)[0].toString().toDouble()
        pair.price = (bidPrice + asksPrice) / 2
    }

    override fun priceHistory(pair: CurrencyPair, interval: String, limit: Int) {
        super.priceHistory(pair, interval, limit)
        val end = Instant.now().toString()
        val per = changePeriods.find { it.name == interval }!!.period
        val start = Instant.now().minus(per.multipliedBy(limit.toLong()))
        val uri = "$URL_ENDPOINT${URL_PRICE_CHANGE.replace(pathId, pair.symbol)}?start=$start&end=$end&granularity=${per.seconds}"
        try{
            val array = restCore.blockingStringRequest(uri, JSONArray::class)
            if (stateChecker.checkEmptyJson(array, exId) || array.operateError(pair)) return
            pair.priceHistory.clear()
            for (i in 0 until array.second.length()){ //fixme data is incomplete
                val arr = array.second.getJSONArray(i)
                pair.priceHistory.add((arr.getDouble(1) + arr.getDouble(2)) / 2)
            }
            logger.trace("price history updated on ${pair.symbol} pair $name exch")
        }catch (e: Exception){
            logger.error("Connect exception")
            logger.error(e)
        } //todo wrong operate
    }


    override fun updateSinglePriceChange(pair: CurrencyPair, period: TimePeriod) {
        val end = Instant.now().toString()
        val start = Instant.now().minus(period.period)
        val uri = "$URL_ENDPOINT${URL_PRICE_CHANGE.replace(pathId, pair.symbol)}?start=$start&end=$end&granularity=${period.period.seconds}"
        val array = restCore.blockingStringRequest(uri, JSONArray::class)
        logger.trace("Response of $uri \n$array")
        if (stateChecker.checkEmptyJson(array, exId) || array.operateError(pair)) return
        val arr = array.second.getJSONArray(0)
        val oldVal = (arr.getDouble(1) + arr.getDouble(2)) / 2
        writePriceChange(pair, period, oldVal)
    }

    override fun <T: Any> Pair<HttpStatus, T>.getError(): Int {
        logger.error("Request has error: $second")
        return when(first){
            HttpStatus.OK -> ClientCodes.SUCCESS
            HttpStatus.INTERNAL_SERVER_ERROR -> ClientCodes.EXCHANGE_NOT_ACCESSIBLE
            HttpStatus.BAD_REQUEST -> {
                logger.error("Bad Request")
                ClientCodes.TEMPORARY_UNAVAILABLE
            }
            HttpStatus.NOT_FOUND -> ClientCodes.EXCHANGE_NOT_FOUND
            else -> {
                logger.error("Unknown remote server error: $first")
                ClientCodes.TEMPORARY_UNAVAILABLE
            }
        }
    }

}