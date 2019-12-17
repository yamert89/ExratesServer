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
  "limitCode": 429,
  "name": "binanceExchange",
  "pairs": [
    {
      "symbol": "VENBTC",
      "price": 1.3928E-4,
      "priceChange": {
        "\"3d\"": 1.35645E-4,
        "\"4h\"": 1.35645E-4,
        "\"1h\"": 1.35645E-4,
        "\"5m\"": 1.35625E-4,
        "\"30m\"": 1.35645E-4,
        "\"15m\"": 1.35645E-4,
        "\"1d\"": 1.35645E-4,
        "\"1M\"": 1.35645E-4,
        "\"6h\"": 1.35645E-4,
        "\"12h\"": 1.35645E-4,
        "\"1w\"": 1.35645E-4,
        "\"8h\"": 1.35645E-4,
        "\"3m\"": 1.3559E-4
      },
      "priceHistory": [],
      "updateTimes": [
        1576605044157,
        1576605048739,
        0
      ]
    },
    {
      "symbol": "YOYOBNB",
      "price": 7.62E-4,
      "priceChange": {
        "\"3d\"": 7.8E-4,
        "\"4h\"": 7.62E-4,
        "\"1h\"": 7.62E-4,
        "\"5m\"": 7.62E-4,
        "\"30m\"": 7.62E-4,
        "\"15m\"": 7.62E-4,
        "\"1d\"": 7.8E-4,
        "\"1M\"": 8.515E-4,
        "\"6h\"": 7.685E-4,
        "\"12h\"": 7.685E-4,
        "\"1w\"": 7.79E-4,
        "\"8h\"": 7.62E-4,
        "\"3m\"": 7.62E-4
      },
      "priceHistory": [],
      "updateTimes": [
        1576605036127,
        1576605043358,
        0
      ]
    }
  ],
  "changePeriods": [
    "3m",
    "5m",
    "15m",
    "30m",
    "1h",
    "4h",
    "6h",
    "8h",
    "12h",
    "1d",
    "3d",
    "1w",
    "1M"
  ],
  "url_INFO": "/api/v1/exchangeInfo",
  "url_ORDER": "/api/v3/depth",
  "url_PING": "/api/v1/ping",
  "url_ENDPOINT": "https://api.binance.com",
  "url_CURRENT_AVG_PRICE": "/api/v3/avgPrice",
  "url_PRICE_CHANGE": "/api/v1/klines"
}





    */


}