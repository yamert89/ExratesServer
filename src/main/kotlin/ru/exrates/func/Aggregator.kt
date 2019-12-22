package ru.exrates.func

import com.sun.xml.fastinfoset.util.StringArray
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
import ru.exrates.repos.ExchangeService
import java.util.*
import javax.annotation.PostConstruct
import kotlin.collections.HashMap
import kotlin.reflect.KClass
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType

@Component
class Aggregator(
    val logger: Logger = LogManager.getLogger(Aggregator::class),
    val exchanges: MutableMap<String, BasicExchange> = HashMap(),
    val exchangeNames: MutableMap<String, KClass<out BasicExchange>> = HashMap(),
    @Autowired
    val exchangeService: ExchangeService,
    val applicationContext: ApplicationContext, //todo del
    @Autowired
    val genericApplicationContext: GenericApplicationContext,
    @Autowired
    val props: Properties
) {

    init {
        exchangeNames["binanceExchange"] = BinanceExchange::class
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

            val finalExchange = exchange!!
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

    fun getExchange(exName: String): BasicExchange{
        var ex: BasicExchange? = exchanges[exName]
        ex = ex?.clone() as BasicExchange
        val pairs = TreeSet<CurrencyPair>(ex.pairs)
        while (pairs.size > 1) pairs.pollLast() //todo limit count
        ex.pairs.clear()
        ex.pairs.addAll(pairs)
        return ex.clone() as BasicExchange
    }

    fun getExchange(exName: String, pairsN: Array<String>, period: String): BasicExchange?{
        val exch = exchanges[exName]
        if(exch == null){
            logger.error("Exchange $exName not found")
            return null
        }
        val pairs = exch.pairs
        val temp = CurrencyPair()
        pairsN.forEach {
            temp.symbol = it

            if( !pairs.contains(temp)) exch.insertPair(exchangeService.findPair(it, exch)
                ?: throw NullPointerException("Pair $it not found in ${exch.name}"))

        }

        val reqPairs = HashSet(pairs)
        //todo - limit request pairs
        val timePeriod = exch.changePeriods.filter { it.name == period }[0]
        reqPairs.forEach {
            exch.currentPrice(it, timePeriod.period)
            exch.priceChange(it, timePeriod.period) //todo needs try catch?
        }
        return exch

    }

    fun getCurStat(curName1: String, curName2: String) = getCurStat(curName1 + curName2)

    fun getCurStat(pName: String): Map<String, CurrencyPair> {
        val curs : MutableMap<String, CurrencyPair> = HashMap()
        exchanges.forEach {
            val p = it.value.getPair(pName)
            if(p != null){
                curs[it.key] = p
                it.value.insertPair(p)
            } else {
                val pair = exchangeService.findPair(pName, it.value)
                if (pair != null){ //todo optional
                    curs[it.key] = pair
                    it.value.insertPair(pair)
                }
            }
        }
        return curs
    }

    fun getNamesExchangesAndCurrencies(): Map<String, List<String>> {
        return mapOf(
            "exchanges" to exchangeNames.keys.toList(),
            "currencies" to exchangeService.getAllPairs()
        )
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





}