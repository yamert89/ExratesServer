package ru.exrates.configs

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.InjectionPoint
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.*
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement
import ru.exrates.entities.exchanges.rest.BinanceExchange
import ru.exrates.entities.exchanges.rest.CoinBaseExchange
import ru.exrates.entities.exchanges.rest.P2pb2bExchange
import ru.exrates.func.RestCore

/*import ru.exrates.entities.exchanges.ExmoExchange*/

@Configuration
@EnableJpaRepositories(basePackages = ["ru.exrates.repos"])
@EnableTransactionManagement
class App {

    @Bean
    @Lazy
    fun binanceExchange(): BinanceExchange =
        BinanceExchange()

    @Bean
    @Lazy
    fun p2pb2bExchange() : P2pb2bExchange =
        P2pb2bExchange()

    @Bean
    @Lazy
    fun coinbaseExchange() = CoinBaseExchange()

    @Bean
    @Scope(value = "prototype")
    fun restCore(endPoint: String, banCode: Int, l: Int, s:Int ) = RestCore(endPoint, banCode, l, s)

    /*@Bean
    @Lazy
    open fun exmoExchange(): ExmoExchange {
        return ExmoExchange()
    }*/

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource.first")
    open fun firstDataSourceProperties(): DataSourceProperties {
        return DataSourceProperties()
    }

    @Bean
    //@Primary
    @ConfigurationProperties("app.datasource.second")
    open fun secondDataSourceProperties(): DataSourceProperties {
        return DataSourceProperties()
    }

    @Bean
    @Scope("prototype")
    fun logger(injectionPoint: InjectionPoint): Logger{
        return LogManager.getLogger(injectionPoint.field?.declaringClass)
    }

}