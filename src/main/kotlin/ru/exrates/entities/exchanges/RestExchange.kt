package ru.exrates.entities.exchanges

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.LimitType
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.secondary.BanException
import ru.exrates.entities.exchanges.secondary.LimitExceededException
import java.net.ConnectException
import java.time.Duration
import javax.annotation.PostConstruct
import javax.persistence.DiscriminatorColumn
import javax.persistence.Entity
import javax.persistence.Inheritance
import javax.persistence.InheritanceType
import kotlin.reflect.KClass

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE) @DiscriminatorColumn(name = "EXCHANGE_TYPE")
@JsonIgnoreProperties("URL_ENDPOINT", "URL_CURRENT_AVG_PRICE", "URL_INFO", "URL_PRICE_CHANGE", "URL_PING", "URL_ORDER",
    "url_ENDPOINT", "url_CURRENT_AVG_PRICE", "url_INFO", "url_PRICE_CHANGE", "url_PING", "url_ORDER")
abstract class RestExchange : BasicExchange(){

    lateinit var URL_ENDPOINT: String
    lateinit var URL_CURRENT_AVG_PRICE: String
    lateinit var URL_INFO: String
    lateinit var URL_PRICE_CHANGE: String
    lateinit var URL_PING: String
    lateinit var URL_ORDER: String

    @PostConstruct
    override fun init() {

        if (id == 0 && !temporary) return
        logger.debug("Postconstuct concrete ${this::class.simpleName} id = $id" )

       /* if (!temporary) {
            //super.init()
            return
        }*/
        super.init()
        //todo needs exceptions?

    }

   protected fun initVars(){}

   protected fun limitsFill(entity: JSONObject){}

   protected fun pairsFill(entity: JSONObject, symbolsKey: String, baseCurKey: String, quoteCurKey: String, symbolKey: String){
       val symbols = entity.getJSONArray(symbolsKey)
       for(i in 0 until symbols.length()){
           //pairs.plus(CurrencyPair(symbols.getJSONObject(i).getString("symbol"), this))
           val baseCur = symbols.getJSONObject(i).getString(baseCurKey)
           val quoteCur = symbols.getJSONObject(i).getString(quoteCurKey)
           val symbol = symbols.getJSONObject(i).getString(symbolKey)
           pairs.add(CurrencyPair(baseCur, quoteCur, symbol, this))
       }
   }

    override fun task() {
        if(id == 0) {
            logger.debug("task aborted, id = 0")
            throw RuntimeException("interrupt task...")
        }
        logger.debug("task ping try...$URL_ENDPOINT$URL_PING")
        webClient.get().uri(URL_ENDPOINT + URL_PING).retrieve().onStatus(HttpStatus::isError){
            Mono.error(ConnectException("Ping $URL_PING failed"))
        }.bodyToMono(String::class.java).block()
        super.task()
    }

    fun <T: Any> request(uri: String, clazz: KClass<T>) : T{
        logger.trace("Try request to : $uri")
        val resp = webClient.get().uri(uri).retrieve()
            .onStatus(HttpStatus::is4xxClientError) { resp ->
                logger.trace("RESPONSE of $uri: ${resp}")
                val ex = when(resp.statusCode().value()){
                    banCode -> BanException()
                    limitCode -> LimitExceededException(LimitType.WEIGHT)
                    //null -> NullPointerException()
                    else -> IllegalStateException("Unexpected value: ${resp.statusCode().value()}")
                }
                Mono.error(ex) }

            .bodyToMono(clazz.java).block()!! //todo 1 - null compile notif? // 2 - todo operate exception
        logger.trace("Response of $uri : $resp")
        return resp
    }

    fun stringResponse(uri: String) = request(uri, String::class)

    override fun currentPrice(pair: CurrencyPair, timeout: Duration){
        if(!dataElasped(pair, timeout, 0)){
            logger.trace("current price $pair.symbol req skipped")
            return
        }
    }

    override fun priceChange(pair: CurrencyPair, timeout: Duration){
        if(!dataElasped(pair, timeout, 1)) {
            logger.trace("price change $pair req skipped")
            return
        }
    }

    abstract override fun priceHistory(pair: CurrencyPair, interval: String, limit: Int)

    override fun toString(): String {
        return "${this::class.simpleName} exId = $exId pairs: ${pairs.joinToString{it.symbol}}\n"
    }


}