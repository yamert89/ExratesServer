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
    "webClient", "props", "URL_ENDPOINT", "URL_CURRENT_AVG_PRICE", "URL_INFO", "URL_PRICE_CHANGE", "URL_PING", "URL_ORDER",
    "url_ENDPOINT", "url_CURRENT_AVG_PRICE", "url_INFO", "url_PRICE_CHANGE", "url_PING", "url_ORDER")
abstract class BasicExchange(@javax.persistence.Transient protected val logger: Logger = LogManager.getLogger(BasicExchange::class)) : Exchange, Cloneable{

    lateinit var URL_ENDPOINT: String
    lateinit var URL_CURRENT_AVG_PRICE: String
    lateinit var URL_INFO: String
    lateinit var URL_PRICE_CHANGE: String
    lateinit var URL_PING: String
    lateinit var URL_ORDER: String
    var exId: Int = 0
    var temporary = true
    var limitCode: Int = 0
    var banCode: Int = 0
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
        webClient = WebClient.create(URL_ENDPOINT)
        updatePeriod = Duration.ofMillis(props.timerPeriod())
        val task = object : TimerTask() {
            override fun run() {
                try {
                    task()
                }catch (e: RuntimeException) {
                    logger.debug(e.message)
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
                    priceHistory(p, historyPeriods[0], 10) //todo [0] right?
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
                }
            }
        /*}*/
    }

    fun dataElasped(pair: CurrencyPair, timeout: Duration, idx: Int): Boolean{
        logger.debug("Pair $pair was updated on field $idx ${Instant.ofEpochMilli(pair.updateTimes[idx])} | now is ${Instant.now()}")
        return Instant.now().isAfter(Instant.ofEpochMilli(pair.updateTimes[idx] + timeout.toMillis()))
    }

    fun <T: Any> request(uri: String, clazz: KClass<T>) : T{
        logger.trace("Try request to : $uri")
        return webClient.get().uri(uri).retrieve().onStatus(HttpStatus::is4xxClientError) { resp ->
            val ex = when(resp.statusCode().value()){
                banCode -> BanException()
                limitCode -> LimitExceededException(LimitType.WEIGHT)
                //null -> NullPointerException()
                else -> IllegalStateException("Unexpected value: ${resp.statusCode().value()}")
            }
            Mono.error(ex) }
            .bodyToMono(clazz.java).block()!! //todo 1 - null compile notif? // 2 - todo operate exception
    }

    fun stringResponse(uri: String) = request(uri, String::class)



    override fun insertPair(pair: CurrencyPair) {
        pairs.add(pair)
        if(pairs.size > props.maxSize()) {
            pairs.last().exchange = null
            pairs.remove(pairs.last())
        }
    }

    override fun getPair(c1: String, c2: String): CurrencyPair? {
        var pair: CurrencyPair? = null
        pairs.spliterator().forEachRemaining { if(it.symbol == c1 + c2) pair = it }
        return pair
    }

    override fun getPair(pairName: String): CurrencyPair? {
        var pair: CurrencyPair? = null
        pairs.spliterator().forEachRemaining {  if(it.symbol == pairName) pair = it}
        return pair
    }

    public override fun clone(): Any {
        return super.clone()
    }


    abstract fun currentPrice(pair: CurrencyPair, timeout: Duration)

    abstract fun priceChange(pair: CurrencyPair, timeout: Duration)

    abstract fun priceHistory(pair: CurrencyPair, interval: String, limit: Int)


}

class ExchangeDTO(exchange: BasicExchange?){
    val exId = exchange?.exId ?: 0
    val name = exchange?.name ?: ""
    @JsonSerialize(using = TimePeriodListSerializer::class)
    val changePeriods = exchange?.changePeriods ?: listOf(TimePeriod())
    val historyPeriods = exchange?.historyPeriods ?: emptyList()
    var pairs: SortedSet<CurrencyPair> = TreeSet<CurrencyPair>(exchange?.pairs ?: TreeSet<CurrencyPair>())

    override fun toString(): String {
        return "$name exId = $exId pairs: ${pairs.joinToString { it.symbol }}\n"
    }

}