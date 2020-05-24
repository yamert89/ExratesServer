package ru.exrates.entities

import java.time.Instant
import javax.persistence.ElementCollection
import javax.persistence.Embeddable

@Embeddable
class UpdateTimes(){
    var priceTime: Long = 0
    @ElementCollection val priceChangeTimes: MutableMap<String, Long> = HashMap()

    fun priceTimeElapsed(period: TimePeriod): Boolean{
        return Instant.now().isAfter(Instant.ofEpochMilli(priceTime + period.period.toMillis()))
    }

    fun priceChangeTimeElapsed(period: TimePeriod): Boolean{
        val current = priceChangeTimes[period.name] ?: return true
        return Instant.now().isAfter(Instant.ofEpochMilli( current + period.period.toMillis()))
    }

}