package ru.exrates.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.userdetails.User
import org.springframework.web.bind.annotation.*
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.entities.exchanges.EmptyExchange
import ru.exrates.entities.exchanges.ExchangeDTO
import ru.exrates.entities.exchanges.secondary.ExchangeNamesObject
import ru.exrates.func.Aggregator
import ru.exrates.utils.ExchangePayload
import java.security.Principal
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession
import kotlin.Exception


@RestController
class RestInfo(@Autowired val aggregator: Aggregator, @Autowired val objectMapper: ObjectMapper,
               @Autowired val context: ConfigurableApplicationContext,
               val logger: Logger = LogManager.getLogger(RestInfo::class)) {

    /*
       {"exchange" : "binanceExchange", "timeout": "3m", "pairs" : ["btcusd", "etcbtc"]}
       {\"exchange\": \"binanceExchange\", \"timeout\" : 12, \"pairs\":[\"VENBTC\"]}
       //todo pairs - list of favorite pairs in bd for each user
    */

    @GetMapping("/ping")
    fun ping() = ""

    @PostMapping("/rest/exchange")
    fun getExchange(@RequestBody exchangePayload: ExchangePayload, response: HttpServletResponse, principal: Principal?, session: HttpSession): ExchangeDTO {
        logger.debug("principal is ${principal?.name}") //todo test
        val secContext = session.getAttribute("SPRING_SECURITY_CONTEXT") as SecurityContext?
        val login = (secContext?.authentication?.principal as User?)?.username
        logger.debug("session user name $login")
        logger.debug("REQUEST ON /rest/exchange: $exchangePayload")
        val ex = if(exchangePayload.pairs.isNotEmpty() && exchangePayload.timeout.isNotEmpty()){
            with(exchangePayload){
                val interval = if (timeout.isEmpty()) "1h" else timeout
                aggregator.getExchange(exId, pairs, interval)
            }
        } else aggregator.getExchange(exchangePayload.exId)
        try {
            logger.debug("RESPONSE of /rest/exchange: ${objectMapper.writeValueAsString(ex)}")
            logger.debug("RESPONSE Pairs of /rest/exchange: $ex")
            logger.debug("pairs: ${ex?.pairs?.joinToString()}")
        }catch (e: Exception){
            logger.error(e)
            logger.error(ex.toString())
        }

        if (ex == null) {
            response.status = 404 //todo test
            response.sendError(404, "Exchange not found")
            //response.sendRedirect("/error?message=Exchange not found")
            return ExchangeDTO(null)
        }
        return ex
    }

   /* @GetMapping("/rest/pair", params = ["c1", "c2"])
    fun pair(@RequestParam c1: String, @RequestParam c2: String):List<CurrencyPair>{
        logger.debug("c1 = $c1, c2 = $c2")
        return aggregator.getCurStat(c1, c2)
    }*/

    @GetMapping("/rest/pair"/*, params = ["pname", "historyinterval"]*/)
    fun pair(@RequestParam c1: String, @RequestParam c2: String, @RequestParam(required = false) historyInterval: String?, @RequestParam limit: Int): List<CurrencyPair>{
        logger.debug("REQUEST ON /rest/pair: c1 = $c1, c2 = $c2, historyInterval = $historyInterval, limit = $limit")
        val res = aggregator.getCurStat(c1, c2, historyInterval, limit)
        logger.debug("RESPONSE of /rest/pair: ${objectMapper.writeValueAsString(res)}")
        return res
    }

    @GetMapping("/rest/pair/history")
    fun history(@RequestParam c1: String, @RequestParam c2: String, @RequestParam exId: Int, @RequestParam historyinterval: String, @RequestParam limit: Int): List<Double>{
        logger.debug("REQUEST ON /rest/pair/history: pair = $c1 - $c2, exchId = $exId, historyinterval = $historyinterval, limit = $limit")
        val list = aggregator.priceHistory(c1, c2, exId, historyinterval, limit)
        logger.debug("RESPONSE of /rest/pair/history: ${objectMapper.writeValueAsString(list)}")
        return list
    }


    @GetMapping("/rest/lists")
    fun lists() : List<ExchangeNamesObject>{
        val res = aggregator.getNamesExchangesAndCurrencies()
        logger.debug("Lists response: ${objectMapper.writeValueAsString(res)}")
        return res
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