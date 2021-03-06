package ru.exrates.repos

import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import ru.exrates.configs.Properties
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.entities.exchanges.secondary.ExchangeNamesObject
import javax.persistence.NoResultException
import javax.transaction.Transactional

@Service @Transactional
class ExchangeService(@Autowired private val exchangeRepository: ExchangeRepository,
                      @Autowired private val currencyRepository: CurrencyRepository) {
    @Autowired
    private lateinit var logger: Logger

    @Autowired
    lateinit var props: Properties

    @Transactional
    fun find(id: Int): BasicExchange? = exchangeRepository.findById(id).orElse(null)

    @Transactional
    fun find(name: String): BasicExchange?{
        try{
            return exchangeRepository.findByName(name)
        }catch (e: NoResultException){
            return null
        }

    }

    @Transactional
    fun merge(exchange: BasicExchange) = exchangeRepository.save(exchange)

    @Transactional
    fun update(exchange: BasicExchange): BasicExchange{
        logger.debug("$exchange.name updated")
        exchange.pairs.forEach { currencyRepository.save(it) }
        return exchangeRepository.save(exchange)
    }

    @Transactional
    fun persist(exchange: BasicExchange): BasicExchange {
        val periods = exchangeRepository.getAllTimePeriods()
        outer@ for (oldPeriod in periods) {
            for (it in exchange.changePeriods) {
                if(oldPeriod.name == it.name) {
                    val idx = exchange.changePeriods.indexOf(it)
                    exchange.changePeriods.remove(it)
                    exchange.changePeriods.add(idx, oldPeriod)
                    continue@outer
                }
            }
        }

        try{
            logger.debug("change periods for exchange: ${exchange.name} ${exchange.changePeriods.joinToString { it.name }}")
            return exchangeRepository.save(exchange)
        }catch (e: Exception){
            print("fuck")
        }

        return exchangeRepository.save(exchange)
    }

    @Transactional
    fun fillPairs(amount: Int) = currencyRepository.findAll(PageRequest.of(1, amount))

    @Transactional
    fun fillPairs(amount: Int, exchange: BasicExchange): List<CurrencyPair>{

        val reqPairs = if (amount < exchange.topPairs.size) {
            exchange.topPairs.subList(0, amount - 1)
        } else exchange.topPairs

        val curPairs = currencyRepository.findByExchangeAndSymbolIn(exchange, reqPairs)
        if (curPairs.size < amount){
            val diffNumb = amount - curPairs.size
            curPairs.addAll(currencyRepository.f(exchange, exchange.topPairs, PageRequest.of(0, diffNumb)))
        }

        return curPairs
    }

    @Transactional
    fun findPair(c1: String, c2: String, exchange: BasicExchange) = currencyRepository.findByBaseCurrencyAndQuoteCurrencyAndExchange(c1, c2, exchange)

    @Transactional
    fun findPair(pSymbol: String, exchange: BasicExchange) = currencyRepository.findBySymbolAndExchange(pSymbol, exchange)

    @Transactional
    //fun getAllPairs() = currencyRepository.getAll().toSet().toList()
    fun getAllPairs(exchanges: Collection<BasicExchange>): Map<Int, ExchangeNamesObject> {
        val map = HashMap<Int, ExchangeNamesObject>()
        exchanges.forEach {  map[it.exId] = ExchangeNamesObject(it.exId, it.name, it.delimiter, currencyRepository.getCurrencyPairsNames(it)) }
        return map
    }


}