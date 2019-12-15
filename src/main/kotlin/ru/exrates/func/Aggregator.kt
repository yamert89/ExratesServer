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
import ru.exrates.entities.exchanges.secondary.BinanceExchange
import ru.exrates.repos.ExchangeService
import java.util.*
import javax.annotation.PostConstruct
import kotlin.collections.HashMap
import kotlin.reflect.KClass

@Component
class Aggregator(
    val logger: Logger = LogManager.getLogger(Aggregator::class),
    val exchanges: Map<String, BasicExchange> = HashMap(),
    val exchangeNames: Map<String, KClass<out BasicExchange>> = HashMap(),
    @Autowired
    val exchangeService: ExchangeService,
    val applicationContext: ApplicationContext,
    @Autowired
    val genericApplicationContext: GenericApplicationContext,
    @Autowired
    val props: Properties
) {

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
            val clazz = it.value
            genericApplicationContext.registerBean(clazz.java, {finalExchange}, {def: BeanDefinition -> def.isPrimary = true})
        }
    }

    fun calculatePairsSize(exchange: BasicExchange): Int{
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