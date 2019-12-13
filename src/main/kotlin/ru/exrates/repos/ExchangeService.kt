package ru.exrates.repos

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import ru.exrates.entities.exchanges.BasicExchange
import javax.persistence.NoResultException
import javax.transaction.Transactional

@Service @Transactional
class ExchangeService(private val logger: Logger = LogManager.getLogger(ExchangeService::class),
                      @Autowired private val exchangeRepository: ExchangeRepository,
                      @Autowired private val currencyRepository: CurrencyRepository) {

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
    fun persist(exchange: BasicExchange) = exchangeRepository.save(exchange)

    @Transactional
    fun fillPairs(amount: Int) = currencyRepository.findAll(PageRequest.of(1, amount))

    @Transactional
    fun findPair(symbol: String, exchange: BasicExchange) = currencyRepository.findBySymbolAndExchange(symbol, exchange)
}