package ru.exrates.repos;

import javax.persistence.AttributeConverter;
import java.time.Duration;

public class DurationConverter implements AttributeConverter<Duration, Long> {
    @Override
    public Long convertToDatabaseColumn(Duration attribute) {
        return attribute.toMillis();
    }

    @Override
    public Duration convertToEntityAttribute(Long dbData) {
        return Duration.ofMillis(dbData);
    }
}
