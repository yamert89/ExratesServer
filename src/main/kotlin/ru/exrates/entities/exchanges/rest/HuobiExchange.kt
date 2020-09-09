package ru.exrates.entities.exchanges.rest

import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.http.HttpStatus
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod
import java.time.Duration
import kotlin.IllegalStateException

class HuobiExchange: RestExchange() {


    override fun extractInfo() {
        val entity = restCore.blockingStringRequest(URL_ENDPOINT + URL_INFO, JSONObject::class)
        if (entity.hasErrors()) throw IllegalStateException("Failed info initialization")
        pairsFill(entity.second.getJSONArray("data"), "base-currency", "quote-currency", "symbol")
    }

    override fun initVars() {
        exId = 4
        name = "huobi"
        URL_ENDPOINT = "https://api.huobi.pro"
        URL_PING = "$URL_ENDPOINT/v2/market-status"
        URL_INFO = "/v1/common/symbols"
        URL_PRICE_CHANGE = "/market/history/kline"
        URL_CURRENT_AVG_PRICE = "/market/depth"
        /*
        * 1min, 5min, 15min, 30min, 60min, 4hour, 1day, 1mon, 1week, 1year*/
        changePeriods.addAll(listOf(
            TimePeriod(Duration.ofMinutes(1), "1min"),
            TimePeriod(Duration.ofMinutes(5), "5min"),
            TimePeriod(Duration.ofMinutes(15), "15min"),
            TimePeriod(Duration.ofMinutes(30), "30min"),
            TimePeriod(Duration.ofMinutes(60), "60min"),
            TimePeriod(Duration.ofHours(4), "4hour"),
            TimePeriod(Duration.ofDays(1), "1day"),
            TimePeriod(Duration.ofDays(30), "1mon"),
            TimePeriod(Duration.ofDays(7), "1week"),
            TimePeriod(Duration.ofDays(364), "1year")
        ))

        historyPeriods = changePeriods.map { it.name }
    }

    override fun currentPrice(pair: CurrencyPair, period: TimePeriod) {
        super.currentPrice(pair, period)
        val uri = "$URL_ENDPOINT$URL_CURRENT_AVG_PRICE?symbol=${pair.symbol}&type=step0&depth=5"
        val entity = restCore.blockingStringRequest(uri, JSONObject::class)
        if (failHandle(entity, pair)) return
        val tick = entity.second.getJSONObject("tick")
        val bids = tick.getJSONArray("bids")
        val asks = tick.getJSONArray("asks")
        var bPrice = 0.0
        for (i in 0 until bids.length()){
            bPrice += bids.getJSONArray(i).getDouble(0)
        }
        var askPrice = 0.0
        for(i in 0 until asks.length()){
            askPrice += asks.getJSONArray(i).getDouble(0)
        }
        pair.price = (bPrice / bids.length() + askPrice / asks.length()) / 2
    }

    override fun priceHistory(pair: CurrencyPair, interval: String, limit: Int) {
        super.priceHistory(pair, interval, limit)
        val uri = "$URL_ENDPOINT$URL_PRICE_CHANGE?symbol=${pair.symbol}&period=$interval&size=$limit"
        val entity = restCore.blockingStringRequest(uri, JSONObject::class)
        if (failHandle(entity, pair)) return
        pair.priceHistory.clear()
        val array = entity.second.getJSONArray("data")
        for (i in 0 until array.length()){
            val ob = array.getJSONObject(i)
            pair.priceHistory.add(((ob.getDouble("low") + ob.getDouble("high")) / 2))
        }
        logger.trace("price history updated on ${pair.symbol} pair $name exch")
    }


    override fun updateSinglePriceChange(pair: CurrencyPair, period: TimePeriod) {
        val uri = "$URL_ENDPOINT$URL_PRICE_CHANGE?symbol=${pair.symbol}&period=${period.name}&size=1"
        val entity = restCore.blockingStringRequest(uri, JSONObject::class)
        logger.trace("Response of $uri \n$entity")
        if (failHandle(entity, pair)) return
        val data = entity.second.getJSONArray("data").getJSONObject(0)
        //{"id":1598803200,"open":11615.88,"close":11740.01,"low":11570.34,"high":11776.53,"amount":35795.05511616849,"vol":4.1770683711697394E8,"count":370484}
        val oldVal = (data.getDouble("low") + data.getDouble("high")) / 2
        writePriceChange(pair, period, oldVal)
    }

    override fun <T : Any> Pair<HttpStatus, T>.getError(): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}