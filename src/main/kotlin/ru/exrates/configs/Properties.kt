package ru.exrates.configs

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.PropertySource
import org.springframework.stereotype.Component

@Component
@PropertySource("classpath:application.properties")
class Properties {
    @Value("\${app.timer}")
    private lateinit var timerPeriod: String //todo min period - premium function

    @Value("\${app.pairs.size}")
    private lateinit var maxSize: String
    @Value("\${app.pairs.strategy.persistent}")
    private lateinit var persistenceSize: String
    @Value("\${app.save.timer}")
    private lateinit var savingTimer: String

    fun timerPeriod() = timerPeriod.toLong()
    fun maxSize() = maxSize.toInt()
    fun savingTimer() = savingTimer.toInt()
    fun isPersistenceStrategy() = persistenceSize.toBoolean()
}


