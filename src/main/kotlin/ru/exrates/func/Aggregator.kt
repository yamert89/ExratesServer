package ru.exrates.func

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanDefinitionCustomizer
import org.springframework.context.support.GenericApplicationContext
import org.springframework.stereotype.Component
import ru.exrates.configs.Properties
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.entities.exchanges.ExchangeDTO
import ru.exrates.entities.exchanges.rest.*
import ru.exrates.entities.exchanges.secondary.ExchangeNamesObject
import ru.exrates.repos.ExchangeService
import ru.exrates.utils.ClientCodes
import ru.exrates.utils.CursPeriod
import ru.exrates.utils.ExchangePayload
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
    val props: Properties,
    @Autowired
    val stateChecker: EndpointStateChecker,
    @Autowired
    val taskHandler: TaskHandler
) {
    @Autowired
    lateinit var logger: Logger
    val exchanges: MutableMap<Int, BasicExchange> = HashMap()
    val exchangeNames: MutableMap<String, KClass<out BasicExchange>> = HashMap()
    private val fullExchangeNames = mapOf<String, KClass<out BasicExchange>>(
        "binance" to BinanceExchange::class,
        "p2pb2b" to P2pb2bExchange::class,
        "huobi" to HuobiExchange::class,
        "coinbase" to CoinBaseExchange::class)



    init {
        exchangeNames["binance"] = BinanceExchange::class
        exchangeNames["p2pb2b"] = P2pb2bExchange::class
        exchangeNames["coinbase"] = CoinBaseExchange::class
        exchangeNames["huobi"] = HuobiExchange::class
        //exchangeNames["exmoExchange"] = ExmoExchange::class
    }

    /*
    * ******************************************************************************************************************
    *       Initialization
    * ******************************************************************************************************************
    * */
    //TODO replace to other class

    @PostConstruct
    fun init(){
        logger.trace("\n\n\t\t\t\tSTARTING EXRATES VERSION ${props.appVersion()}\n\n")

            exchangeNames.entries.forEach {
                loadExchange(it.key, it.value)
            }
            GlobalScope.launch {
                launch(taskHandler.getExecutorContext()){
                    repeat(Int.MAX_VALUE){
                        delay(props.savingTimer())
                        save()
                    }
                }

            }

    }

    private fun loadExchange(exName: String, claz: KClass<out BasicExchange>){
        var key = ""
        try {
            var exchange: BasicExchange? = exchangeService.find(exName)
           key = exName
            var pairsSize = 0
            if(exchange == null){
                exchange = genericApplicationContext.getBean(claz.java)
                exchange = exchangeService.persist(exchange)
                pairsSize = calculatePairsSize(exchange)
                val pairs = TreeSet(exchange.pairs)
                while(pairs.size > pairsSize) pairs.pollLast()
                exchange.pairs.clear()
                exchange.pairs.addAll(pairs)
            }else{
                pairsSize = calculatePairsSize(exchange)
                if (!props.skipTop()) {
                    val pairs = exchangeService.fillPairs(pairsSize, exchange)
                    exchange.pairs.clear()
                    exchange.pairs.addAll(pairs)
                }else{
                    while (exchange.pairs.size > pairsSize) exchange.pairs.remove(exchange.pairs.last())
                }
            }

            val finalExchange = exchange
            val clazz: KClass<BasicExchange> = claz as KClass<BasicExchange>
            genericApplicationContext.registerBean(
                clazz.java, {finalExchange},
                arrayOf(BeanDefinitionCustomizer { def: BeanDefinition -> def.isPrimary = true }))

            exchange = genericApplicationContext.getBean(clazz.java)
            exchanges[exchange.exId] = exchange
            logger.trace("Initialization $key success with ${exchange.pairs.size} pairs")
        }catch (e: Exception){
            logger.error(e)
            logger.error("Failed $key initialization")
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
        if (!stateChecker.accessible(exId)) return ExchangeDTO(null, exchanges[exId]!!.name, ClientCodes.EXCHANGE_NOT_ACCESSIBLE)
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
        if (!stateChecker.accessible(exId)) return ExchangeDTO(null, exchanges[exId]!!.name, ClientCodes.EXCHANGE_NOT_ACCESSIBLE)
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
            with(taskHandler){
                awaitTasks( exch.requestDelay(),
                    { exch.currentPrice(it, timePeriod) },
                    { exch.priceHistory(it, period, 10) }
                )
                if (it.updateTimes.priceChangeTimeElapsed(timePeriod))
                   awaitTasks({ restExch.updateSinglePriceChange(it, timePeriod)})
                //exch.priceChange(it, timePeriod, true) //todo needs try catch?
            }

        }
        currentMills = System.currentTimeMillis() - currentMills
        logger.debug("end reqPairs update: $currentMills")
        val dto = ExchangeDTO(exch)
        dto.pairs.removeIf { !pairsN.contains(it.symbol) }

        logger.debug("pairs in exchange: ${exch.pairs.joinToString()}")

        return dto

    }

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
                with(taskHandler){
                    awaitTasks(exchange.castToRestExchange().requestDelay(), //todo replace tasks out loop, add second loop
                        { exchange.currentPrice(p, exchange.taskTimeOut) },
                        { exchange.priceChange(p, exchange.taskTimeOut) },
                        { exchange.priceHistory(p, historyInterval ?:
                        exchange.historyPeriods.find { per -> per == "1h" } ?: exchange.historyPeriods[1], limit) }
                    )
                }

                p.historyPeriods = exchange.historyPeriods
                curs.add(p)
            }
        }

        return curs
    }

    fun getOnePair(c1: String, c2: String, exId: Int, currentInterval: String): CurrencyPair {
        if (!stateChecker.accessible(exId)) return CurrencyPair(ClientCodes.EXCHANGE_NOT_ACCESSIBLE)
        val ex = exchanges[exId]!!
        var pair = ex.getPair(c1, c2)
        if (pair == null) {
            pair = exchangeService.findPair(c1, c2, ex)
            ex.insertPair(pair!!)
        }
        with(taskHandler){
            awaitTasks(ex.castToRestExchange().requestDelay(),
                {ex.currentPrice(pair, ex.taskTimeOut)},
                {ex.priceChange(pair, ex.taskTimeOut)},
                {ex.priceHistory(pair, currentInterval, 10)}  //todo right?
            )

        }

        return pair
    }

    fun priceHistory(c1: String, c2: String, exId: Int, historyInterval: String, limit: Int): List<Double>{
        logger.debug("exchanges: ${exchanges.values}")
        if (!stateChecker.accessible(exId)) return listOf(ClientCodes.EXCHANGE_NOT_ACCESSIBLE.toDouble(), exId.toDouble())
        val exchange: BasicExchange = exchanges[exId] ?: throw NullPointerException("exchange $exId not found")
        var pair = exchange.getPair(c1, c2)
        if(pair == null){
            pair = exchangeService.findPair(c1, c2, exchange) ?: throw NullPointerException("pair $c1 - $c2 not found in $exchange")
            exchange.insertPair(pair)
        }
        taskHandler.awaitTasks({ exchange.priceHistory(pair, historyInterval, limit) })
        return pair.priceHistory
    }

    fun getCursIntervalStatistic(cursPayload: ExchangePayload): CursPeriod{
        if (!stateChecker.accessible(cursPayload.exId)) return CursPeriod("", mapOf(), ClientCodes.EXCHANGE_NOT_ACCESSIBLE)
        val ex = exchanges[cursPayload.exId]!!
        val values = HashMap<String, Double>()
        val functions = mutableListOf<() -> Unit>()
        val restEx = ex as RestExchange
        val timePeriod = ex.getTimePeriod(cursPayload.interval)
        val pairs = mutableListOf<CurrencyPair>()

        cursPayload.pairs.forEach {
            var pair = ex.getPair(it)
            if(pair == null){
                pair = exchangeService.findPair(it, ex) ?: throw java.lang.NullPointerException("pair $it not found in $ex")
                ex.insertPair(pair)
            }
            pairs.add(pair)
            if (pair.updateTimes.priceChangeTimeElapsed(timePeriod)) {
                functions.add {ex.currentPrice(pair, timePeriod)}
                functions.add {restEx.updateSinglePriceChange(pair, timePeriod)}
            }
            else logger.debug("SKIPPED: price change for ${timePeriod.name} in $pair")
        }
        taskHandler.awaitTasks(*functions.toTypedArray())


        pairs.forEach {
             values[it.symbol] = it.getPriceChangeValue(ex.getTimePeriod(cursPayload.interval)) ?: Double.MAX_VALUE
        }

        return CursPeriod(cursPayload.interval, values)
    }

    /*
    * ******************************************************************************************************************
    *       Class methods
    * ******************************************************************************************************************
    * */

    fun getNamesExchangesAndCurrencies(): Map<Int, ExchangeNamesObject>{
        return exchangeService.getAllPairs(exchanges.values)
    }

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

    fun disableExchange(exName: String) {
        if (!exchangeNames.containsKey(exName)) {logger.error("exchange $exName not found in context");  return}
        genericApplicationContext.removeBeanDefinition("${exName}Exchange")
        genericApplicationContext.beanDefinitionNames.joinToString("\n")
        exchangeNames.remove(exName)
        exchanges.remove(exchanges.values.find { it.name == exName }!!.exId)
        logger.trace("Exchange $exName disabled")
    }

    fun enableExchange(ex: String) { //fixme reenabling
        if (exchangeNames.contains(ex)) logger.error("Exchange $ex already enabled")
        fullExchangeNames[ex]?.let {
            loadExchange(ex, it)
            exchangeNames[ex] = fullExchangeNames[ex]!!
        } ?: logger.error("Exchange $ex not found")
    }


}