package ru.exrates.entities.exchanges

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.hibernate.annotations.SortComparator
import org.springframework.beans.factory.annotation.Autowired
import ru.exrates.configs.Properties
import ru.exrates.entities.Currency
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.secondary.Limit
import ru.exrates.utils.JsonSerializers.TimePeriodListSerializer
import java.time.Duration
import java.util.*

import javax.persistence.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

@Entity @Inheritance(strategy = InheritanceType.SINGLE_TABLE) @DiscriminatorColumn(name = "EXCHANGE_TYPE")
@JsonIgnoreProperties("id", "limits", "limitcode", "banCode", "sleepValueSeconds", "updatePeriod", "temporary")
class BasicExchange(private val logger: Logger = LogManager.getLogger(BasicExchange::class)) : Exchange{

    var temporary = true
    var limitCode: Int = 0
    var banCode: Int = 0
    @Autowired
    lateinit var props: Properties


    lateinit var URL_ENDPOINT: String
    lateinit var URL_CURRENT_AVG_PRICE: String
    lateinit var URL_INFO: String
    lateinit var URL_PRICE_CHANGE: String
    lateinit var URL_PING: String
    lateinit var URL_ORDER: String

    @Id @GeneratedValue
    var id: Int = 0
    lateinit var name: String

    @OneToMany(cascade = [CascadeType.PERSIST], fetch = FetchType.EAGER)
    @SortComparator(CurrencyPair)


    @ManyToMany(cascade = [CascadeType.PERSIST], fetch = FetchType.EAGER)
    @JsonSerialize(using = TimePeriodListSerializer::class)
    val changePeriods: List<TimePeriod> = ArrayList()

    @OneToMany(orphanRemoval = true, cascade = [CascadeType.PERSIST], fetch = FetchType.EAGER)
    val limits: Set<Limit> = HashSet()

    var updatePeriod: Duration = Duration.ofMillis(props.timerPeriod())



    override fun insertPair(pair: CurrencyPair) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getPair(c1: Currency, c2: Currency): CurrencyPair {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getPair(pairName: String): CurrencyPair {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}