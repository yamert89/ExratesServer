package ru.exrates.utils

import com.sun.xml.fastinfoset.util.StringArray
import java.util.*

class ExchangePayload() {
    lateinit var exchange: String
    lateinit var timeout: String
    lateinit var pairs: Array<String>

    constructor(exchange: String, timeout: String, pairs: Array<String>): this() {
        this.exchange = exchange
        this.timeout = timeout
        this.pairs = pairs
    }
}















