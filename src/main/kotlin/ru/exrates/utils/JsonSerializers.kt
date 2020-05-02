package ru.exrates.utils

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.BasicExchange
import java.io.StringWriter
import java.lang.IllegalArgumentException

class TimePeriodSerializer(private val mapper: ObjectMapper = ObjectMapper()): JsonSerializer<TimePeriod>() {
    override fun serialize(value: TimePeriod?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        gen?.writeFieldName(value?.name)
    }
}

class TimePeriodListSerializer : JsonSerializer<List<TimePeriod>>() {
    override fun serialize(value: List<TimePeriod>?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        if (value?.isEmpty() ?: throw NullPointerException("list Timeperiod is null in serializer")) {
            gen?.writeStartArray()
            gen?.writeEndArray()
            return
        }
        gen?.writeStartArray()
        value.forEach {
            gen?.writeString(it.name)
        }
        gen?.writeEndArray()
    }
}

class ExchangeSerializer: JsonSerializer<BasicExchange>(){
    override fun serialize(value: BasicExchange?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        gen!!.writeString(value!!.name)
    }

}

