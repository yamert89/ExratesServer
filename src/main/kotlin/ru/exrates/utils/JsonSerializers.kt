package ru.exrates.utils

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.entities.exchanges.Exchange
import java.io.StringWriter

class TimePeriodSerializer(private val mapper: ObjectMapper = ObjectMapper()): JsonSerializer<TimePeriod>() {
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

class ExchangeSerializer(private val mapper: ObjectMapper = ObjectMapper()): JsonSerializer<BasicExchange>(){
    override fun serialize(value: BasicExchange?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        gen!!.writeString(value!!.name)
    }

}

