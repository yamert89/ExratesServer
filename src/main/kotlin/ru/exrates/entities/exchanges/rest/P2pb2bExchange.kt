package ru.exrates.entities.exchanges.rest

import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.http.HttpStatus
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.secondary.ExRJsonObject
import ru.exrates.entities.exchanges.secondary.RestCurPriceObject
import ru.exrates.entities.exchanges.secondary.RestHistoryObject
import ru.exrates.utils.ClientCodes
import java.time.Duration
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity

@Entity
@DiscriminatorValue("p2pb2b")
class P2pb2bExchange: RestExchange() {

    /*
    * ******************************************************************************************************************
    *       Initialization
    * ******************************************************************************************************************
    * */


    override fun extractInfo() {
        val entity = restCore.blockingStringRequest(URL_ENDPOINT + URL_INFO, ExRJsonObject::class)
        if (entity.hasErrors()) throw IllegalStateException("Failed info initialization")
        pairsFill(entity.second.getJSONArray("result"), "stock", "money", "name")
    }

    override fun initVars() {
        exId = 2
        delimiter = "_"
        URL_ENDPOINT = "https://api.p2pb2b.io"
        URL_CURRENT_AVG_PRICE = "/api/v2/public/ticker"
        URL_INFO = "/api/v2/public/markets"
        URL_PRICE_CHANGE = "/api/v2/public/market/kline"
        URL_PING = "$URL_ENDPOINT/api/v2/public/ticker?market=ETH_BTC"
        URL_TOP_STATISTIC = ""
        TOP_COUNT_FIELD = ""
        TOP_SYMBOL_FIELD = ""
        limitCode = 0
        banCode = 0
        taskTimeOut = TimePeriod(Duration.ofMinutes(1), "1m")
        name = "p2pb2b"
        changePeriods.addAll(listOf(
            TimePeriod(Duration.ofMinutes(1), "1m"),
            TimePeriod(Duration.ofHours(1), "1h"),
            TimePeriod(Duration.ofDays(1), "1d")
        ))
        historyPeriods = changePeriods.map { it.name }
    }

    override fun fillTop(getArrayFunc: () -> Pair<HttpStatus, JSONArray>) {
        topPairs.addAll(listOf("ETH_BTC", "BNB_BTC", "DASH_BTC", "NEO_BTC", "BCH_BTC", "ETC_BTC", "BTG_BTC",
            "LTC_BTC", "XLM_BTC", "WAVES_BTC", "WTC_BTC", "GAS_BTC", "YAP_BTC", "DOGE_BTC", "ENJ_BTC", "HNC_BTC"))
    }



    /*
    * ******************************************************************************************************************
    *       Update methods
    * ******************************************************************************************************************
    * */


    override fun CurrencyPair.currentPriceExt() = RestCurPriceObject(
        "$URL_ENDPOINT$URL_CURRENT_AVG_PRICE?market=${symbol}",
        ExRJsonObject::class
    ){jsonUnit ->
        val result = (jsonUnit as ExRJsonObject).getJSONObject("result")
        val bid = result.getDouble("bid")
        val ask = result.getDouble("ask")
        (ask + bid) / 2
    }

    override fun CurrencyPair.historyExt(interval: String, limit: Int) = RestHistoryObject(
        {
            val lim = if (limit < 50) 50 else limit
            "$URL_ENDPOINT$URL_PRICE_CHANGE?market=${symbol}&interval=$interval&limit=$lim"
        }(),
        ExRJsonObject::class
    ){jsonUnit -> mutableListOf<Double>().apply{
        val array = (jsonUnit as ExRJsonObject).getJSONArray("result")
        logger.debug("P2pArray: $array")
        if (array.length() == 0) {
            logger.warn("Price history result array is empty")
        } else {
            for (i in 0 until array.length()) {
                val arr = array.getJSONArray(i)
                add((arr.getDouble(1) + arr.getDouble(2)) / 2)
            }
        }
    } }

    /*
    * ******************************************************************************************************************
    *       Super class methods
    * ******************************************************************************************************************
    * */

    override fun CurrencyPair.singlePriceChangeExt(period: TimePeriod) = RestCurPriceObject(
        "$URL_ENDPOINT$URL_PRICE_CHANGE?market=${symbol}&interval=${period.name}&limit=50",
        ExRJsonObject::class
    ){jsonUnit ->
        val ob = jsonUnit as ExRJsonObject
        val array = jsonUnit.getJSONArray("result")
        if(array.length() == 0){
            this.putInPriceChange(period, Double.MAX_VALUE)
            Double.MAX_VALUE
        } else{
            val array2 = array.getJSONArray(0)
            (array2.getDouble(1) + array2.getDouble(2)) / 2
        }
    }

    override fun <T: Any> Pair<HttpStatus, T>.getError(): Int {
        return when{
            first == HttpStatus.OK || second is JSONArray -> ClientCodes.SUCCESS
            first == HttpStatus.INTERNAL_SERVER_ERROR -> ClientCodes.EXCHANGE_NOT_ACCESSIBLE
            (second as JSONObject).getInt("errorCode") == 2021 || (second as JSONObject).getInt("errorCode") == 2020 -> ClientCodes.CURRENCY_NOT_FOUND
            else -> throw IllegalStateException("Unexpected response code of json: ${this.second}")
        }
    }


}