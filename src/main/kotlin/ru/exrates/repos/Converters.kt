package ru.exrates.repos

import java.time.Duration
import javax.persistence.AttributeConverter

class DurationConverter : AttributeConverter<Duration, Long> {
    override fun convertToDatabaseColumn(attribute: Duration) = attribute.toMillis()
    override fun convertToEntityAttribute(dbData: Long): Duration = Duration.ofMillis(dbData)
}

