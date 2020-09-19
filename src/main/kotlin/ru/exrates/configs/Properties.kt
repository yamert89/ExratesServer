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
    @Value("\${app.taskHandler.poolSize}")
    private lateinit var initPoolSize: String
    @Value("\${app.taskHandler.maxPollSize}")
    private lateinit var maxPoolSize: String
    @Value("\${app.version}")
    private lateinit var appVersion: String
    @Value("\${app.exchanges.skipTop}")
    private lateinit var skipTop: String
    @Value("\${app.exchanges.enableTask}")
    private lateinit var enableTask: String


    fun timerPeriod() = timerPeriod.toLong()
    fun maxSize() = maxSize.toInt()
    fun savingTimer() = savingTimer.toLong()
    fun isPersistenceStrategy() = persistenceSize.toBoolean()
    fun initPoolSize() = initPoolSize.toInt()
    fun maxPoolSize() = maxPoolSize.toInt()
    fun appVersion() = appVersion
    fun skipTop() = skipTop.toBoolean()
    fun taskIsEnabled() = enableTask.toBoolean()
}


