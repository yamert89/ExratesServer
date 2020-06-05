package ru.exrates.func

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanDefinitionCustomizer
import org.springframework.context.support.GenericApplicationContext
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import ru.exrates.configs.Properties
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.*
/*import ru.exrates.entities.exchanges.ExmoExchange*/
import ru.exrates.repos.ExchangeService
import ru.exrates.utils.CursPeriod
import ru.exrates.utils.ExchangePayload
import java.time.Duration
import java.util.*
import javax.annotation.PostConstruct
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.reflect.KClass

@Component
class Aggregator(
    @Autowired
    val exchangeService: ExchangeService,
    @Autowired
    val genericApplicationContext: GenericApplicationContext,
    @Autowired
    val props: Properties
) {
    val logger: Logger = LogManager.getLogger(Aggregator::class)
    val exchanges: MutableMap<Int, BasicExchange> = HashMap()
    val exchangeNames: MutableMap<String, KClass<out BasicExchange>> = HashMap()

    init {
        exchangeNames["binance"] = BinanceExchange::class
        exchangeNames["p2pb2b"] = P2pb2bExchange::class
        //exchangeNames["exmoExchange"] = ExmoExchange::class
    }

    /*
    * ******************************************************************************************************************
    *       Initialization
    * ******************************************************************************************************************
    * */

    @PostConstruct
    fun init(){
        exchangeNames.entries.forEach {
            var exchange: BasicExchange? = exchangeService.find(it.key)
            var pairsSize = 0
            if(exchange == null){
                exchange = genericApplicationContext.getBean(it.value.java)
                exchange = exchangeService.persist(exchange)
                pairsSize = calculatePairsSize(exchange)
                val pairs = TreeSet(exchange.pairs)
                while(pairs.size > pairsSize) pairs.pollLast()
                exchange.pairs.clear()
                exchange.pairs.addAll(pairs)
            }else{
                pairsSize = calculatePairsSize(exchange)
                if(exchange.pairs.size > pairsSize) {
                    val pairs = exchangeService.fillPairs(pairsSize, exchange)
                    exchange.pairs.clear()
                    exchange.pairs.addAll(pairs)
                }
            }

            val finalExchange = exchange
            val clazz: KClass<BasicExchange> = it.value as KClass<BasicExchange>
            //Arrays.stream(genericApplicationContext.beanDefinitionNames).forEach(System.out::println);
            genericApplicationContext.registerBean(
                clazz.java, {finalExchange},
                arrayOf(BeanDefinitionCustomizer { def: BeanDefinition -> def.isPrimary = true }))

            exchange = genericApplicationContext.getBean(it.value.java)
            exchanges[exchange.exId] = exchange
             val task = object: TimerTask(){
                 override fun run() {
                     save()
                 }
             }
            Timer().schedule(task, 300000, props.savingTimer())
            //Arrays.stream(genericApplicationContext.beanDefinitionNames).forEach(System.out::println);
            //genericApplicationContext.removeBeanDefinition(it.key);

        }
    }

    /*
    * ******************************************************************************************************************
    *       Request methods
    * ******************************************************************************************************************
    * */

    fun getExchange(exId: Int): ExchangeDTO{
        logger.debug("exchanges: ${exchanges.values}")
        logger.debug("get exchange for exId $exId")
        val ex: BasicExchange? = exchanges[exId]
        val dto = ExchangeDTO(ex)
        if (ex == null) return dto
        val pairs = TreeSet<CurrencyPair>()
        val iterator = dto.pairs.iterator()
        for (i in 0 until props.maxSize()) if (iterator.hasNext()) pairs.add(iterator.next())
        dto.pairs = pairs
        logger.debug("pairs in exchange: ${pairs.joinToString()}")
        return dto
    }

    fun getExchange(exId: Int, pairsN: Array<String>, period: String): ExchangeDTO{
        logger.debug("exchanges: ${exchanges.values}")
        var currentMills = System.currentTimeMillis()
        logger.debug("start ex")
        val exch = exchanges[exId]
        if(exch == null){
            logger.error("Exchange $exId not found")
            return ExchangeDTO(null)
        }
        val pairs = exch.pairs
        val temp = CurrencyPair()
        pairsN.forEach {
            temp.symbol = it
            temp.exId = exch.exId

            if( !pairs.containsPair(temp)) exch.insertPair(exchangeService.findPair(it, exch)
                ?: throw NullPointerException("Pair $it not found in ${exch.name}"))

        }
        currentMills = System.currentTimeMillis() - currentMills
        logger.debug("end pairsN loop: $currentMills")

        val reqPairs = HashSet(pairs.filter { pairsN.contains(it.symbol) })
        currentMills = System.currentTimeMillis() - currentMills
        logger.debug("end reqPairs Filter: $currentMills")
        //todo - limit request pairs
        val timePeriod = exch.getTimePeriod(period)
        val restExch = exch as RestExchange
        reqPairs.forEach {
            exch.currentPrice(it, timePeriod)
            if (it.updateTimes.priceChangeTimeElapsed(timePeriod)) restExch.updateSinglePriceChange(it, timePeriod, restExch.singlePriceChangeRequest(it, timePeriod))
           //exch.priceChange(it, timePeriod, true) //todo needs try catch?
            exch.priceHistory(it, period, 10)
        }
        currentMills = System.currentTimeMillis() - currentMills
        logger.debug("end reqPairs update: $currentMills")
        val dto = ExchangeDTO(exch)
        dto.pairs.removeIf { !pairsN.contains(it.symbol) }

        logger.debug("pairs in exchange: ${exch.pairs.joinToString()}")

        return dto

    }

    //fun getCurStat(curName1: String, curName2: String) = getCurStat(curName1 + curName2)

    fun getCurStat(c1: String, c2: String, historyInterval: String?, limit: Int): List<CurrencyPair> {
        logger.debug("exchanges: ${exchanges.values}")
        val curs = mutableListOf<CurrencyPair>()
        exchanges.forEach {
            val exchange = it.value
            var p = exchange.getPair(c1, c2)
            if(p == null) {
                p = exchangeService.findPair(c1, c2, exchange)
                if (p != null) exchange.insertPair(p)
            }
            if (p != null){
                p.exchange = exchange
                // p = exchange.getPair(pair.symbol)!!
                exchange.currentPrice(p, exchange.taskTimeOut)
                exchange.priceChange(p, exchange.taskTimeOut)
                exchange.priceHistory(p, historyInterval ?:
                    exchange.historyPeriods.find { per -> per == "1h" } ?: exchange.historyPeriods[1], limit)
                p.historyPeriods = exchange.historyPeriods
                curs.add(p)
            }
        }

        return curs
    }

    fun getOnePair(c1: String, c2: String, exId: Int, currentInterval: String): CurrencyPair {
        val ex = exchanges[exId]!!
        var pair = ex.getPair(c1, c2)
        if (pair == null) {
            pair = exchangeService.findPair(c1, c2, ex)
            ex.insertPair(pair!!)
        }
        ex.currentPrice(pair, ex.taskTimeOut)
        ex.priceChange(pair, ex.taskTimeOut)
        ex.priceHistory(pair, currentInterval, 10) //todo right?
        return pair
    }

    fun priceHistory(c1: String, c2: String, exId: Int, historyInterval: String, limit: Int): List<Double>{
        logger.debug("exchanges: ${exchanges.values}")
        val exchange: BasicExchange = exchanges[exId] ?: throw NullPointerException("exchange $exId not found")
        var pair = exchange.getPair(c1, c2)
        if(pair == null){
            pair = exchangeService.findPair(c1, c2, exchange) ?: throw NullPointerException("pair $c1 - $c2 not found in $exchange")
            exchange.insertPair(pair)
        }
        exchange.priceHistory(pair, historyInterval, limit)
        return pair.priceHistory
    }

    fun getCursIntervalStatistic(cursPayload: ExchangePayload): CursPeriod{
        val ex = exchanges[cursPayload.exId]!!
        val values = HashMap<String, Double>()
        val requests = HashMap<CurrencyPair, Mono<String>>()
        val restEx = ex as RestExchange
        val timePeriod = ex.getTimePeriod(cursPayload.interval)

        cursPayload.pairs.forEach {
            var pair = ex.getPair(it)
            if(pair == null){
                pair = exchangeService.findPair(it, ex) ?: throw java.lang.NullPointerException("pair $it not found in $ex")
                ex.insertPair(pair)
            }
            if (pair.updateTimes.priceChangeTimeElapsed(timePeriod)) requests[pair] = restEx.singlePriceChangeRequest(pair, timePeriod)
            else logger.debug("SKIPPED: price change for ${timePeriod.name} in $pair")
           //
        }

        requests.forEach {
            ex.currentPrice(it.key, timePeriod)
            restEx.updateSinglePriceChange(it.key, timePeriod, it.value)
            values[it.key.symbol] = it.key.getPriceChangeValue(ex.getTimePeriod(cursPayload.interval)) ?: Double.MAX_VALUE
        }
        return CursPeriod(cursPayload.interval, values)
    }

    /*
    * ******************************************************************************************************************
    *       Class methods
    * ******************************************************************************************************************
    * */

    fun getNamesExchangesAndCurrencies() = exchangeService.getAllPairs(exchanges.values)

    fun save(){
        logger.debug("Saving exchanges...")
        exchanges.forEach { (_, exch) -> exchangeService.update(exch) }
    }

    private fun calculatePairsSize(exchange: BasicExchange): Int{ //todo check for seconds limit
        var counter = 0
        val tLimits = LinkedList<Int>()
        if(props.isPersistenceStrategy()) return  props.maxSize()
        val ammountReqs = exchange.changePeriods.size + 1
        exchange.limits.forEach {limit ->
            val l = (limit.limitValue / (limit.interval.seconds / 60).toDouble()).toInt()
            logger.debug("tLimit = $l")
            tLimits.plus(l)
        }
        tLimits.forEach { counter += it }
        return counter / tLimits.size
    }




}