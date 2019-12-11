package ru.exrates.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import ru.exrates.entities.exchanges.Exchange
import ru.exrates.func.Aggregator
import ru.exrates.utils.JsonTemplates
import java.io.IOException


class RestInfo(@Autowired val aggregator: Aggregator, @Autowired val objectMapper: ObjectMapper,
               @Autowired val context: ConfigurableApplicationContext,
               val logger: Logger = LogManager.getLogger(RestInfo::class)) {

    /*
       {"exchange" : "binanceExchange", "timeout": "3m", "pairs" : ["btcusd", "etcbtc"]}
       //todo pairs - list of favorite pairs in bd for each user
    */

    @PostMapping("/rest/exchange")
    fun getExchange(@RequestBody exchangePayload: JsonTemplates.ExchangePayload): Exchange{
        logger.debug("payload = $exchangePayload")
        val ex = if(exchangePayload.pairs.size > 0)


    }
}