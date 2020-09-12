package ru.exrates.entities.exchanges

import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.socket.client.StandardWebSocketClient
import org.springframework.web.reactive.socket.client.WebSocketClient
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod

class WSExchange: BasicExchange() {

    val handler: WebSocketClient = StandardWebSocketClient()






    fun fillTop(getArrayFunc: () -> Pair<HttpStatus, JSONArray>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun currentPrice(pair: CurrencyPair, period: TimePeriod) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun priceChange(pair: CurrencyPair, interval: TimePeriod) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}