package ru.exrates.configs

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement
import ru.exrates.entities.exchanges.BinanceExchange
import ru.exrates.entities.exchanges.P2pb2bExchange

/*import ru.exrates.entities.exchanges.ExmoExchange*/

@Configuration
@EnableJpaRepositories(basePackages = ["ru.exrates.repos"])
@EnableTransactionManagement
class App {

    @Bean
    @Lazy
    fun binanceExchange(): BinanceExchange = BinanceExchange()

    @Bean
    @Lazy
    fun p2pb2bExchange() : P2pb2bExchange = P2pb2bExchange()

    /*@Bean
    @Lazy
    open fun exmoExchange(): ExmoExchange {
        return ExmoExchange()
    }*/

    @Bean
    //@Primary
    @ConfigurationProperties("app.datasource.first")
    open fun firstDataSourceProperties(): DataSourceProperties {
        return DataSourceProperties()
    }

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource.second")
    open fun secondDataSourceProperties(): DataSourceProperties {
        return DataSourceProperties()
    }
}