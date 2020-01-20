package ru.exrates.controllers

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import ru.exrates.utils.ErrorBody

class Utils {

    @GetMapping("/error")
    @ResponseBody
    fun error(@RequestParam message: String): ErrorBody{
        return ErrorBody(message)
    }
}