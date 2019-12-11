package ru.exrates.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.*;
import ru.exrates.entities.CurrencyPair;
import ru.exrates.entities.exchanges.Exchange;
import ru.exrates.func.Aggregator;
import ru.exrates.utils.JsonTemplates;

import java.io.IOException;
import java.util.Map;

@RestController
public class RestInfo {

    private final static Logger logger = LogManager.getLogger(RestInfo.class);

    private Aggregator aggregator;
    private ObjectMapper objectMapper;

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Autowired
    public void setAggregator(Aggregator aggregator) {
        this.aggregator = aggregator;
    }

    /*
        {"exchange" : "binanceExchange", "timeout": "3m", "pairs" : ["btcusd", "etcbtc"]}
        //todo pairs - list of favorite pairs in bd for each user
     */

    @PostMapping("/rest/exchange")
    public Exchange getExchange(@RequestBody JsonTemplates.ExchangePayload exchangePayload){
        //JsonTemplates.ExchangePayload exchangePayload = null;
        logger.debug("payload = " + exchangePayload);
        try {
            //exchangePayload = objectMapper.readValue(payload, JsonTemplates.ExchangePayload.class);
            if (exchangePayload == null) throw new IOException();
        } catch (IOException e) {
            logger.error("error read Json" , e); //todo exc to client
            return null;
        }

        var ex = exchangePayload.getPairs().length > 0 ?
                aggregator.getExchange(exchangePayload.getExchange(), exchangePayload.getPairs(), exchangePayload.getTimeout()) :
                aggregator.getExchange(exchangePayload.getExchange());
        try {
            logger.debug("Exchange response: " + new ObjectMapper().writeValueAsString(ex));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return ex;


    }

    @GetMapping("/rest/pair")
    public Map<String, CurrencyPair> pair(@RequestParam(required = false) String c1, @RequestParam(required = false) String c2,
                                          @RequestParam(required = false) String pname){
        logger.debug("c1 = [" + c1 + "], c2 = [" + c2 + "], pname = [" + pname + "]");
        return pname == null ? aggregator.getCurStat(c1, c2) : aggregator.getCurStat(pname);
    }

    @GetMapping("/service/save")
    public void save(){
        aggregator.save();
    }

    @GetMapping("/close")
    public void close(){
        context.close();
    }

    /*
    Request:
    {
    "exchange" : "binanceExchange",
    "timeout": "3m",
    "pairs" : ["BTCUSDT", "ETCBTC"]
    }



    Response:
    {
    "changePeriods":["3m","5m","15m","30m","1h","4h","6h","8h","12h","1d","3d","1w","1M"],
    "name":"binanceExchange",
    "pairs":[
        {
            "symbol":"ERDPAX",
            "price":0.0012527,
            "priceChange":{
                "\"4h\"":0.0012527,
                "\"6h\"":0.0012527,
                "\"30m\"":0.0012527,
                "\"1M\"":0.00177985,
                "\"1d\"":0.0012527,
                "\"1h\"":0.0012527,
                "\"12h\"":0.0012527,
                "\"3d\"":0.00141975,
                "\"8h\"":0.0012527,
                "\"5m\"":0.0012527,
                "\"1w\"":0.0012527,
                "\"15m\"":0.0012527,
                "\"3m\"":0.0012527},
                "priceHistory":[],
                "lastUse":"2019-12-05T13:08:21.932122600Z",
                "updateTimes":[1575551295166,0,0]}
             ]
     }

    */

}
