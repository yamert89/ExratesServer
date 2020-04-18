package ru.exrates.entities.exchanges

import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.web.reactive.function.client.WebClient
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod
import java.time.Duration
import javax.annotation.PostConstruct
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity

@Entity
@DiscriminatorValue("p2pb2b")
class P2pb2bExchange: BasicExchange() {

    @PostConstruct
    override fun init() {
        if (id == 0 && !temporary) return
        exId = 2
        URL_ENDPOINT = "https://api.p2pb2b.io"
        URL_CURRENT_AVG_PRICE = "/api/v2/public/ticker"
        URL_INFO = "/api/v2/public/markets"
        URL_PRICE_CHANGE = "/api/v2/public/market/kline"
        URL_PING = "/api/v2/public/history"
        limitCode = 0
        banCode = 0
        historyPeriods = listOf()
        webClient = WebClient.create(URL_ENDPOINT)
        if (!temporary) {
            super.init()
            return
        }
        logger.debug("Postconstuct concrete ${this::class.simpleName} id = $id" )
        name = "p2pb2b"

        changePeriods.addAll(listOf(
            TimePeriod(Duration.ofMinutes(3), "1m"),
            TimePeriod(Duration.ofHours(1), "1h"),
            TimePeriod(Duration.ofDays(1), "1d")
        ))
        val entity = JSONObject(stringResponse(URL_ENDPOINT + URL_INFO))
        val symbols = entity.getJSONArray("result")
        for (i in 0 until symbols.length()){
            val baseCur = symbols.getJSONObject(i).getString("stock")
            val quoteCur = symbols.getJSONObject(i).getString("money")
            val symbol = symbols.getJSONObject(i).getString("name")
            pairs.add(CurrencyPair(baseCur, quoteCur, symbol, this))
        }

        temporary = false
        logger.debug("exchange " + name + " initialized with " + pairs.size + " pairs")

        //todo code duplicate


        super.init()
    }



    override fun currentPrice(pair: CurrencyPair, timeout: Duration) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun priceChange(pair: CurrencyPair, timeout: Duration) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun priceHistory(pair: CurrencyPair, interval: String, limit: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}