package ru.exrates.repos

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.stereotype.Repository
import ru.exrates.entities.AuthorizedUser
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.BasicExchange

interface CurrencyRepository : JpaRepository<CurrencyPair, Int> {
    fun findByBaseCurrencyAndQuoteCurrencyAndExchange(baseCurrency: String, quoteCurrency: String, exchange: BasicExchange) : CurrencyPair?

    fun findBySymbolAndExchange(symbol: String, exchange: BasicExchange) : CurrencyPair?

    @Query("select c.symbol from CurrencyPair c")
    fun getAll(): List<String>

    @Query("select concat(c.baseCurrency, '/', c.quoteCurrency)  from CurrencyPair c where c.exchange = ?1")
    fun getCurrencyPairsNames(exchange: BasicExchange): List<String>

}

@NoRepositoryBean interface ExchangeModRepo : JpaRepository<BasicExchange, Int> {
    fun findByName(name: String): BasicExchange? //todo noresulexception

    @Query("select p from TimePeriod p")
    fun getAllTimePeriods(): List<TimePeriod>

}

@Repository interface ExchangeRepository : ExchangeModRepo

//@Repository //todo needs?
interface UserRepository: JpaRepository<AuthorizedUser, Long> {
    fun findByToken(token: String): AuthorizedUser?

}