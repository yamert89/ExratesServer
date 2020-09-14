package ru.exrates.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import ru.exrates.func.Aggregator
import ru.exrates.utils.ErrorBody

@RestController
class Service(@Autowired val aggregator: Aggregator, @Autowired val context: ConfigurableApplicationContext) {

    @GetMapping("/error")
    @ResponseBody
    fun error(@RequestParam message: String): ErrorBody{
        return ErrorBody(message)
    }

    @GetMapping("/service/save")
    fun save() = aggregator.save()

    @GetMapping("/close")
    fun close() = context.close()

    @GetMapping("/service/disable")
    fun disableExchange(@RequestParam exname: String) = aggregator.disableExchange(exname)

    @GetMapping("/service/enable")
    fun enableExchange(@RequestParam exname: String) = aggregator.enableExchange(exname)


}