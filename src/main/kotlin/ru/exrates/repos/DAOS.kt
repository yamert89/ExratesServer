package ru.exrates.repos

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.stereotype.Repository
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.exchanges.BasicExchange

interface CurrencyRepository : JpaRepository<CurrencyPair, Int> {
    fun findBySymbolAndExchange(symbol: String, exchange: BasicExchange) : CurrencyPair
}

@NoRepositoryBean interface ExchangeModRepo : JpaRepository<BasicExchange, Int> {
    fun findByName(name: String): BasicExchange //todo noresulexception
}

@Repository interface ExchangeRepository : ExchangeModRepo