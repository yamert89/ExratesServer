package ru.exrates.entities

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.utils.JsonSerializers
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import javax.persistence.*

data class Currency(val name: String, val symbol: String)

data class CurrencyPair(val lastUse: Instant = Instant.now()){
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int,
    @Column(unique = true) val symbol: String,
    val price: Double,

    @ElementCollection(fetch = FetchType.EAGER) @MapKeyColumn(name = "PERIOD") @Column(name = "VALUE")
    @JsonSerialize(keyUsing = JsonSerializers.TimePeriodListSerializer::class)
    val priceChange: Map<TimePeriod, Double>,
    @ElementCollection(fetch = FetchType.EAGER)
    val priceHistory: Collection<Double> = ArrayBlockingQueue(20, true), //todo

    @ManyToOne(fetch = FetchType.LAZY)
    val exchange: BasicExchange
    /*
      indexes:
        0 - price
        1 - priceChange
        2 - priceHistory
     */
    val updateTimes: LongArray = LongArray(3)
}





