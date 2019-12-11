package ru.exrates.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@NoArgsConstructor
public class Currency {
    @Getter @Setter
    private String name;

    @Getter @Setter
    private String symbol;


    public Currency(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Currency currency = (Currency) o;
        return symbol.equals(currency.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol);
    }
}
