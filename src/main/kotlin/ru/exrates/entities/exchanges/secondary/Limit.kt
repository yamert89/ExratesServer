package ru.exrates.entities.exchanges.secondary;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.exrates.repos.DurationConverter;

import javax.persistence.*;
import java.time.Duration;

@Entity
@Table(name = "Limits")
@NoArgsConstructor
public class Limit {
    @Id
    @GeneratedValue
    private Integer id;
    @Getter @Setter private String name;
            @Column(name = "_interval") @Convert(converter = DurationConverter.class)
            @Getter private Duration interval;
            @Enumerated(value = EnumType.STRING)
            @Column(length = 20)
            @Getter private LimitType type;
    @Getter @Setter private int limitValue;


    public Limit(String name, LimitType type, Duration interval, int limit) {
        this.name = name;
        this.type = type;
        this.limitValue = limit;
        this.interval = interval;
    }

}
