package ru.exrates.entities.exchanges.rest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.LimitType
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.entities.exchanges.secondary.*
import ru.exrates.func.RestCore
import ru.exrates.utils.ClientCodes
import java.math.BigDecimal
import java.math.MathContext
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
    lateinit var headers: HttpHeaders
    @Transient
    @Autowired
    lateinit var restCore: RestCore

    /*
    * ******************************************************************************************************************
    *       Initialization
    * ******************************************************************************************************************
    * */

    @PostConstruct
    override fun init() {
        if (id == 0 && !temporary) return
        if (!temporary){
            fillTop()
            super.init()
            return
        }
        initVars()
        extractInfo()
        temporary = false
        fillTop()
        logger.debug("exchange " + name + " initialized with " + pairs.size + " pairs")
        super.init()
    }


    fun fillTop(getArrayFunc: () -> Pair<HttpStatus, JSONArray> = {HttpStatus.I_AM_A_TEAPOT to  ExRJsonArray()}) {
        if (props.skipTop()) return
        val pairs = getArrayFunc()//restCore.blockingStringRequest(URL_ENDPOINT + URL_TOP_STATISTIC, ExRJsonArray::class)
        if (pairs.hasErrors()) throw IllegalStateException("Failed fill top (base in rest) from $URL_ENDPOINT")
        if (pairs.second.length() == 0) return
        val all = HashMap<String, Int>()
        val topSize = if(props.maxSize() < pairs.second.length()) props.maxSize() else pairs.second.length()
        var count = 0
        var pairName = ""
        var jsonObject: JSONObject
        for (i in 0 until pairs.second.length()){
            jsonObject = pairs.second.getJSONObject(i)
            count = jsonObject.getInt(TOP_COUNT_FIELD)
            pairName = jsonObject.getString(TOP_SYMBOL_FIELD)
            all[pairName] = count
        }
        topPairs.addAll(all.entries.sortedByDescending { it.value }.map { it.key }.subList(0, topSize))
        logger.debug("Top pairs in $name : ${topPairs.joinToString()}")
    }

    abstract fun initVars()

    protected fun limitsFill(entity: ExRJsonObject){}

    abstract fun extractInfo()

    protected fun pairsFill(symbols: JSONArray, baseCurKey: String, quoteCurKey: String, symbolKey: String){
       for(i in 0 until symbols.length()){
           val baseCur = symbols.getJSONObject(i).getString(baseCurKey)
           val quoteCur = symbols.getJSONObject(i).getString(quoteCurKey)
           var symbol = symbols.getJSONObject(i).getString(symbolKey)
           pairs.add(CurrencyPair(baseCur, quoteCur, symbol, this))
       }
    }





    override fun task() {
        if(id == 0) {
            logger.debug("id = 0, task aborted")
            return
        }
        logger.debug("task ping try...$URL_PING")
        val resp = restCore.blockingStringRequest(URL_PING, ExRJsonObject::class)
        if (resp.hasErrors()) throw IllegalStateException("Failed ping $URL_ENDPOINT, resp: ${resp.second}")
        super.task()
    }

    /*
    * ******************************************************************************************************************
    *       Update methods
    * ******************************************************************************************************************
    * */

    final override fun currentPrice(pair: CurrencyPair, period: TimePeriod){
        if(!pair.updateTimes.priceTimeElapsed()){
            logger.debug("current price $pair.symbol req skipped")
            return
        }
        val ob = pair.currentPriceExt()
        val entity = restCore.blockingStringRequest(ob.uri, ob.jsonType)
        if (failHandle(entity, pair)) return
        pair.status = ClientCodes.SUCCESS
        pair.price = ob.price(entity.second)
        logger.trace("Price updated on ${pair.symbol} pair $name exch| = ${pair.price}")
    }

    /**
     * @return RestCurPriceObject which contains
     * - uri
     * - type of json
     * - function with get value logic*/
    abstract fun CurrencyPair.currentPriceExt(): RestCurPriceObject

    override fun priceChange(pair: CurrencyPair, interval: TimePeriod){
        if(!pair.updateTimes.priceChangeTimeElapsed(interval)) {
            logger.debug("price change $pair req skipped")
            return
        }
        val debMills = System.currentTimeMillis()


        runBlocking {
            val job = launch{
                changePeriods.forEach {
                    //logger.debug("CHANGE")
                    delay(requestDelay())
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


    final fun updateSinglePriceChange(pair: CurrencyPair, period: TimePeriod){
        val ob = pair.singlePriceChangeExt(period)
        val entity = restCore.blockingStringRequest(ob.uri, ob.jsonType)
        if (failHandle(entity, pair)) return
        val oldVal = ob.price(entity.second)
        if (oldVal == Double.MAX_VALUE) {
            //pair.putInPriceChange(period, Double.MAX_VALUE) //todo needs?
            return
        }
        val changeVol = if (pair.price > oldVal) ((pair.price - oldVal) * 100) / pair.price else (((oldVal - pair.price) * 100) / oldVal) * -1
        logger.trace("single price change calculating: price: ${pair.price}, period: ${period.name}, oldVal: $oldVal, changeVol: $changeVol")
        pair.putInPriceChange(period, BigDecimal(changeVol, MathContext(2)).toDouble())
        logger.trace("Change period updated on ${pair.symbol} pair $name exch, interval = ${period.name} | change = $changeVol")
    }

    abstract fun CurrencyPair.singlePriceChangeExt(period: TimePeriod): RestCurPriceObject

    final override fun priceHistory(pair: CurrencyPair, interval: String, limit: Int) {
        super.priceHistory(pair, interval, limit)
        val ob = pair.historyExt(interval, limit)
        val entity = restCore.blockingStringRequest(ob.uri, ob.jsonType)
        if (failHandle(entity, pair)) return
        pair.priceHistory.clear()
        ob.history(entity.second).forEach { pair.priceHistory.add(it) }
        logger.trace("price history updated on ${pair.symbol} pair $name exch")
    }

    abstract fun CurrencyPair.historyExt(interval: String, limit: Int): RestHistoryObject

    fun limited() = limits.any { it.type == LimitType.REQUEST }

    fun requestDelay(): Long{
        val l = limits.find { it.type == LimitType.REQUEST } ?: return 0
        return l.interval.toMillis() / l.limitValue
    }

    abstract fun <T: Any> Pair<HttpStatus, T>.getError(): Int

    protected fun <T: Any>  Pair<HttpStatus, T>.operateError(pair: CurrencyPair): Boolean{
        val error = getError()
        if (error != ClientCodes.SUCCESS){
            logger.error("Response has error: $first $second")
            when(error){
                ClientCodes.CURRENCY_NOT_FOUND -> {
                    pair.status = ClientCodes.CURRENCY_NOT_FOUND
                    return true
                }
                ClientCodes.EXCHANGE_NOT_ACCESSIBLE -> return true
                ClientCodes.TEMPORARY_UNAVAILABLE -> return true
            }
        }
        return false
    }

    protected fun <T: Any> Pair<HttpStatus, T>.hasErrors() = getError() != ClientCodes.SUCCESS

    protected fun <T: Any> failHandle(jsonEntity: Pair<HttpStatus, T>, pair: CurrencyPair): Boolean
            = stateChecker.checkEmptyJson(jsonEntity.second, exId) || jsonEntity.operateError(pair)

    override fun toString(): String {
        return "${this::class.simpleName} exId = $exId pairs: ${pairs.joinToString{it.symbol}}\n"
    }

}
