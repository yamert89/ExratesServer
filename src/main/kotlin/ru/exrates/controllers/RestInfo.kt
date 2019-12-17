package ru.exrates.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.web.bind.annotation.*
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.func.Aggregator
import ru.exrates.utils.ExchangePayload


@RestController
class RestInfo(@Autowired val aggregator: Aggregator, @Autowired val objectMapper: ObjectMapper,
               @Autowired val context: ConfigurableApplicationContext,
               val logger: Logger = LogManager.getLogger(RestInfo::class)) {

    /*
       {"exchange" : "binanceExchange", "timeout": "3m", "pairs" : ["btcusd", "etcbtc"]}
       //todo pairs - list of favorite pairs in bd for each user
    */

    @PostMapping("/rest/exchange")
    fun getExchange(@RequestBody exchangePayload: ExchangePayload): BasicExchange {
        logger.debug("payload = $exchangePayload")
        val ex = if(exchangePayload.pairs.size > 0){
            with(exchangePayload){
                aggregator.getExchange(exchange, pairs, timeout)
            }
        } else aggregator.getExchange(exchangePayload.exchange)
        logger.debug("Exchange response: ${objectMapper.writeValueAsString(ex)}")
        return ex!! //todo if null req to client?
    }

    @GetMapping("/rest/pair", params = ["c1", "c2"])
    fun pair(@RequestParam c1: String, @RequestParam c2: String): Map<String, CurrencyPair>{
        logger.debug("c1 = $c1, c2 = $c2")
        return aggregator.getCurStat(c1, c2)
    }

    @GetMapping("/rest/pair", params = ["pname"])
    fun pair(@RequestParam pname: String): Map<String, CurrencyPair>{
        logger.debug("pname = $pname")
        return aggregator.getCurStat(pname)
    }

    @GetMapping("/service/save")
    fun save() = aggregator.save()

    @GetMapping("/close")
    fun close() = context.close()

    /*
    Request:
    {
    "exchange" : "binanceExchange",
    "timeout": "3m",
    "pairs" : ["BTCUSDT", "ETCBTC"]
    }



    Response:
    {
    {"limitCode":429,"props":{"persistenceStrategy":true},"URL_ENDPOINT":"https://api.binance.com","URL_CURRENT_AVG_PRICE":"/api/v3/avgPrice","URL_INFO":"/api/v1/exchangeInfo","URL_PRICE_CHANGE":"/api/v1/klines","URL_PING":"/api/v1/ping","URL_ORDER":"/api/v3/depth","name":"binanceExchange","pairs":[{"symbol":"BTCUSDT","price":6738.87880221,"priceChange":{"\"3d\"":6813.605,"\"1h\"":6730.0,"\"1d\"":6813.605,"\"12h\"":6812.92,"\"6h\"":6812.92,"\"8h\"":6813.605,"\"4h\"":6812.92,"\"5m\"":6745.049999999999,"\"30m\"":6730.0,"\"1M\"":7217.5,"\"3m\"":6739.844999999999,"\"15m\"":6716.764999999999,"\"1w\"":6917.5},"priceHistory":[],"updateTimes":[1576592788162,1576592795760,0]},{"symbol":"ETCBTC","price":5.3036E-4,"priceChange":{"\"3d\"":5.3315E-4,"\"1h\"":5.288000000000001E-4,"\"1d\"":5.3115E-4,"\"12h\"":5.298E-4,"\"6h\"":5.298E-4,"\"8h\"":5.31E-4,"\"4h\"":5.298E-4,"\"5m\"":5.308E-4,"\"30m\"":5.288000000000001E-4,"\"1M\"":5.2155E-4,"\"3m\"":5.304999999999999E-4,"\"15m\"":5.286E-4,"\"1w\"":5.3315E-4},"priceHistory":[],"updateTimes":[1576592796213,1576592801870,0]}],"changePeriods":["3m","5m","15m","30m","1h","4h","6h","8h","12h","1d","3d","1w","1M"],"url_INFO":"/api/v1/exchangeInfo","url_PING":"/api/v1/ping","url_ORDER":"/api/v3/depth","url_ENDPOINT":"https://api.binance.com","url_CURRENT_AVG_PRICE":"/api/v3/avgPrice","url_PRICE_CHANGE":"/api/v1/klines"}
0


    */


}