package ru.exrates.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.exrates.func.Aggregator
import java.net.ConnectException
import javax.servlet.http.HttpServletResponse

@RestController
class TestController(@Autowired val aggregator: Aggregator, @Autowired val objectMapper: ObjectMapper,
                     @Autowired val context: ConfigurableApplicationContext) {
    val logger: Logger = LogManager.getLogger(RestInfo::class)

    @GetMapping("/test/pair/history")
    fun history(@RequestParam c1: String, @RequestParam c2: String, @RequestParam exId: Int,
                @RequestParam historyinterval: String, @RequestParam limit: Int, response: HttpServletResponse
    ): List<Double>{
        logger.debug("REQUEST ON /test/pair/history: pair = $c1 - $c2, exchId = $exId, historyinterval = $historyinterval, limit = $limit")
        var list: List<Double> = listOf()
        try{
            throw ConnectException()
        } catch (e: ConnectException){
            logger.error("RESPONSE of /test/pair/history: Connection exception")
            response.status = 404
            //response.sendError(404)
            return listOf(400.0)
        }
        logger.debug("RESPONSE of /test/pair/history: ${objectMapper.writeValueAsString(list)}")
        return list
    }


}