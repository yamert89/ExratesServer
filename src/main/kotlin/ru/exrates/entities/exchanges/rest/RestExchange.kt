package ru.exrates.entities.exchanges.rest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONObject
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.func.RestCore
import java.util.*
import javax.annotation.PostConstruct
import javax.persistence.DiscriminatorColumn
import javax.persistence.Entity
import javax.persistence.Inheritance
import javax.persistence.InheritanceType

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE) @DiscriminatorColumn(name = "EXCHANGE_TYPE")
@JsonIgnoreProperties("URL_ENDPOINT", "URL_CURRENT_AVG_PRICE", "URL_INFO", "URL_PRICE_CHANGE", "URL_PING", "URL_ORDER",
    "url_ENDPOINT", "url_CURRENT_AVG_PRICE", "url_INFO", "url_PRICE_CHANGE", "url_PING", "url_ORDER", "restCore")
abstract class RestExchange : BasicExchange(){

    lateinit var URL_ENDPOINT: String
    lateinit var URL_CURRENT_AVG_PRICE: String
    lateinit var URL_INFO: String
    lateinit var URL_PRICE_CHANGE: String
    lateinit var URL_PING: String
    lateinit var URL_ORDER: String
    lateinit var URL_TOP_STATISTIC: String
    lateinit var TOP_COUNT_FIELD: String
    lateinit var TOP_SYMBOL_FIELD: String
    @Transient
    lateinit var restCore: RestCore

    /*
    * ******************************************************************************************************************
    *       Initialization
    * ******************************************************************************************************************
    * */

    @PostConstruct
    override fun init() {

        if (id == 0 && !temporary) return
        logger.debug("Postconstuct concrete ${this::class.simpleName} id = $id" )
        super.init()

        //todo needs exceptions?

    }

    override fun fillTop() {
        val pairs = restCore.blockingStringRequest(URL_TOP_STATISTIC, JSONArray::class)
        if (pairs.length() == 0) return
        val all = HashMap<String, Int>()
        val topSize = if(props.maxSize() < pairs.length()) props.maxSize() else pairs.length()
        var count = 0
        var pairName = ""
        var jsonObject: JSONObject
        for (i in 0 until pairs.length()){
            jsonObject = pairs.getJSONObject(i)
            count = jsonObject.getInt(TOP_COUNT_FIELD)
            pairName = jsonObject.getString(TOP_SYMBOL_FIELD)
            all[pairName] = count
        }
        topPairs.addAll(all.entries.sortedByDescending { it.value }.map { it.key }.subList(0, topSize))
    }

    abstract fun initVars()

    protected fun limitsFill(entity: JSONObject){}

    protected fun pairsFill(symbols: JSONArray, baseCurKey: String, quoteCurKey: String, symbolKey: String, delimiterForRemoving: String = ""){
       for(i in 0 until symbols.length()){
           val baseCur = symbols.getJSONObject(i).getString(baseCurKey)
           val quoteCur = symbols.getJSONObject(i).getString(quoteCurKey)
           var symbol = symbols.getJSONObject(i).getString(symbolKey)
           if(delimiterForRemoving.isNotEmpty()) symbol = symbol.replace(delimiterForRemoving, "")
           pairs.add(CurrencyPair(baseCur, quoteCur, symbol, this))
       }
    }





    override fun task() {
        if(id == 0) {
            return
        }
        logger.debug("task ping try...$URL_PING")
        restCore.stringRequest(URL_PING).block()
        super.task()
    }

    /*
    * ******************************************************************************************************************
    *       Update methods
    * ******************************************************************************************************************
    * */

    override fun currentPrice(pair: CurrencyPair, period: TimePeriod){
        if(!pair.updateTimes.priceTimeElapsed()){
            logger.trace("current price $pair.symbol req skipped")
            return
        }
    }

    override fun priceChange(pair: CurrencyPair, interval: TimePeriod){
        if(!pair.updateTimes.priceChangeTimeElapsed(interval)) {
            logger.trace("price change $pair req skipped")
            return
        }
        val debMills = System.currentTimeMillis()
        runBlocking {
            val job = launch{
                changePeriods.forEach {
                    launch(taskHandler.getExecutorContext()){
                        //val mono = singlePriceChangeRequest(pair, it)
                        updateSinglePriceChange(pair, it)
                    }
                }
            }
            job.join()
        }

        logger.debug("price change ends with ${System.currentTimeMillis() - debMills}")
    }

    /*
    * ******************************************************************************************************************
    *       Class methods
    * ******************************************************************************************************************
    * */


    abstract fun updateSinglePriceChange(pair: CurrencyPair, period: TimePeriod)


    override fun toString(): String {
        return "${this::class.simpleName} exId = $exId pairs: ${pairs.joinToString{it.symbol}}\n"
    }


}