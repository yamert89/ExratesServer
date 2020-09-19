package ru.exrates.entities.exchanges.rest

import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.http.HttpStatus
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.LimitType
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.secondary.*
import ru.exrates.utils.ClientCodes
import java.time.Duration
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import kotlin.IllegalStateException

@Entity
@DiscriminatorValue("huobi")
class HuobiExchange: RestExchange() {


    override fun extractInfo() {
        val entity = restCore.blockingStringRequest(URL_ENDPOINT + URL_INFO, ExRJsonObject::class)
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
        URL_TOP_STATISTIC = "/market/tickers"
        TOP_SYMBOL_FIELD = "symbol"
        TOP_COUNT_FIELD = "count"
        taskTimeOut = TimePeriod(Duration.ofMinutes(1), "1m")

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

    override fun fillTop(getArrayFunc: () -> Pair<HttpStatus, JSONArray>) {
       super.fillTop{
          val resp = restCore.blockingStringRequest(URL_ENDPOINT + URL_TOP_STATISTIC, ExRJsonObject::class)
          resp.first to resp.second.getJSONArray("data")
       }

    }

    override fun limitsFill(entity: ExRJsonObject) {
        super.limitsFill(entity)
        limits.add(
            Limit("SECOND", LimitType.REQUEST, Duration.ofSeconds(1), 9)
        )
    }

    override fun CurrencyPair.currentPriceExt() = RestCurPriceObject(
        "$URL_ENDPOINT$URL_CURRENT_AVG_PRICE?symbol=${symbol}&type=step0&depth=5",
        ExRJsonObject::class
    ){jsonUnit ->
        jsonUnit as ExRJsonObject
        val tick = jsonUnit.getJSONObject("tick")
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
        (bPrice / bids.length() + askPrice / asks.length()) / 2
    }

    override fun CurrencyPair.historyExt(interval: String, limit: Int) = RestHistoryObject(
        "$URL_ENDPOINT$URL_PRICE_CHANGE?symbol=${symbol}&period=$interval&size=$limit",
        ExRJsonObject::class
    ){jsonUnit -> mutableListOf<Double>().apply {
        jsonUnit as ExRJsonObject
        val jsArray = jsonUnit.getJSONArray("data")
        for (i in 0 until jsArray.length()) {
            val ob = jsArray.getJSONObject(i)
            add((ob.getDouble("low") + ob.getDouble("high")) / 2)
        }
    }
    }

    override fun CurrencyPair.singlePriceChangeExt(period: TimePeriod) = RestCurPriceObject(
        "$URL_ENDPOINT$URL_PRICE_CHANGE?symbol=${symbol}&period=${period.name}&size=1",
        ExRJsonObject::class
    ){jsonUnit ->
        jsonUnit as ExRJsonObject
        val arr = jsonUnit.getJSONArray("data")
        if (arr.length() == 0) Double.MAX_VALUE
        else{
            val data = arr.getJSONObject(0)
            //{"id":1598803200,"open":11615.88,"close":11740.01,"low":11570.34,"high":11776.53,"amount":35795.05511616849,"vol":4.1770683711697394E8,"count":370484}
            (data.getDouble("low") + data.getDouble("high")) / 2
        }
    }

    override fun <T : Any> Pair<HttpStatus, T>.getError(): Int {
        if (second is JSONArray) return ClientCodes.SUCCESS
        val resp = second as JSONObject
        if (!resp.has("status") || resp.getString("status") == "ok") return ClientCodes.SUCCESS
        val errorCode = resp.getString("err-code") //fixme no value for err-code
        val errorMessage = resp.getString("err-msg")
        val invalidParameter = "invalid-parameter"

        return when{
            errorCode == invalidParameter ->
                when(errorMessage){
                    "invalid symbol" -> ClientCodes.CURRENCY_NOT_FOUND
                    else -> {
                        logger.error("Invalid huobi parameter: $errorMessage")
                        ClientCodes.TEMPORARY_UNAVAILABLE
                    }
                }
            else ->{
                logger.error("Huobi error: err-code: $errorCode, err-msg: $errorMessage")
                ClientCodes.TEMPORARY_UNAVAILABLE
            }
        }

    }
}