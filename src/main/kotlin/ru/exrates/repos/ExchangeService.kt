package ru.exrates.repos

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.entities.exchanges.secondary.ExchangeNamesObject
import javax.persistence.NoResultException
import javax.transaction.Transactional

@Service @Transactional
class ExchangeService(@Autowired private val exchangeRepository: ExchangeRepository,
                      @Autowired private val currencyRepository: CurrencyRepository) {
    private val logger: Logger = LogManager.getLogger(ExchangeService::class)

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

        return exchangeRepository.save(exchange)
    }

    @Transactional
    fun fillPairs(amount: Int) = currencyRepository.findAll(PageRequest.of(1, amount))

    @Transactional
    fun fillPairs(amount: Int, exchange: BasicExchange): List<CurrencyPair>{

        val reqPairs = if (amount < exchange.topPairs.size) {
            exchange.topPairs.subList(0, amount)
        } else exchange.topPairs

        val curPairs = currencyRepository.findByExchangeAndSymbolIn(exchange, reqPairs)
        if (curPairs.size < amount){
            val diffNumb = amount - curPairs.size
            curPairs.addAll(currencyRepository.findTopBySymbolNotIn(exchange.topPairs, PageRequest.of(1, diffNumb)))
        }
        return curPairs
    }

    @Transactional
    fun findPair(c1: String, c2: String, exchange: BasicExchange) = currencyRepository.findByBaseCurrencyAndQuoteCurrencyAndExchange(c1, c2, exchange)

    @Transactional
    fun findPair(pSymbol: String, exchange: BasicExchange) = currencyRepository.findBySymbolAndExchange(pSymbol, exchange)

    @Transactional
    //fun getAllPairs() = currencyRepository.getAll().toSet().toList()
    fun getAllPairs(exchanges: Collection<BasicExchange>): List<ExchangeNamesObject> {
        val list = ArrayList<ExchangeNamesObject>()
        exchanges.forEach {  list.add(ExchangeNamesObject(it.exId, it.name, currencyRepository.getCurrencyPairsNames(it))) }
        return list
    }


}