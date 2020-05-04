package ru.exrates.entities.exchanges

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.hibernate.annotations.SortComparator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import ru.exrates.configs.Properties
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.LimitType
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.secondary.BanException
import ru.exrates.entities.exchanges.secondary.ErrorCodeException
import ru.exrates.entities.exchanges.secondary.Limit
import ru.exrates.entities.exchanges.secondary.LimitExceededException
import ru.exrates.utils.TimePeriodListSerializer
import java.net.ConnectException
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet
import javax.annotation.PostConstruct
import javax.persistence.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.reflect.KClass

@Entity @Inheritance(strategy = InheritanceType.SINGLE_TABLE) @DiscriminatorColumn(name = "EXCHANGE_TYPE")
@JsonIgnoreProperties("id", "limits", "limitCode", "banCode", "sleepValueSeconds", "updatePeriod", "temporary",
    "webClient", "props")
abstract class BasicExchange(@javax.persistence.Transient protected val logger: Logger = LogManager.getLogger(BasicExchange::class)) : Exchange, Cloneable{


    var exId: Int = 0
    var temporary = true
    var limitCode: Int = 0
    var banCode: Int = 0
    var serverError: Int = 503
    var sleepValueSeconds = 30L

    @Autowired
    @javax.persistence.Transient
    lateinit var props: Properties

    @Id @GeneratedValue
    var id: Int = 0
    var name: String = ""

    @OneToMany(cascade = [CascadeType.PERSIST], fetch = FetchType.EAGER)
    @SortComparator(CurrencyPair.SortComparator::class)
    val pairs: SortedSet<CurrencyPair> = TreeSet<CurrencyPair>() //FIXMe duplicate pairs in response

    @ManyToMany(cascade = [CascadeType.PERSIST], fetch = FetchType.EAGER)
    @JsonSerialize(using = TimePeriodListSerializer::class)
    val changePeriods: MutableList<TimePeriod> = ArrayList()

    @OneToMany(orphanRemoval = true, cascade = [CascadeType.PERSIST], fetch = FetchType.EAGER)
    val limits: Set<Limit> = HashSet()

    lateinit var updatePeriod: Duration

    @Transient
    lateinit var webClient: WebClient

    @ElementCollection
    lateinit var historyPeriods: List<String>

    @PostConstruct
    fun init(){
        logger.debug("Postconstruct super $name")

        updatePeriod = Duration.ofMillis(props.timerPeriod())
        val task = object : TimerTask() {
            override fun run() {
                try {
                    task()
                }catch (e: RuntimeException) {
                    logger.debug(e)
                    this.cancel()
                }
            }

        }
        Timer().schedule(task, 10000, props.timerPeriod())
    }

    fun task(){
        logger.debug("$name task started with ${pairs.size} pairs")
        logger.debug("pairs in exchange: ${pairs.joinToString { it.symbol }}")
        /*synchronized(pairs){*/
            for (p in pairs){
                try {
                    currentPrice(p, updatePeriod)
                    priceChange(p, updatePeriod)
                    priceHistory(p, changePeriods[0].name, 10) //todo [0] right?
                }catch (e: LimitExceededException){
                    logger.error(e.message)
                    sleepValueSeconds *= 2
                    Thread.sleep(sleepValueSeconds)
                    task()
                    return
                }catch (e: ErrorCodeException){ logger.error(e.message)
                }catch (e: BanException){
                    logger.error(e.message)
                    throw RuntimeException("You are banned from $name")
                }catch (e: ConnectException){
                    logger.error(e)
                }
            }
        /*}*/
    }

    fun dataElasped(pair: CurrencyPair, timeout: Duration, idx: Int): Boolean{
        logger.debug("Pair $pair was updated on field $idx ${Instant.ofEpochMilli(pair.updateTimes[idx])} | now is ${Instant.now()}")
        return Instant.now().isAfter(Instant.ofEpochMilli(pair.updateTimes[idx] + timeout.toMillis()))
    }





    override fun insertPair(pair: CurrencyPair) {
        pairs.add(pair)
        if(pairs.size > props.maxSize()) {
            pairs.last().exchange = null
            pairs.remove(pairs.last())
        }
    }

    override fun getPair(c1: String, c2: String): CurrencyPair? = pairs.find{c1 == it.baseCurrency && c2 == it.quoteCurrency}

    override fun getPair(pairName: String): CurrencyPair? = pairs.find { it.symbol == pairName }

    public override fun clone(): Any {
        return super.clone()
    }


    abstract fun currentPrice(pair: CurrencyPair, timeout: Duration)

    abstract fun priceChange(pair: CurrencyPair, timeout: Duration)

    fun priceHistory(pair: CurrencyPair, interval: String, limit: Int){
        if (!this.historyPeriods.contains(interval)) {
            logger.error("History period $interval incorrect for ${this.name} exchange")
            return
        }
    }


}

class ExchangeDTO(exchange: BasicExchange?){
    val exId = exchange?.exId ?: 0
    val name = exchange?.name ?: ""
    @JsonSerialize(using = TimePeriodListSerializer::class)
    val changePeriods = exchange?.changePeriods ?: listOf<TimePeriod>()
    val historyPeriods = exchange?.historyPeriods ?: emptyList()
    var pairs: SortedSet<CurrencyPair> = TreeSet<CurrencyPair>(exchange?.pairs ?: TreeSet<CurrencyPair>())
    var status: Int = 200
    init {
       if(exchange == null) status = 400
    }


    override fun toString(): String {
        return "\n$name exId = $exId pairs: ${pairs.joinToString { it.symbol }}"
    }

}