package ru.exrates.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import ru.exrates.repos.DurationConverter
import ru.exrates.utils.JsonSerializers.TimePeriodSerializer
import java.time.Duration
import javax.persistence.*

@Entity
@Table(name = "change_periods")
@JsonSerialize(using = TimePeriodSerializer::class)
data class TimePeriod(
    @JsonIgnore @Convert(converter = DurationConverter::class) @Column(nullable = false)
    private val period: Duration,
    @Column(nullable = false, unique = true)
    val name: String,
    @Id @GeneratedValue var id: Int = 0) {

    override fun toString() =  "TimePeriod{ id = $id, period = $period, name = $name}"

}

enum class LimitType{REQUEST, WEIGHT}