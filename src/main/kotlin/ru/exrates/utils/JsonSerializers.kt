package ru.exrates.utils

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import ru.exrates.entities.TimePeriod
import java.io.StringWriter

class TimePeriodSerializer(val mapper: ObjectMapper): JsonSerializer<TimePeriod>() {
    override fun serialize(value: TimePeriod?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        val writer = StringWriter()
        mapper.writeValue(writer, value?.name)
        gen?.writeFieldName(writer.toString())
        writer.close()
    }
}

class TimePeriodListSerializer : JsonSerializer<List<TimePeriod>>() {
    override fun serialize(value: List<TimePeriod>?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        gen?.writeStartArray()
        value?.forEach { gen?.writeString(it.name) }
        gen?.writeEndArray()
    }
}
