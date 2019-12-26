package ru.exrates.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import ru.exrates.repos.DurationConverter
import ru.exrates.utils.TimePeriodSerializer
import java.time.Duration
import javax.persistence.*

@Entity
@Table(name = "change_periods")
@JsonSerialize(using = TimePeriodSerializer::class)
class TimePeriod(){
    @JsonIgnore @Convert(converter = DurationConverter::class) @Column(nullable = false)
    lateinit var period: Duration

    @Column(nullable = false, unique = true)
    lateinit var name: String

    @Id @GeneratedValue 
    var id: Int = 0

    constructor(period: Duration, name: String) : this(){
        this.period = period
        this.name = name
    }

    override fun toString() =  "TimePeriod{ id = $id, period = $period, name = $name}"

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TimePeriod) return false
        return name == other.name
    }
}







enum class LimitType{REQUEST, WEIGHT}