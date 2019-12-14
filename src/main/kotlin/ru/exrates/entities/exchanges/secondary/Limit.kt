package ru.exrates.entities.exchanges.secondary

import ru.exrates.entities.LimitType
import ru.exrates.repos.DurationConverter
import java.time.Duration
import javax.persistence.*

@Entity
@Table(name = "Limits")
class Limit(
    val name: String,
    @Column(length = 20) @Enumerated(value = EnumType.STRING)
    private val type: LimitType,
    @Convert(converter = DurationConverter::class) @Column(name = "_interval")
    private val interval: Duration,
    var limitValue: Int,
    @Id @GeneratedValue
    private val id: Int = 0
)