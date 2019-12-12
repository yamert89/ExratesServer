package ru.exrates.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.exrates.repos.DurationConverter;
import ru.exrates.utils.JsonSerializers;

import javax.persistence.*;
import java.time.Duration;


@Entity @Table(name = "change_periods")
@NoArgsConstructor
@JsonSerialize(using = JsonSerializers.TimePeriodSerializer.class)
public class TimePeriod {
    @Id
    @GeneratedValue
    @Getter
    private Integer id;

    @Getter @Setter
    @Column(nullable = false) @Convert(converter = DurationConverter.class)
    @JsonIgnore
    private Duration period;

    @Getter @Setter
    @Column(nullable = false, unique = true)
    private String name;

    public TimePeriod(Duration period, String name) {
        this.period = period;
        this.name = name;

    }

    @Override
    public String toString() {
        return "TimePeriod{" +
                "id=" + id +
                ", period=" + period +
                ", name='" + name + '\'' +
                '}';
    }
}
