package ru.exrates.entities.exchanges.secondary

import ru.exrates.entities.LimitType
import ru.exrates.repos.DurationConverter
import java.time.Duration
import javax.persistence.*

@Entity
@Table(name = "Limits")
class Limit(){
    lateinit var name: String
    @Column(length = 20) @Enumerated(value = EnumType.STRING)
    lateinit var type: LimitType
    @Convert(converter = DurationConverter::class) @Column(name = "_interval")
    lateinit var interval: Duration
    var limitValue: Int = 0
    @Id @GeneratedValue
    private val id: Int = 0

    constructor(name: String, type: LimitType, interval: Duration, limitValue: Int) : this(){
        this.name = name
        this.interval = interval
        this.limitValue = limitValue
        this.type = type
    }
}
    
