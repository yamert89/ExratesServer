package ru.exrates.entities.exchanges;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.web.reactive.function.client.WebClient;
import ru.exrates.entities.CurrencyPair;
import ru.exrates.entities.TimePeriod;
import ru.exrates.entities.exchanges.secondary.*;
import ru.exrates.entities.exchanges.secondary.exceptions.BanException;
import ru.exrates.entities.exchanges.secondary.exceptions.ErrorCodeException;
import ru.exrates.entities.exchanges.secondary.exceptions.LimitExceededException;

import javax.annotation.PostConstruct;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;


@Entity @DiscriminatorValue("binance")
public class BinanceExchange extends BasicExchange {
    private final static Logger logger = LogManager.getLogger(BinanceExchange.class);

    //https://github.com/binance-exchange/binance-official-api-docs/blob/master/rest-api.md#general-api-information

    static {
        URL_ENDPOINT = "https://api.binance.com";
        URL_CURRENT_AVG_PRICE = "/api/v3/avgPrice"; //todo /api/v3/ticker/price ?
        URL_INFO = "/api/v1/exchangeInfo";
        URL_PRICE_CHANGE = "/api/v1/klines";
        URL_PING = "/api/v1/ping";
        URL_ORDER = "/api/v3/depth";
    }

    public BinanceExchange() {
        super();
    }

    @Override
    protected void task () throws RuntimeException{
        if (getId() == null) return;
        var resp = restTemplate.getForEntity(URL_ENDPOINT + URL_PING, String.class).getStatusCode().value();
        if (resp != 200) return;
        super.task();
    }

    @Override
    public void currentPrice (CurrencyPair pair, Duration timeout)
            throws JSONException, NullPointerException, LimitExceededException, ErrorCodeException, BanException { //todo timeout
        if (!dataElapsed(pair, timeout, 0)) {
            logger.debug("current price req skipped");
            return;
        }
        //var variables = new HashMap<String, String>();
        //variables.put("symbol", pair.getSymbol());
        var uri = URL_ENDPOINT + URL_CURRENT_AVG_PRICE + "?symbol=" + pair.getSymbol();
        var entity = new JSONObject(stringResponse(uri));
        var price = Double.parseDouble(entity.getString("price"));
        pair.setPrice(price);
        logger.debug(String.format("Price updated on %1$s pair | = %2$s", pair.getSymbol(), price));


    }

    @Override
    public void priceChange (CurrencyPair pair, Duration timeout)
            throws JSONException, LimitExceededException, ErrorCodeException, BanException {
        if (!dataElapsed(pair, timeout, 1)) {
            logger.debug("price change req skipped");
            return;
        }

        var symbol = "?symbol=" + pair.getSymbol();
        var period = "&interval=";
        String uri = "";
        for (TimePeriod per : changePeriods) {
            uri = URL_ENDPOINT + URL_PRICE_CHANGE +  symbol + period + per.getName() + "&limit=1";
            var entity = new JSONArray(stringResponse(uri));
            var array = entity.getJSONArray(0);
            var changeVol = (array.getDouble(2) + array.getDouble(3)) / 2;
            pair.putInPriceChange(per, changeVol);
            logger.debug(String.format("Change period updated on %1$s pair, interval = %2$s | change = %3$s", pair.getSymbol(), per.getName(), changeVol));
        }
    }

    @Override
    public void priceChange (CurrencyPair pair, Duration timeout, Map<String, String> uriVariables) //todo limit > 1 logic
            throws JSONException, LimitExceededException, ErrorCodeException, BanException{
        if (!dataElapsed(pair, timeout, 1)) return;
        for (TimePeriod per : changePeriods) {
            var entity = new JSONArray(stringResponse(URL_PRICE_CHANGE));
            var array = entity.getJSONArray(0);
            pair.putInPriceChange(per, (array.getDouble(2) + array.getDouble(3)) / 2);
        }
    }

    @PostConstruct
    @Override
    protected void init(){
        limitCode = 429;
        banCode = 418;
        //restTemplate.setLimitCode(limitCode);
        //restTemplate.setBanCode(banCode);
        webClient = WebClient.create(URL_ENDPOINT);
        if (!temporary) return;
        name = "binanceExchange";
        limits = new HashSet<>();
        changePeriods = new ArrayList<>();
        Collections.addAll(changePeriods,
                new TimePeriod(Duration.ofMinutes(3), "3m"),
                new TimePeriod(Duration.ofMinutes(5), "5m"),
                new TimePeriod(Duration.ofMinutes(15), "15m"),
                new TimePeriod(Duration.ofMinutes(30), "30m"),
                new TimePeriod(Duration.ofHours(1), "1h"),
                new TimePeriod(Duration.ofHours(4), "4h"),
                new TimePeriod(Duration.ofHours(6), "6h"),
                new TimePeriod(Duration.ofHours(8), "8h"),
                new TimePeriod(Duration.ofHours(12), "12h"),
                new TimePeriod(Duration.ofDays(1), "1d"),
                new TimePeriod(Duration.ofDays(3), "3d"),
                new TimePeriod(Duration.ofDays(7), "1w"),
                new TimePeriod(Duration.ofDays(30), "1M"));


        try {
            var entity = new JSONObject(stringResponse(URL_ENDPOINT + URL_INFO));


            JSONArray symbols = null;
            var array = entity.getJSONArray("rateLimits");

            limits.add(new Limit("MINUTE", LimitType.WEIGHT, Duration.ofMinutes(1), 1200));

            for (int i = 0; i < array.length(); i++) {
                var ob = array.getJSONObject(i);
                for (Limit limit : limits) {
                    var name = ob.getString("interval");
                    if (name.equals(limit.getName())) {
                        limit.setLimitValue(ob.getInt("limit"));
                    }
                }

            }

            symbols = entity.getJSONArray("symbols");
            for (int i = 0; i < symbols.length(); i++) {
                pairs.add(new CurrencyPair(symbols.getJSONObject(i).getString("symbol"), this));
            }

            temporary = false;
            logger.debug("exchange " + name + "initialized with " + pairs.size() + " pairs");
        } catch (JSONException e) {
            logger.error("task JSON E", e);
        } catch (LimitExceededException e) {
            logger.error(e.getMessage());
        } catch (BanException e){
            logger.error(e.getMessage());
        } catch (NullPointerException e){
            logger.error("NPE init");
        } catch (Exception e){
            logger.error("Unknown exc in init", e);
        }
        super.init();


    }

    private String stringResponse(String uri) throws IllegalStateException, BanException, LimitExceededException{
        return super.request(uri, String.class);
    }
}




    /*
    {
  "timezone": "UTC",
  "serverTime": 1565246363776,
  "rateLimits": [
    {
      //These are defined in the `ENUM definitions` section under `Rate Limiters (rateLimitType)`.
      //All limits are optional
    }
  ],
  "exchangeFilters": [
    //These are the defined filters in the `Filters` section.
    //All filters are optional.
  ],
  "symbols": [
    {
      "symbol": "ETHBTC",
      "status": "TRADING",
      "baseAsset": "ETH",
      "baseAssetPrecision": 8,
      "quoteAsset": "BTC",
      "quotePrecision": 8,
      "orderTypes": [
        "LIMIT",
        "LIMIT_MAKER",
        "MARKET",
        "STOP_LOSS",
        "STOP_LOSS_LIMIT",
        "TAKE_PROFIT",
        "TAKE_PROFIT_LIMIT"
      ],
      "icebergAllowed": true,
      "ocoAllowed": true,
      "isSpotTradingAllowed": true,
      "isMarginTradingAllowed": false,
      "filters": [
        //These are defined in the Filters section.
        //All filters are optional
      ]
    }
  ]
}
     */

