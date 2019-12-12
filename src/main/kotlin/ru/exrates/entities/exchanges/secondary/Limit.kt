package ru.exrates.entities.exchanges.secondary

import ru.exrates.repos.DurationConverter
import java.time.Duration
import javax.persistence.*

@Entity
@Table(name = "Limits")
class Limit(
    private val name: String,
    @Column(length = 20) @Enumerated(value = EnumType.STRING)
    private val type: LimitType,
    @Convert(converter = DurationConverter::class) @Column(name = "_interval")
    private val interval: Duration,
    private val limitValue: Int,
    @Id @GeneratedValue
    private val id: Int = 0
)