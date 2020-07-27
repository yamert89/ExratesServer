package ru.exrates.entities.exchanges.rest

import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONObject
import reactor.core.publisher.Mono
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.BasicExchange
import java.time.Duration
import javax.annotation.PostConstruct

//https://docs.pro.coinbase.com/#get-trades
//76019C0m0YLw0511 - coin base


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
        val entity = restCore.blockingStringRequest(URL_ENDPOINT + URL_INFO, JSONArray::class)
        pairsFill(entity, "base_currency", "quote_currency", "id", "-")
        temporary = false
        fillTop()
        logger.debug("exchange " + name + " initialized with " + pairs.size + " pairs")
    }

    override fun initVars() {
       exId = 3
        URL_ENDPOINT = "https://api.pro.coinbase.com"
        URL_CURRENT_AVG_PRICE = "/products/<product-id>/ticker"
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
        val reqs = mutableMapOf<String, Mono<String>>()
        pairs.forEach {
            reqs[it.symbol] = restCore.stringRequest("$URL_ENDPOINT${URL_TOP_STATISTIC.replace(pathId, it.symbol)}")
        }
        val topSize = if(props.maxSize() < pairs.size) props.maxSize() else pairs.size
        topPairs.addAll(reqs.mapValues { JSONObject(it.value.block()).getDouble(TOP_COUNT_FIELD) }
            .entries.sortedByDescending { it.value }.map { it.key }.subList(0, topSize))
    }

    override fun updateSinglePriceChange(pair: CurrencyPair, period: TimePeriod) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


}