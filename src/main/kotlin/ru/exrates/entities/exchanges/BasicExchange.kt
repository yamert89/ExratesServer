package ru.exrates.entities.exchanges

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import org.hibernate.annotations.SortComparator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.web.reactive.function.client.WebClient
import ru.exrates.configs.Properties
import ru.exrates.entities.CurrencyPair
import ru.exrates.entities.TimePeriod
import ru.exrates.entities.exchanges.secondary.BanException
import ru.exrates.entities.exchanges.secondary.ErrorCodeException
import ru.exrates.entities.exchanges.secondary.Limit
import ru.exrates.entities.exchanges.secondary.LimitExceededException
import ru.exrates.func.EndpointStateChecker
import ru.exrates.func.TaskHandler
import ru.exrates.repos.TimePeriodConverter
import ru.exrates.utils.ClientCodes
import ru.exrates.utils.TimePeriodListSerializer
import java.net.ConnectException
import java.time.Duration
import java.util.*
import javax.annotation.PostConstruct
import javax.persistence.*
import javax.xml.bind.JAXBElement
import kotlin.NullPointerException
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.jvm.Transient

@Entity @Inheritance(strategy = InheritanceType.SINGLE_TABLE) @DiscriminatorColumn(name = "EXCHANGE_TYPE")
@JsonIgnoreProperties("id", "limits", "limitCode", "banCode", "sleepValueSeconds", "taskTimeOut", "temporary",
    "props", "delimiter")
abstract class BasicExchange() : Exchange, Cloneable{
    var exId: Int = 0
    var temporary = true
    var limitCode: Int = 0
    var banCode: Int = 0
    var serverError: Int = 503
    var sleepValueSeconds = 30L

    @Autowired
    @javax.persistence.Transient
    lateinit var props: Properties

    @javax.persistence.Transient
    @Autowired
    lateinit var stateChecker: EndpointStateChecker

    @Autowired
    @javax.persistence.Transient
    lateinit var applicationContext: ApplicationContext

    @javax.persistence.Transient
    @Autowired
    lateinit var logger: Logger

    @Autowired
    @javax.persistence.Transient
    lateinit var taskHandler: TaskHandler

    @Id @GeneratedValue
    var id: Int = 0
    var name: String = ""
    var delimiter = ""

    @OneToMany(cascade = [CascadeType.PERSIST], fetch = FetchType.EAGER)
    @SortComparator(CurrencyPair.SortComparator::class)
    val pairs: SortedSet<CurrencyPair> = TreeSet<CurrencyPair>()

    @ManyToMany(cascade = [CascadeType.PERSIST], fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @JsonSerialize(using = TimePeriodListSerializer::class)
    val changePeriods: MutableList<TimePeriod> = ArrayList()

    @OneToMany(orphanRemoval = true, cascade = [CascadeType.PERSIST], fetch = FetchType.EAGER)
    val limits: MutableSet<Limit> = HashSet()

    @Convert(converter = TimePeriodConverter::class)
    lateinit var taskTimeOut: TimePeriod

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    lateinit var historyPeriods: List<String>

    @ElementCollection(fetch = FetchType.EAGER)
    val topPairs: MutableList<String> = LinkedList()


    /*
    * ******************************************************************************************************************
    *       Initialization
    * ******************************************************************************************************************
    * */

    @PostConstruct
    fun init(){
        logger.debug("Postconstruct super $name")
        taskTimeOut = TimePeriod(Duration.ofMillis(props.timerPeriod()), "BaseTaskTimeOut")
        GlobalScope.launch {
            launch(taskHandler.getExecutorContext()){
                delay(10000)
                repeat(Int.MAX_VALUE){
                    task()
                    delay(props.timerPeriod())
                }
            }
        }
    }

    fun task(){
        logger.debug("$name task started with ${pairs.size} pairs")
        logger.debug("pairs in exchange: ${pairs.joinToString { it.symbol }}")
            for (p in pairs){
                try {
                    with(taskHandler){
                        runTask { currentPrice(p, taskTimeOut) }
                        runTask { priceChange(p, taskTimeOut) }
                        runTask { priceHistory(p, changePeriods[0].name, 10) } //todo [0] right?
                    }

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
    }

    abstract override fun fillTop()

    /*
    * ******************************************************************************************************************
    *       Update methods
    * ******************************************************************************************************************
    * */

    abstract fun currentPrice(pair: CurrencyPair, period: TimePeriod)

    abstract fun priceChange(pair: CurrencyPair, interval: TimePeriod)

    fun priceHistory(pair: CurrencyPair, interval: String, limit: Int){
        if (!this.historyPeriods.contains(interval)) {
            logger.error("History period $interval incorrect for ${this.name} exchange")
            return
        }
    }


    /*
    * ******************************************************************************************************************
    *       Class methods
    * ******************************************************************************************************************
    * */

    override fun insertPair(pair: CurrencyPair) {
        pairs.add(pair)
        if(pairs.size > props.maxSize()) {
            pairs.last().exchange = EmptyExchange()
            pairs.remove(pairs.last())
        }
    }

    override fun getPair(c1: String, c2: String): CurrencyPair? = pairs.find{c1 == it.baseCurrency && c2 == it.quoteCurrency}

    override fun getPair(pairName: String): CurrencyPair? = pairs.find { it.symbol == pairName }

    fun getTimePeriod(period: String) = changePeriods.find { it.name == period } ?: throw NullPointerException("period in ${this.name} for $period not found")

    fun getTimePeriod(duration: Duration) = changePeriods.find { it.period == duration } ?: throw NullPointerException("period in ${this.name} for $duration not found")

}

class ExchangeDTO(exchange: BasicExchange?, exName: String = "", stat: Int = ClientCodes.SUCCESS){
    val exId = exchange?.exId ?: 0
    val name = exchange?.name ?: exName
    @JsonSerialize(using = TimePeriodListSerializer::class)
    val changePeriods = exchange?.changePeriods ?: listOf<TimePeriod>()
    val historyPeriods = exchange?.historyPeriods ?: emptyList()
    var pairs: SortedSet<CurrencyPair> = TreeSet<CurrencyPair>(exchange?.pairs ?: TreeSet<CurrencyPair>())
    var status: Int = stat
    init {
       if(exchange == null) this.status = ClientCodes.EXCHANGE_NOT_FOUND
    }

    override fun toString(): String {
        return "\n$name exId = $exId pairs: ${pairs.joinToString { it.symbol }}"
    }

}