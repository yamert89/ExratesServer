package ru.exrates.entities.exchanges.secondary

import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONObject

interface JsonUnit
class ExRJsonObject: JSONObject, JsonUnit{
    constructor(json: String): super(json)
    constructor(): super()
}
class ExRJsonArray: JSONArray, JsonUnit{
    constructor(json: String): super(json)
    constructor() : super()
}
