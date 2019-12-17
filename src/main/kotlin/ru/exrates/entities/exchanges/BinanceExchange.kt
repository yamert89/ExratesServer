package ru.exrates.entities.exchanges

import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.LimitType
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.secondary.Limit
import java.net.ConnectException
import java.time.Duration
import javax.annotation.PostConstruct
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity

@Entity @DiscriminatorValue("binance")
class BinanceExchange(): BasicExchange() {

    @PostConstruct
    override fun init() {
        if (id == 0 && !temporary) return
        URL_ENDPOINT = "https://api.binance.com"
        URL_CURRENT_AVG_PRICE = "/api/v3/avgPrice" //todo /api/v3/ticker/price ?
        URL_INFO = "/api/v1/exchangeInfo"
        URL_PRICE_CHANGE = "/api/v1/klines"
        URL_PING = "/api/v1/ping"
        URL_ORDER = "/api/v3/depth"
        limitCode = 429
        banCode = 418
        webClient = WebClient.create(URL_ENDPOINT)
        if(!temporary) {
            super.init()
            return
        }
        logger.debug("Postconstuct concrete ${this::class.simpleName} id = $id" )
        name = "binanceExchange"




        changePeriods.addAll(listOf(
            TimePeriod(Duration.ofMinutes(3), "3m"),
            TimePeriod(Duration.ofMinutes(5), "5m"),
            TimePeriod(Duration.ofMinutes(15), "15m"),
            TimePeriod(Duration.ofMinutes(30), "30m"),
            TimePeriod(Duration.ofHours(1), "1h"),
            TimePeriod(Duration.ofHours(4), "4h"),
            TimePeriod(Duration.ofHours(6), "6h"),
            TimePeriod(Duration.ofHours(8), "8h"),
            TimePeriod(Duration.ofHours(12), "12h"),
            TimePeriod(Duration.ofDays(1), "1d"),
            TimePeriod(Duration.ofDays(3), "3d"),
            TimePeriod(Duration.ofDays(7), "1w"),
            TimePeriod(Duration.ofDays(30), "1M"))
        )
        val entity = JSONObject(stringResponse(URL_ENDPOINT + URL_INFO))
        val array = entity.getJSONArray("rateLimits")
        limits.plus(
            Limit(
                "MINUTE",
                LimitType.WEIGHT,
                Duration.ofMinutes(1),
                1200
            )
        )
        for(i in 0 until array.length()){
            val ob = array.getJSONObject(i)
            limits.forEach {
                val name = ob.getString("interval")
                if(name == it.name) it.limitValue = ob.getInt("limit")
            }
        }
        val symbols = entity.getJSONArray("symbols")
        for(i in 0 until symbols.length()){
            pairs.plus(CurrencyPair(symbols.getJSONObject(i).getString("symbol"), this))
        }

        temporary = false
        logger.debug("exchange " + name + "initialized with " + pairs.size + " pairs")
        //todo needs exceptions?
        super.init()
    }

    override fun task() {
        if(id == 0) {
            logger.debug("task aborted, id = 0")
            return
        }
        logger.debug("task ping try...")
        webClient.get().uri(URL_ENDPOINT + URL_PING).retrieve().onStatus(HttpStatus::isError){
            Mono.error(ConnectException("Ping $URL_PING failed"))
        }.bodyToMono(String::class.java).block()
        super.task()
    }

    private fun stringResponse(uri: String) = super.request(uri, String::class)

    override fun currentPrice(pair: CurrencyPair, timeout: Duration) {
        if(!dataElasped(pair, timeout, 0)){
            logger.debug("current price $pair.symbol req skipped")
            return
        }
        val uri = URL_ENDPOINT + URL_CURRENT_AVG_PRICE + "?symbol=" + pair.symbol
        val entity = JSONObject(stringResponse(uri))
        val price = entity.getString("price").toDouble()
        pair.price = price
        logger.debug("Price updated on ${pair.symbol} pair | = $price")
    }

    override fun priceChange(pair: CurrencyPair, timeout: Duration) {
        if(!dataElasped(pair, timeout, 1)) {
            logger.debug("price change $pair req skipped")
            return
        }
        val symbol = "?symbol=" + pair.symbol
        val period = "&interval="
        changePeriods.forEach {
            val uri = URL_ENDPOINT + URL_PRICE_CHANGE + symbol + period + it.name + "&limit=1"
            val entity = JSONArray(stringResponse(uri))
            val array = entity.getJSONArray(0)
            val changeVol = (array.getDouble(2) + array.getDouble(3)) / 2
            pair.putInPriceChange(it, changeVol)
            logger.debug("Change period updated on ${pair.symbol} pair, interval = $it.name | change = $changeVol")
        }
    }

    override fun toString(): String {
        return this::class.simpleName + "  id=" + id
    }
}

/*public void priceChange (CurrencyPair pair, Duration timeout, Map<String, String> uriVariables) //todo limit > 1 logic
            throws JSONException, LimitExceededException, ErrorCodeException, BanException{
        if (!dataElapsed(pair, timeout, 1)) return;
        for (TimePeriod per : changePeriods) {
            var entity = new JSONArray(stringResponse(URL_PRICE_CHANGE));
            var array = entity.getJSONArray(0);
            pair.putInPriceChange(per, (array.getDouble(2) + array.getDouble(3)) / 2);
        }
    }*/