package ru.exrates.func

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanDefinitionCustomizer
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.stereotype.Component
import ru.exrates.configs.Properties
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.entities.exchanges.BinanceExchange
import ru.exrates.entities.exchanges.ExchangeDTO
import ru.exrates.entities.exchanges.ExmoExchange
import ru.exrates.repos.ExchangeService
import java.util.*
import javax.annotation.PostConstruct
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.reflect.KClass

@Component
class Aggregator(
    val logger: Logger = LogManager.getLogger(Aggregator::class),
    val exchanges: MutableMap<String, BasicExchange> = HashMap(),
    val exchangeNames: MutableMap<String, KClass<out BasicExchange>> = HashMap(),
    @Autowired
    val exchangeService: ExchangeService,
    @Autowired
    val genericApplicationContext: GenericApplicationContext,
    @Autowired
    val props: Properties
) {

    init {
        exchangeNames["binanceExchange"] = BinanceExchange::class
        exchangeNames["exmoExchange"] = ExmoExchange::class
    }

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
                    val page = exchangeService.fillPairs(pairsSize)
                    exchange.pairs.clear()
                    exchange.pairs.addAll(page.content)
                }
            }

            val finalExchange = exchange
            val clazz: KClass<BasicExchange> = it.value as KClass<BasicExchange>
            //Arrays.stream(genericApplicationContext.beanDefinitionNames).forEach(System.out::println);
            genericApplicationContext.registerBean(
                clazz.java, {finalExchange},
                arrayOf(BeanDefinitionCustomizer { def: BeanDefinition -> def.isPrimary = true }))

            exchange = genericApplicationContext.getBean(it.value.java)
            exchanges[it.key] = exchange
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

    fun getExchange(exId: Int): ExchangeDTO{
        logger.debug("exchanges: ${exchanges.values}")
        logger.debug("get exchange for exId $exId")
        val ex: BasicExchange? = getExById(exId)
        val dto = ExchangeDTO(ex)
        val pairs = TreeSet<CurrencyPair>()
        val iterator = dto.pairs.iterator()
        for (i in 0..3) pairs.add(iterator.next())  //todo top pairs
        dto.pairs = pairs
        logger.debug("pairs in exchange: ${pairs.joinToString()}")
        return dto
    }

    fun getExchange(exId: Int, pairsN: Array<String>, period: String): ExchangeDTO?{
        logger.debug("exchanges: ${exchanges.values}")
        val exch = getExById(exId)
        if(exch == null){
            logger.error("Exchange $exId not found")
            return null
        }
        val pairs = exch.pairs
        val temp = CurrencyPair()
        pairsN.forEach {
            temp.symbol = it
            temp.exId = exch.exId

            if( !pairs.containsPair(temp)) exch.insertPair(exchangeService.findPair(it, exch)
                ?: throw NullPointerException("Pair $it not found in ${exch.name}"))

        }

        val reqPairs = HashSet(pairs.filter { pairsN.contains(it.symbol) })
        //todo - limit request pairs
        val timePeriod = exch.changePeriods.filter { it.name == period }[0]
        reqPairs.forEach {
            exch.currentPrice(it, timePeriod.period)
            exch.priceChange(it, timePeriod.period) //todo needs try catch?
            exch.priceHistory(it, period, 10)
        }
        val dto = ExchangeDTO(exch)
        dto.pairs.removeIf { !pairsN.contains(it.symbol) }

        logger.debug("pairs in exchange: ${exch.pairs.joinToString()}")

        return dto

    }

    //fun getCurStat(curName1: String, curName2: String) = getCurStat(curName1 + curName2)

    fun getCurStat(pName: String, histroryInterval: String?, limit: Int): List<CurrencyPair> {
        logger.debug("exchanges: ${exchanges.values}")
        val curs = mutableListOf<CurrencyPair>()
        exchanges.forEach {
            val exchange = it.value
            val p = exchange.getPair(pName)
            if(p != null){
                p.exchange = exchange
                curs.add(p)
            } else {
                val pair = exchangeService.findPair(pName, exchange)
                if (pair != null){ //todo optional
                    pair.exchange = exchange
                    curs.add(pair)
                    exchange.insertPair(pair)
                   // p = exchange.getPair(pair.symbol)!!
                    exchange.currentPrice(pair, exchange.updatePeriod)
                    exchange.priceChange(pair, exchange.updatePeriod)
                    exchange.priceHistory(pair, histroryInterval ?: exchange.historyPeriods[0], limit)
                    pair.historyPeriods = exchange.historyPeriods
                }
            }
        }

        return curs
    }

    fun priceHistory(pName: String, exId: Int, historyInterval: String, limit: Int): List<Double>{
        logger.debug("exchanges: ${exchanges.values}")
        val exchange: BasicExchange = getExById(exId) ?: throw NullPointerException("exchange $exId not found")
        var pair = exchange.getPair(pName)
        if(pair == null){
            pair = exchangeService.findPair(pName, exchange) ?: throw NullPointerException("pair $pName not found")
            exchange.insertPair(pair)
        }
        exchange.priceHistory(pair, historyInterval, limit)
        return pair.priceHistory
    }

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

    private fun getExById(id: Int) = exchanges.values.find { it.exId == id }





}