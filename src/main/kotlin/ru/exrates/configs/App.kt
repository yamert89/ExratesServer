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

@Configuration
@EnableJpaRepositories(basePackages = ["ru.exrates.repos"])
@EnableTransactionManagement
class App {

    @Bean
    @Lazy
    open fun binanceExchange(): BinanceExchange {
        return BinanceExchange()
    }

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
}