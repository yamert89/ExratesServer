package ru.exrates.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.userdetails.User
import org.springframework.web.bind.annotation.*
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.exchanges.ExchangeDTO
import ru.exrates.entities.exchanges.secondary.ExchangeNamesObject
import ru.exrates.func.Aggregator
import ru.exrates.utils.CursPeriod
import ru.exrates.utils.ExchangePayload
import java.net.ConnectException
import java.security.Principal
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession


@RestController
class RestInfo(@Autowired val aggregator: Aggregator, @Autowired val objectMapper: ObjectMapper,
               @Autowired val context: ConfigurableApplicationContext) {
    @Autowired
    lateinit var logger: Logger

       //todo pairs - list of favorite pairs in bd for each user


    @GetMapping("/ping")
    fun ping() = ""

    @PostMapping("/rest/exchange")
    fun getExchange(@RequestBody exchangePayload: ExchangePayload, response: HttpServletResponse, principal: Principal?, session: HttpSession): ExchangeDTO {
        logger.debug("principal is ${principal?.name}") //todo test
        val secContext = session.getAttribute("SPRING_SECURITY_CONTEXT") as SecurityContext?
        val login = (secContext?.authentication?.principal as User?)?.username
        logger.debug("session user name $login")
        logger.debug("REQUEST ON /rest/exchange: $exchangePayload")
        val ex = if(exchangePayload.pairs.isNotEmpty() && exchangePayload.interval.isNotEmpty()){
            with(exchangePayload){
                val interval = if (interval.isEmpty()) "1h" else interval
                aggregator.getExchange(exId, pairs, interval)
            }
        } else aggregator.getExchange(exchangePayload.exId)
        try {
            logger.debug("RESPONSE of /rest/exchange: ${objectMapper.writeValueAsString(ex)}")
            logger.debug("RESPONSE Pairs of /rest/exchange: $ex")
            logger.debug("pairs: ${ex.pairs.joinToString()}")
        }catch (e: Exception){
            logger.error(e)
            logger.error(ex.toString())
        }

        return ex
    }

    @PostMapping("/rest/dynamics")
    fun priceChange(@RequestBody curs: ExchangePayload): CursPeriod{
        logger.debug("REQUEST ON /rest/dynamics: $curs")
        val res = aggregator.getCursIntervalStatistic(curs)
        logger.debug("RESPONSE OF /rest/dynamics: $res")
        return res
    }

    @GetMapping("/rest/pair")
    fun pair(@RequestParam c1: String, @RequestParam c2: String, @RequestParam(required = false) historyInterval: String?, @RequestParam limit: Int): List<CurrencyPair>{
        logger.debug("REQUEST ON /rest/pair: c1 = $c1, c2 = $c2, historyInterval = $historyInterval, limit = $limit")
        val res = aggregator.getCurStat(c1, c2, historyInterval, limit)
        logger.debug("RESPONSE of /rest/pair: ${objectMapper.writeValueAsString(res)}")
        return res
    }

    @GetMapping("/rest/pair/single")
    fun onePair(@RequestParam c1: String, @RequestParam c2: String, @RequestParam exId: Int, @RequestParam currentInterval: String): CurrencyPair{
        logger.debug("REQUEST ON /rest/pair/single: pair = $c1 - $c2, exchId = $exId, currentInterval = $currentInterval")
        val res = aggregator.getOnePair(c1, c2, exId, currentInterval)
        logger.debug("RESPONSE of /rest/pair/single: ${objectMapper.writeValueAsString(res)}")
        return res
    }

    @GetMapping("/rest/pair/history")
    fun history(@RequestParam c1: String, @RequestParam c2: String, @RequestParam exId: Int, @RequestParam historyinterval: String, @RequestParam limit: Int): List<Double>{
        logger.debug("REQUEST ON /rest/pair/history: pair = $c1 - $c2, exchId = $exId, historyinterval = $historyinterval, limit = $limit")
        var list: List<Double>? = null
        try{
            list = aggregator.priceHistory(c1, c2, exId, historyinterval, limit)
        } catch (e: ConnectException){
            logger.error("RESPONSE of /rest/pair/history: Connection exception")
            return listOf(400.0)
        }
        logger.debug("RESPONSE of /rest/pair/history: ${objectMapper.writeValueAsString(list)}")
        return list
    }


    @GetMapping("/rest/lists")
    fun lists() : Map<Int, ExchangeNamesObject>{
        val res : Map<Int, ExchangeNamesObject> = aggregator.getNamesExchangesAndCurrencies()
        logger.debug("Lists response: ${objectMapper.writeValueAsString(res.values)}")
        return res
    }

    @GetMapping("/rest/checkmessages")
    fun checkMessages(@RequestParam versionToken: String, request: HttpServletRequest) = aggregator.checkMessages(versionToken, request.remoteAddr)

}