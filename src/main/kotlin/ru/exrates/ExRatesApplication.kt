package ru.exrates

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
object ExRatesApplication {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            println("start")
            SpringApplication.run(ExRatesApplication::class.java, *args)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}