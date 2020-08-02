package ru.exrates.entities.exchanges.rest

import org.springframework.beans.factory.getBean
import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.data.mapping.PreferredConstructor
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.func.RestCore
import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration
import java.time.Instant
import javax.annotation.PostConstruct

//https://docs.pro.coinbase.com/#get-trades
//76019C0m0YLw0511 - coin base
//fixme 3 request per second

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
        initVars()
        restCore = applicationContext.getBean(RestCore::class.java, URL_ENDPOINT, banCode, limitCode, serverError)
        val entity = restCore.blockingStringRequest(URL_ENDPOINT + URL_INFO, JSONArray::class)
        pairsFill(entity, "base_currency", "quote_currency", "id")
        temporary = false
        fillTop()
        logger.debug("exchange " + name + " initialized with " + pairs.size + " pairs")
    }

    override fun initVars() {
       exId = 3
        URL_ENDPOINT = "https://api.pro.coinbase.com"
        URL_CURRENT_AVG_PRICE = "/products/<product-id>/book"
        URL_INFO = "/products"
        URL_PRICE_CHANGE = "/products/<product-id>/candles"
        URL_PING = URL_ENDPOINT
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
        val list = pairs.map { it.symbol }
        /*val reqs = mutableMapOf<String, Mono<String>>()
        pairs.forEach {
            reqs[it.symbol] = restCore.stringRequest("$URL_ENDPOINT${URL_TOP_STATISTIC.replace(pathId, it.symbol)}")
        }
        val topSize = if(props.maxSize() < pairs.size) props.maxSize() else pairs.size
        topPairs.addAll(reqs.mapValues { JSONObject(it.value.block()).getDouble(TOP_COUNT_FIELD) }
            .entries.sortedByDescending { it.value }.map { it.key }.subList(0, topSize))*/
        val topSize = if(props.maxSize() < pairs.size) props.maxSize() else pairs.size

        val flux = restCore.patchStringRequests(pairs.map { "$URL_ENDPOINT${URL_TOP_STATISTIC.replace(pathId, it.symbol)}" })
        flux.subscribe()
        val vList = flux.collectList().block().map {JSONObject(it).getDouble(TOP_COUNT_FIELD)  }
        val map = mutableMapOf<String, Double>()
        for(i in list.indices){
            map[list[i]] = vList[i]
        }
       topPairs.addAll(map.entries.sortedByDescending { it.value }.map { it.key }.subList(0, topSize))

    }

    override fun currentPrice(pair: CurrencyPair, period: TimePeriod) {
        super.currentPrice(pair, period)
        val uri = "$URL_ENDPOINT${URL_CURRENT_AVG_PRICE.replace(pathId, pair.symbol)}?level=1"
        val entity = restCore.blockingStringRequest(uri, JSONObject::class)
        if (stateChecker.checkEmptyJson(entity, exId)) return
        val bidPrice = entity.getJSONArray("bids").getJSONArray(0)[0].toString().toDouble()
        val asksPrice = entity.getJSONArray("asks").getJSONArray(0)[0].toString().toDouble()
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
            if (stateChecker.checkEmptyJson(array, exId)) return

            pair.priceHistory.clear()
            for (i in 0 until limit){
                val arr = array.getJSONArray(i)
                pair.priceHistory.add((arr.getDouble(1) + arr.getDouble(2)) / 2)
            }
            logger.trace("price history updated on ${pair.symbol} pair $name exch")
        }catch (e: Exception){logger.error("Connect exception")} //todo wrong operate
    }


    override fun updateSinglePriceChange(pair: CurrencyPair, period: TimePeriod) {
        val end = Instant.now().toString()
        val start = Instant.now().minus(period.period)
        val uri = "$URL_ENDPOINT${URL_PRICE_CHANGE.replace(pathId, pair.symbol)}?start=$start&end=$end&granularity=${period.period.seconds}"
        val stringResponse = restCore.stringRequest(uri)
        val curMills = System.currentTimeMillis()
        val res = stringResponse.block()
        logger.trace("Response of $uri \n$res")
        val entity = JSONArray(res)
        if (stateChecker.checkEmptyJson(entity, exId)) return
        val arr = entity.getJSONArray(0)
        val oldVal = (arr.getDouble(1) + arr.getDouble(2)) / 2
        val changeVol = if (pair.price > oldVal) ((pair.price - oldVal) * 100) / pair.price else (((oldVal - pair.price) * 100) / oldVal) * -1 //fixme full logging
        logger.trace("single price change calculating: price: ${pair.price}, period: ${period.name}, oldVal: $oldVal, changeVol: $changeVol")
        pair.putInPriceChange(period, BigDecimal(changeVol, MathContext(2)).toDouble())
        logger.trace("Change period updated in ${System.currentTimeMillis() - curMills} ms on ${pair.symbol} pair $name exch, interval = ${period.name} | change = $changeVol")
    }


}