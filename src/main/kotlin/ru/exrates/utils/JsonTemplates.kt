package ru.exrates.utils

data class ExchangePayload(val exId: Int, val interval: String, val pairs: Array<String>) {

}
/**
 * @param interval common interval for all pairs
 * @param values map of curs where key is name of pair and value is price changing
 * */
data class CursPeriod(val interval: String, val values: Map<String, Double>,  val status: Int = ClientCodes.SUCCESS)

class ErrorBody(val message: String)















