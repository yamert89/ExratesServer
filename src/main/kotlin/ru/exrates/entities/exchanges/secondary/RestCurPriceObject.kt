package ru.exrates.entities.exchanges.secondary

import kotlin.reflect.KClass

class RestCurPriceObject(
    val uri: String,
    val jsonType: KClass<out JsonUnit>,
    private val func: (JsonUnit) -> Double){

    fun price(jsonUnit: JsonUnit): Double = func(jsonUnit)
}