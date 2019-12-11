package ru.exrates.configs

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.PropertySource
import org.springframework.stereotype.Component

@Component
@PropertySource("classpath:application.properties")
class Properties {
    @Value("\${app.timer}")
    private val timerPeriod //todo min period - premium function
            : String? = null
    @Value("\${app.pairs.size}")
    private val maxSize: String? = null
    @Value("\${app.pairs.strategy.persistent}")
    private val persistenceSize: String? = null
    @Value("\${app.save.timer}")
    private val savingTimer: String? = null

    fun getSavingTimer(): Int {
        return savingTimer!!.toInt()
    }

    fun getTimerPeriod(): Long {
        return timerPeriod!!.toLong()
    }

    fun getMaxSize(): Int {
        return maxSize!!.toInt()
    }

    val isPersistenceStrategy: Boolean
        get() = java.lang.Boolean.parseBoolean(persistenceSize)
}