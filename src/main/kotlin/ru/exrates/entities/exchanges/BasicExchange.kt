package ru.exrates.entities.exchanges

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.hibernate.annotations.SortComparator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import ru.exrates.configs.Properties
import ru.exrates.entities.Currency
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
import javax.annotation.PostConstruct

import javax.persistence.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.jvm.Transient
import kotlin.reflect.KClass

@Entity @Inheritance(strategy = InheritanceType.SINGLE_TABLE) @DiscriminatorColumn(name = "EXCHANGE_TYPE")
@JsonIgnoreProperties("id", "limits", "limitcode", "banCode", "sleepValueSeconds", "updatePeriod", "temporary", "webClient")
abstract class BasicExchange(@javax.persistence.Transient protected val logger: Logger = LogManager.getLogger(BasicExchange::class)) : Exchange{

    var temporary = true
    var limitCode: Int = 0
    var banCode: Int = 0
    var sleepValueSeconds = 30L
    @Autowired
    @javax.persistence.Transient
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
    @SortComparator(CurrencyPair.SortComparator::class)
    val pairs: SortedSet<CurrencyPair> = TreeSet()

    @ManyToMany(cascade = [CascadeType.PERSIST], fetch = FetchType.EAGER)
    @JsonSerialize(using = TimePeriodListSerializer::class)
    val changePeriods: MutableList<TimePeriod> = ArrayList()

    @OneToMany(orphanRemoval = true, cascade = [CascadeType.PERSIST], fetch = FetchType.EAGER)
    val limits: Set<Limit> = HashSet()

    lateinit var updatePeriod: Duration

    @Transient
    lateinit var webClient: WebClient

    @PostConstruct
    fun init(){
        logger.debug("Postconstruct $name")
        updatePeriod = Duration.ofMillis(props.timerPeriod())
        val task = object : TimerTask() {
            override fun run() {
                try {
                    task()
                }catch (e: java.lang.RuntimeException) {
                    logger.error(e)
                    logger.error(e.message)
                }
            }

        }
        Timer().schedule(task, 10000, props.timerPeriod())
    }

    fun task(){
        logger.debug("$name task started...")
        synchronized(pairs){
            for (p in pairs){
                try {
                    currentPrice(p, updatePeriod)
                    priceChange(p, updatePeriod)
                }catch (e: LimitExceededException){
                    logger.error(e.message)
                    sleepValueSeconds *= 2
                    Thread.sleep(sleepValueSeconds)
                    task()
                    return
                }catch (e: ErrorCodeException){ logger.error(e.message)}
                catch (e: BanException){
                    logger.error(e.message)
                    throw RuntimeException("You are banned from $name")
                }
            }
        }
    }

    fun dataElasped(pair: CurrencyPair, timeout: Duration, idx: Int): Boolean{
        logger.debug("Pair $pair was updated on field $idx ${Instant.ofEpochMilli(pair.updateTimes[idx])} | now is ${Instant.now()}")
        return Instant.now().isAfter(Instant.ofEpochMilli(pair.updateTimes[idx] + timeout.toMillis()))
    }

    fun <T: Any> request(uri: String, clazz: KClass<T>) : T{
        logger.debug("Try request to : $uri")
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



    override fun insertPair(pair: CurrencyPair) {
        pairs.add(pair)
        if(pairs.size > props.maxSize()) pairs.remove(pairs.first())
    }

    override fun getPair(c1: Currency, c2: Currency): CurrencyPair? {
        var pair: CurrencyPair? = null
        pairs.spliterator().forEachRemaining { if(it.symbol == c1.symbol + c2.symbol) pair = it }
        return pair
    }

    override fun getPair(pairName: String): CurrencyPair? {
        var pair: CurrencyPair? = null
        pairs.spliterator().forEachRemaining {  if(it.symbol == pairName) pair = it}
        return pair
    }

    abstract fun currentPrice(pair: CurrencyPair, timeout: Duration)

    abstract fun priceChange(pair: CurrencyPair, timeout: Duration)

}