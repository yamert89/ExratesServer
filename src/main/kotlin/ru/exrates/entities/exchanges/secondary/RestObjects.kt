package ru.exrates.entities.exchanges.secondary

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createInstance

class RestCurPriceObject(
    val uri: String,
    val jsonType: KClass<out JsonUnit>,
    private val func: (JsonUnit) -> Double){

    fun price(jsonUnit: JsonUnit): Double = func(jsonUnit)



    inline fun <reified T: JsonUnit> jsonType(): KClass<T> = T::class
}

class RestHistoryObject(
    val uri: String,
    val jsonType: KClass<out JsonUnit>,
    private val func: (JsonUnit) -> List<Double>){

    fun history(jsonUnit: JsonUnit): List<Double> = func(jsonUnit)
}
