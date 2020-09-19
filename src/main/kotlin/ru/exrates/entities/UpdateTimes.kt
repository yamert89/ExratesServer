package ru.exrates.entities

import java.time.Duration
import java.time.Instant
import javax.persistence.ElementCollection
import javax.persistence.Embeddable
import javax.persistence.FetchType
import javax.persistence.Transient

@Embeddable
class UpdateTimes(){
    var priceTime: Long = 0
    @ElementCollection(fetch = FetchType.EAGER)
    val priceChangeTimes: MutableMap<String, Long> = HashMap()

    @Transient
    lateinit var taskTimeout : TimePeriod

    constructor(taskTimeout: TimePeriod) : this(){
        this.taskTimeout = taskTimeout
    }

    fun priceTimeElapsed(): Boolean{
        return Instant.now().isAfter(Instant.ofEpochMilli(priceTime + taskTimeout.period.toMillis()))
    }

    fun priceChangeTimeElapsed(period: TimePeriod): Boolean{
        val old = priceChangeTimes[period.name] ?: return true
        val offset = when {
            period.period.toDays() > 0 -> Duration.ofHours(3)
            period.period.toHours() > 0 -> Duration.ofMinutes(10)
            else -> Duration.ZERO
        }
        return Instant.now().isAfter(Instant.ofEpochMilli( old + taskTimeout.period.toMillis() + offset.toMillis()))
    }

}