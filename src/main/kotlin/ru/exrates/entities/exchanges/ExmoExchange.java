package ru.exrates.entities.exchanges;

import org.springframework.boot.configurationprocessor.json.JSONException;
import ru.exrates.entities.CurrencyPair;
import ru.exrates.entities.exchanges.secondary.exceptions.BanException;
import ru.exrates.entities.exchanges.secondary.exceptions.ErrorCodeException;
import ru.exrates.entities.exchanges.secondary.exceptions.LimitExceededException;

import java.time.Duration;
import java.util.Map;

public class ExmoExchange extends BasicExchange {

    static {
        URL_ENDPOINT = "https://api.exmo.com/v1";
        URL_INFO = "/pair_settings/";
        URL_PRICE_CHANGE = "/order_book/";
        URL_CURRENT_AVG_PRICE = "/ticker/";

    }

    @Override
    protected void init() {
        super.init();

    }

    @Override
    protected void task() throws RuntimeException {

    }

    @Override
    public void currentPrice(CurrencyPair pair, Duration timeout) throws JSONException, NullPointerException, LimitExceededException, ErrorCodeException, BanException {

    }

    @Override
    public void priceChange(CurrencyPair pair, Duration timeout, Map<String, String> uriVariables) throws JSONException, LimitExceededException, ErrorCodeException, BanException {

    }

    @Override
    public void priceChange(CurrencyPair pair, Duration timeout) throws JSONException, LimitExceededException, ErrorCodeException, BanException {

    }
}
