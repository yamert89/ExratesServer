package ru.exrates.func

import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Service
class EndpointStateChecker {

    private val counter = ConcurrentHashMap<Int, AtomicInteger>()
    private val nonAccessible = ConcurrentSkipListSet<Int>()

    fun <T: Any> checkEmptyJson(jsonObject: T, exId: Int): Boolean{
        val res: Boolean
        when(jsonObject){
            is JSONObject -> res = jsonObject.length() == 0
            is JSONArray -> res = jsonObject.length() == 0
            else -> throw IllegalArgumentException("unsupported json type")
        }

        if (res) {
            if (!counter.containsKey(exId)) counter[exId] = AtomicInteger(0)
                counter[exId]?.set(counter[exId]?.incrementAndGet() ?: 0)
            if (counter[exId]!!.get() > 5) {
                nonAccessible.add(exId)
                counter[exId]!!.set(0)
            }
        } else {
            counter[exId]?.set(0)
            nonAccessible.remove(exId)
        }

        return res
    }

    fun accessible(exId: Int) = !nonAccessible.contains(exId)
}