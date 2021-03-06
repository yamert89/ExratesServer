package ru.exrates.func

import ru.exrates.entities.CurrencyPair
import java.util.*

fun SortedSet<CurrencyPair>.containsPair(pair: CurrencyPair): Boolean{
    return this.any { it.symbol == pair.symbol }
}