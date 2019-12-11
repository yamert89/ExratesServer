package ru.exrates.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;
import ru.exrates.entities.exchanges.BasicExchange;
import ru.exrates.utils.JsonSerializers;

import javax.persistence.*;
import java.time.Instant;
import java.util.Currency;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

@Entity
@JsonIgnoreProperties({"id", "exchange", "lastUse"})
public class CurrencyPair implements Comparable<CurrencyPair>{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private int id;

    @Setter
    @Column(unique = true, nullable = false)
    private String symbol;

    private double price;

    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name = "PERIOD")
    @Column(name = "VALUE")
    @JsonSerialize(keyUsing = JsonSerializers.TimePeriodSerializer.class)
    private Map<TimePeriod, Double> priceChange = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    private Collection<Double> priceHistory = new ArrayBlockingQueue<>(20, true); //todo

    @Getter @Setter
    private Instant lastUse = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @Getter
    private BasicExchange exchange;

    /*
      indexes:
        0 - price
        1 - priceChange
        2 - priceHistory
     */
    @Getter
    private long[] updateTimes = new long[3];

    public CurrencyPair(Currency currency1, Currency currency2) {
        symbol = currency1.getSymbol() + currency2.getSymbol();
    }

    public CurrencyPair(String symbol, BasicExchange exchange) {
        this.symbol = symbol;
        this.exchange = exchange;
    }

    public CurrencyPair(){
        lastUse = Instant.now();
    }

    public CurrencyPair(String pname) {
        symbol = pname;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public int compareTo(CurrencyPair o) {
        return lastUse.isAfter(o.getLastUse()) ? 1 : -1;
    }

    @Override
    public String toString() {
        return symbol + " " + lastUse;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CurrencyPair pair = (CurrencyPair) o;
        return symbol.equals(pair.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, 33);
    }

    public double getPrice() {
        lastUse = Instant.now();
        return price;
    }

    public void setPrice(double price) {
        updateTimes[0] = Instant.now().toEpochMilli();
        this.price = price;
    }

    public Map<TimePeriod, Double> getPriceChange() {
        lastUse = Instant.now();
        return Collections.unmodifiableMap(priceChange);
    }

    public void putInPriceChange(TimePeriod period, Double value){
       updateTimes[1] = Instant.now().toEpochMilli();
        priceChange.put(period, value);
    }

    public void removeFromPriceChange(TimePeriod period){
       updateTimes[1] = Instant.now().toEpochMilli();
       priceChange.remove(period);
    }

    public Collection<Double> getPriceHistory() {
        lastUse = Instant.now();
        return priceHistory;
    }

    public static class CurComparator implements Comparator<CurrencyPair> {

        @Override
        public int compare(CurrencyPair o1, CurrencyPair o2) {
            if(o1.getSymbol().equals(o2.getSymbol())) return 0;
            if (o1.lastUse.toEpochMilli() == o2.lastUse.toEpochMilli()) return o1.getSymbol().compareTo(o2.getSymbol());
            return  o1.lastUse.isAfter(o2.getLastUse()) ? 1 : -1;
        }
    }


}
