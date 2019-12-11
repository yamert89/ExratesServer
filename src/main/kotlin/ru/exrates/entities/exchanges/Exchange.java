package ru.exrates.entities.exchanges;

import org.springframework.stereotype.Component;
import ru.exrates.entities.Currency;
import ru.exrates.entities.CurrencyPair;

@Component
public interface Exchange {
    void insertPair(CurrencyPair pair);
    CurrencyPair getPair(Currency c1, Currency c2);
    CurrencyPair getPair(String pairName);

}
