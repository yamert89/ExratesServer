package ru.exrates.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.func.Aggregator
import ru.exrates.utils.ExchangePayload



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

    @GetMapping("/rest/pair")
    fun pair(@RequestParam c1: String, @RequestParam c2: String): Map<String, CurrencyPair>{
        logger.debug("c1 = $c1, c2 = $c2")
        return aggregator.getCurStat(c1, c2)
    }

    @GetMapping("/rest/pair")
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
    "changePeriods":["3m","5m","15m","30m","1h","4h","6h","8h","12h","1d","3d","1w","1M"],
    "name":"binanceExchange",
    "pairs":[
        {
            "symbol":"ERDPAX",
            "price":0.0012527,
            "priceChange":{
                "\"4h\"":0.0012527,
                "\"6h\"":0.0012527,
                "\"30m\"":0.0012527,
                "\"1M\"":0.00177985,
                "\"1d\"":0.0012527,
                "\"1h\"":0.0012527,
                "\"12h\"":0.0012527,
                "\"3d\"":0.00141975,
                "\"8h\"":0.0012527,
                "\"5m\"":0.0012527,
                "\"1w\"":0.0012527,
                "\"15m\"":0.0012527,
                "\"3m\"":0.0012527},
                "priceHistory":[],
                "lastUse":"2019-12-05T13:08:21.932122600Z",
                "updateTimes":[1575551295166,0,0]}
             ]
     }

    */


}