package ru.exrates.repos

import ru.exrates.entities.TimePeriod
import java.time.Duration
import javax.persistence.AttributeConverter

class DurationConverter : AttributeConverter<Duration, Long> {
    override fun convertToDatabaseColumn(attribute: Duration) = attribute.toMillis()
    override fun convertToEntityAttribute(dbData: Long): Duration = Duration.ofMillis(dbData)
}

class TimePeriodConverter: AttributeConverter<TimePeriod, Long>{
    override fun convertToDatabaseColumn(attribute: TimePeriod) = attribute.period.toMillis()
    override fun convertToEntityAttribute(dbData: Long) = TimePeriod(Duration.ofMillis(dbData), "") //in future mb replace with duration and name (string with delimiter)
}