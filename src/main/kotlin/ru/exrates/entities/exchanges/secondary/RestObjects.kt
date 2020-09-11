package ru.exrates.entities.exchanges.secondary

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createInstance

class RestCurPriceObject<T : JsonUnit>(
    val uri: String,
    val jsonType: KClass<T>,
    private val func: (T) -> Double){





    fun price(jsonUnit: T): Double = func(jsonUnit)



    inline fun <reified T: JsonUnit> jsonType(): KClass<T> = T::class
}

class RestHistoryObject<T : JsonUnit>(
    val uri: String,
    private val func: (T) -> List<Double>){

    fun history(jsonUnit: T): List<Double> = func(jsonUnit)
}
