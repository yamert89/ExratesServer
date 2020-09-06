package ru.exrates.entities.exchanges.rest

import org.springframework.http.HttpStatus
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod
import java.time.Duration

class HuobiExchange: RestExchange() {

    override fun init(){
        super.init()
        if (!temporary){
            fillTop()
            return
        }
        initVars()
    }
    override fun initVars() {
        exId = 4
        name = "huobi"
        URL_ENDPOINT = "https://api.huobi.pro"
        URL_PING = "$URL_ENDPOINT/v2/market-status"
        URL_INFO = "/v1/common/symbols"
        /*
        * 1min, 5min, 15min, 30min, 60min, 4hour, 1day, 1mon, 1week, 1year*/
        changePeriods.addAll(listOf(
            TimePeriod(Duration.ofMinutes(1), "1min"),
            TimePeriod(Duration.ofMinutes(5), "5min"),
            TimePeriod(Duration.ofMinutes(15), "15min"),
            TimePeriod(Duration.ofMinutes(30), "30min"),
            TimePeriod(Duration.ofMinutes(60), "60min"),
            TimePeriod(Duration.ofHours(4), "4hour"),
            TimePeriod(Duration.ofDays(1), "1day"),
            TimePeriod(Duration.ofDays(30), "1mon"),
            TimePeriod(Duration.ofDays(7), "1week"),
            TimePeriod(Duration.ofDays(364), "1year")
        ))
    }

    override fun updateSinglePriceChange(pair: CurrencyPair, period: TimePeriod) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : Any> Pair<HttpStatus, T>.getError(): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}