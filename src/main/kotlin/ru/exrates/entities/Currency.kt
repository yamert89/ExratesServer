package ru.exrates.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.utils.ExchangeSerializer
import ru.exrates.utils.TimePeriodSerializer
import java.time.Instant
import java.util.*
import javax.persistence.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.jvm.Transient

@Entity
@JsonIgnoreProperties("id", "lastUse", "updateTimes")
data class CurrencyPair(var lastUse: Instant = Instant.now(), @Transient @JsonIgnore val logger: Logger = LogManager.getLogger(CurrencyPair::class)) : Comparable<CurrencyPair>{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int = 0

    @Column(/*unique = true*/)
    lateinit var symbol: String

    @Column
    lateinit var baseCurrency: String

    @Column
    lateinit var quoteCurrency: String

    var price: Double = 0.0
        set(value) {
            updateTimes.priceTime = Instant.now().toEpochMilli()
            field = value
        }

    @ElementCollection(fetch = FetchType.EAGER) @MapKeyColumn(name = "PERIOD") @Column(name = "VALUE")
    @JsonSerialize(keyUsing = TimePeriodSerializer::class)
    private val priceChange: MutableMap<TimePeriod, Double> = HashMap()

    //@ElementCollection(fetch = FetchType.EAGER)
    @javax.persistence.Transient
    val priceHistory: MutableList<Double> = ArrayList()
        get() {
            lastUse = Instant.now()
            //logger.trace("last use of $symbol updated")
            return field
        }

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonSerialize(using = ExchangeSerializer::class)
    @JsonProperty("exchangeName")
    var exchange: BasicExchange? = null

    var exId: Int = 0

    lateinit var updateTimes: UpdateTimes
        private set

    @javax.persistence.Transient
    var historyPeriods : List<String>? = null //todo delete

    constructor(curBase: String, curQuote: String, symbol: String, exchange: BasicExchange): this() {
        baseCurrency = curBase
        quoteCurrency = curQuote
        this.symbol = symbol
        this.exchange = exchange
        exId = exchange.exId
        updateTimes = UpdateTimes(exchange.taskTimeOut)
    }

    @JsonIgnore
    fun getUnmodPriceChange(): Map<TimePeriod, Double> {
        lastUse = Instant.now()
        return priceChange.toMap()
    }

    fun putInPriceChange(period: TimePeriod, value: Double){
        updateTimes.priceChangeTimes[period.name] = Instant.now().toEpochMilli()
        priceChange[period] = value
    }

    /*fun removeFromPriceChange(period: TimePeriod){
        updateTimes[1] = Instant.now().toEpochMilli()
        priceChange.remove(period)
    }*/

    fun getPriceChangeValue(period: TimePeriod) = priceChange[period] //todo updateTimes?

    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(other == null || this::class != other::class) return false
        val pair = other as CurrencyPair
        return symbol == pair.symbol && exId == other.exId
    }

    override fun hashCode(): Int {
        val hash = Objects.hash(symbol, exId, 142)
        //logger.trace("hash for $symbol = $hash")
        return hash
    }

    class SortComparator: Comparator<CurrencyPair>{
        override fun compare(o1: CurrencyPair?, o2: CurrencyPair?): Int {
            if (o1 == null || o2 == null) throw NullPointerException("Comparing null element")
            if (o1.symbol == o2.symbol) return 0
            if (o1.lastUse.toEpochMilli() == o2.lastUse.toEpochMilli()) return o1.symbol.compareTo(o2.symbol)
            return if (o1.lastUse.isAfter(o2.lastUse)) 1 else -1
        }

    }

    override fun toString(): String {
        return "$symbol, exId: $exId lastuse: $lastUse"
    }

    override fun compareTo(other: CurrencyPair): Int {
        return SortComparator().compare(this, other)
    }
}





