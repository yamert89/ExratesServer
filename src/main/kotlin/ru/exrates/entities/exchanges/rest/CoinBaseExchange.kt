package ru.exrates.entities.exchanges.rest

import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.util.Base64Utils
import org.springframework.util.LinkedMultiValueMap
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.LimitType
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.secondary.ExRJsonArray
import ru.exrates.entities.exchanges.secondary.ExRJsonObject
import ru.exrates.entities.exchanges.secondary.Limit
import ru.exrates.entities.exchanges.secondary.RestCurPriceObject
import ru.exrates.utils.ClientCodes
import java.time.Duration
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.xml.crypto.dsig.Transform

//https://docs.pro.coinbase.com/#get-trades
//76019C0m0YLw0511 - coin base
//aNkU6H4acoGiEEPf
//8ReDUltcDsoYO1zExv1xP7N3dJE6PmIh

/*
* jqKBHoPTJhQW4cgW
* 5tHxAgfF4jc9nDBt7LyOWFPcq91shidF*/
//fixme 429
@Entity
@DiscriminatorValue("coinbase")
class CoinBaseExchange: RestExchange() {
    private val pathId = "<product-id>"

    /*
   * ******************************************************************************************************************
   *       Initialization
   * ******************************************************************************************************************
   * */


    override fun extractInfo() {
        val entity = restCore.blockingStringRequest(URL_ENDPOINT + URL_INFO, ExRJsonArray::class, generateHeaders(URL_ENDPOINT + URL_INFO))
        if (entity.hasErrors()) throw IllegalStateException("Failed info initialization")
        pairsFill(entity.second, "base_currency", "quote_currency", "id")
        limitsFill(ExRJsonObject())
    }

    override fun initVars() {
       exId = 3
        URL_ENDPOINT = "https://api.pro.coinbase.com"
        URL_PING = "$URL_ENDPOINT/time"
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
        topPairs.addAll(reqs.mapValues { ExRJsonObject(it.value.block()).getDouble(TOP_COUNT_FIELD) }
            .entries.sortedByDescending { it.value }.map { it.key }.subList(0, topSize))*/
        val topSize = if(props.maxSize() < pairs.size) props.maxSize() else pairs.size
        val urls = pairs.map { "$URL_ENDPOINT${URL_TOP_STATISTIC.replace(pathId, it.symbol)}" }

        val flux = restCore.patchStringRequests(urls, Duration.ofMillis(requestDelay()))
        flux.subscribe()
        val vList = flux.collectList().block().map { ExRJsonObject(it).getDouble(TOP_COUNT_FIELD)  }
        val map = mutableMapOf<String, Double>()
        for(i in list.indices){
            map[list[i]] = vList[i]
        }
       topPairs.addAll(map.entries.sortedByDescending { it.value }.map { it.key }.subList(0, topSize))

    }

    override fun limitsFill(entity: ExRJsonObject) {
        super.limitsFill(entity)
        limits.add(
            Limit("SECOND", LimitType.REQUEST, Duration.ofSeconds(1), 1)
        )
    }


    override fun CurrencyPair.currentPriceExt() = RestCurPriceObject<ExRJsonObject>(
        "$URL_ENDPOINT${URL_CURRENT_AVG_PRICE.replace(pathId, symbol)}?level=1"
    ){jsonUnit ->
        val bidPrice = jsonUnit.getJSONArray("bids").getJSONArray(0)[0].toString().toDouble()
        val asksPrice =jsonUnit.getJSONArray("asks").getJSONArray(0)[0].toString().toDouble()
        (bidPrice + asksPrice) / 2
    }

    override fun priceHistory(pair: CurrencyPair, interval: String, limit: Int) {
        super.priceHistory(pair, interval, limit)
        logger.debug("update price history for $interval ${pair.symbol}")
        val end = Instant.now().toString()
        val per = changePeriods.find { it.name == interval }!!.period
        val start = Instant.now().minus(per.multipliedBy(limit.toLong()))
        val uri = "$URL_ENDPOINT${URL_PRICE_CHANGE.replace(pathId, pair.symbol)}?start=$start&end=$end&granularity=${per.seconds}"
        try{
            val array = restCore.blockingStringRequest(uri, ExRJsonArray::class, generateHeaders(uri))
            if (failHandle(array, pair)) return
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

    override fun CurrencyPair.singlePriceChangeExt(period: TimePeriod) = RestCurPriceObject<ExRJsonArray>(
        {
            val end = Instant.now().toString()
            val start = Instant.now().minus(period.period)
            "$URL_ENDPOINT${URL_PRICE_CHANGE.replace(pathId, symbol)}?start=$start&end=$end&granularity=${period.period.seconds}"
        }()
    ){jsonUnit ->
        val arr = jsonUnit.getJSONArray(0)
        (arr.getDouble(1) + arr.getDouble(2)) / 2
    }

    override fun <T: Any> Pair<HttpStatus, T>.getError(): Int {
        return when(first){
            HttpStatus.OK -> ClientCodes.SUCCESS
            HttpStatus.INTERNAL_SERVER_ERROR -> ClientCodes.EXCHANGE_NOT_ACCESSIBLE
            HttpStatus.BAD_REQUEST -> {
                logger.error("Bad Request")
                ClientCodes.SUCCESS//fixme
            }
            HttpStatus.NOT_FOUND -> ClientCodes.EXCHANGE_NOT_FOUND
            HttpStatus.TOO_MANY_REQUESTS -> ClientCodes.TEMPORARY_UNAVAILABLE
            else -> {
                logger.error("Unknown remote server error: $first")
                ClientCodes.TEMPORARY_UNAVAILABLE
            }
        }
    }

    private fun generateHeaders(uri: String): HttpHeaders{
        return HttpHeaders(LinkedMultiValueMap<String, String>().apply {
            //timestamp + method + requestPath + body;
            val timestamp = Instant.now().toEpochMilli().toString()
            this["CB-ACCESS-KEY"] = "aNkU6H4acoGiEEPf"
            this["CB-ACCESS-TIMESTAMP"] = timestamp
            val secret = "8ReDUltcDsoYO1zExv1xP7N3dJE6PmIh"
            val key = Base64Utils.decode(secret.toByteArray())
            val what = "${timestamp}GET$uri"
            val mac = Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(key, Transform.BASE64))
                update(what.toByteArray())
                doFinal()
            }
            this["CB-ACCESS-SIGN"] = Base64Utils.encodeToString(mac)
            //this["CB-ACCESS-PASSPHRASE"] = ""
        } )
    }

}