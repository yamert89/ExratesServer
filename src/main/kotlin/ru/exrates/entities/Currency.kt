package ru.exrates.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.utils.TimePeriodListSerializer
import ru.exrates.utils.TimePeriodSerializer
import java.time.Instant
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import javax.persistence.*
import kotlin.collections.HashMap

data class Currency(val name: String, val symbol: String)
@Entity
@JsonIgnoreProperties("id", "exchange", "lastUse")
data class CurrencyPair(var lastUse: Instant = Instant.now()) : Comparable<CurrencyPair>{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int = 0

    @Column(unique = true)
    lateinit var symbol: String

    var price: Double = 0.0
        set(value) {
            updateTimes[0] = Instant.now().toEpochMilli()
            field = value
        }

    @ElementCollection(fetch = FetchType.EAGER) @MapKeyColumn(name = "PERIOD") @Column(name = "VALUE")
    @JsonSerialize(keyUsing = TimePeriodSerializer::class)
    private val priceChange: MutableMap<TimePeriod, Double> = HashMap()

    @ElementCollection(fetch = FetchType.EAGER)
    val priceHistory: Collection<Double> = ArrayBlockingQueue(20, true) //todo
        get() {
            lastUse = Instant.now()
            return field
        }

    @ManyToOne(fetch = FetchType.LAZY)
    lateinit var exchange: BasicExchange
    /*
      indexes:
        0 - price
        1 - priceChange
        2 - priceHistory
     */
    val updateTimes: LongArray = LongArray(3)

    constructor(cur1: Currency, cur2: Currency): this() {symbol = cur1.symbol + cur2.symbol}
    constructor(symbol: String, exchange: BasicExchange): this() {
        this.symbol = symbol
        this.exchange = exchange
    }

    @JsonIgnore
    fun getUnmodPriceChange(): Map<TimePeriod, Double> {
        lastUse = Instant.now()
        return priceChange.toMap()
    }

    fun putInPriceChange(period: TimePeriod, value: Double){
        updateTimes[1] = Instant.now().toEpochMilli()
        priceChange[period] = value
    }

    fun removeFromPriceChange(period: TimePeriod){
        updateTimes[1] = Instant.now().toEpochMilli()
        priceChange.remove(period)
    }

    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(other == null || this::class != other::class) return false
        val pair = other as CurrencyPair
        return symbol == pair.symbol
    }

    override fun hashCode() = Objects.hash(symbol, 142)

    class SortComparator: Comparator<CurrencyPair>{
        override fun compare(o1: CurrencyPair?, o2: CurrencyPair?): Int {
            if (o1 == null || o2 == null) throw NullPointerException("Comparing null element")
            if (o1.symbol == o2.symbol) return 0
            if (o1.lastUse.toEpochMilli() == o2.lastUse.toEpochMilli()) return o1.symbol.compareTo(o2.symbol)
            return if (o1.lastUse.isAfter(o2.lastUse)) 1 else -1
        }

    }

    override fun toString(): String {
        return "$symbol, lastuse: $lastUse"
    }

    override fun compareTo(other: CurrencyPair): Int {
        return SortComparator().compare(this, other)
    }
}





