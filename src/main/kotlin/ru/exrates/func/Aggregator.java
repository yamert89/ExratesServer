package ru.exrates.func;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.stereotype.Component;
import ru.exrates.entities.CurrencyPair;
import ru.exrates.entities.exchanges.BasicExchange;
import ru.exrates.entities.exchanges.BinanceExchange;
import ru.exrates.entities.exchanges.Exchange;
import ru.exrates.entities.exchanges.secondary.Limit;
import ru.exrates.entities.exchanges.secondary.exceptions.BanException;
import ru.exrates.entities.exchanges.secondary.exceptions.ErrorCodeException;
import ru.exrates.entities.exchanges.secondary.exceptions.LimitExceededException;
import ru.exrates.repos.ExchangeService;

import javax.annotation.PostConstruct;
import java.util.*;

@Component
public class Aggregator {
    private final static Logger logger = LogManager.getLogger(Aggregator.class);
    private Map<String, BasicExchange> exchanges;
    private Map<String, Class<? extends BasicExchange>> exchangeNames = new HashMap<>();
    private ExchangeService exchangeService;
    private ApplicationContext applicationContext;
    private GenericApplicationContext genericApplicationContext;
    private Properties props;


    {
        exchangeNames.put("binanceExchange", BinanceExchange.class);
    }

    @Autowired
    public void setProps(Properties props) {
        this.props = props;
    }

    @Autowired
    public void setGenericApplicationContext(GenericApplicationContext genericApplicationContext) {
        this.genericApplicationContext = genericApplicationContext;
    }

    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Autowired
    public void setExchangeService(ExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    public Aggregator() {
        exchanges = new HashMap<>();
    }

    @PostConstruct
    private void init(){
        try{
        logger.debug("Postconstruct aggregator init");
        logger.debug(exchangeService);
        for (var set : exchangeNames.entrySet()) {
            BasicExchange exchange = exchangeService.find(set.getKey());
            int pairsSize = 0;
            if (exchange == null) {
                try {
                    exchange = applicationContext.getBean(set.getValue());
                    exchange = exchangeService.persist(exchange);
                    pairsSize = calculatePairsSize(exchange);
                    var pairs = new TreeSet<>(exchange.getPairs());
                    while (pairs.size() > pairsSize) pairs.pollLast();
                    exchange.getPairs().clear();
                    Collections.addAll(exchange.getPairs(), pairs.toArray(new CurrencyPair[]{}));
                } catch (Exception e) {
                    logger.error("Exchange initialize crashed", e);
                }
            } else {
                pairsSize = calculatePairsSize(exchange);
                if (exchange.getPairs().size() > pairsSize) {
                    var page = exchangeService.fillPairs(pairsSize);
                    exchange.getPairs().clear();
                    exchange.getPairs().addAll(page.getContent());
                }

            }
            var finalExchange = exchange;
            Class clazz = set.getValue() == BinanceExchange.class ? BinanceExchange.class : BinanceExchange.class;
            //genericApplicationContext.removeBeanDefinition(set.getKey());
            genericApplicationContext.registerBean(clazz, () -> finalExchange, (def) -> def.setPrimary(true));
            //genericApplicationContext.refresh();

            //Arrays.stream(genericApplicationContext.getBeanDefinitionNames()).forEach(System.out::println);
            exchange = genericApplicationContext.getBean(set.getValue());
            exchanges.put(set.getKey(), exchange);

            var task = new TimerTask() {
                @Override
                public void run() {
                    save();
                }
            };
            new Timer().schedule(task, 300000, props.getSavingTimer());
        }
        }catch (Exception e){
            logger.error("Aggregator init failed", e);
        }
    }

    //private Set<Currency> currencies = new HashSet<>();

    public BasicExchange getExchange(String exName){
        return exchanges.get(exName);
    } //todo needs update?

    public Exchange getExchange(String exchange, String[] pairsN, String period) {
        var exch = getExchange(exchange);
        if (exch == null) {
            logger.error("Exchange " + exchange + " not found");
            return null;
        }

        var pairs = exch.getPairs();
        var temp = new CurrencyPair();
        for (String s : pairsN) {
            temp.setSymbol(s);
            try {
                if (!pairs.contains(temp)) exch.insertPair(exchangeService.findPair(s, exch).orElseThrow());//NPE
            }catch (NullPointerException e){
                logger.error(String.format("Pair %1$s not found in %2$s", s, exch.getName()));
            }
        }

        var reqPairs = new HashSet<>(pairs);

        //todo - limit request pairs

        var timePeriod = exch.getChangePeriods().stream().filter(
                p -> p.getName().equals(period)).findFirst().orElseThrow();
        for (CurrencyPair reqPair : reqPairs) {
            try {
                //reqPair.getPriceHistory().add(1.3);
                //reqPair.getPriceHistory().add(1.6);
                exch.currentPrice(reqPair, timePeriod.getPeriod());
                exch.priceChange(reqPair, timePeriod.getPeriod());
            } catch (JSONException e){
                logger.error("Json ex");
            } catch (LimitExceededException e) {
                logger.error(e.getMessage());
            } catch (ErrorCodeException e) {
                logger.error(e.getMessage());
            } catch (BanException e) {
                logger.error(e.getMessage());
            } catch (NoSuchElementException e){
                logger.error(e);
            } catch (Exception e){
                logger.error("unknown exc", e);
            }

        }
        return exch;
    }

    public Map<String, CurrencyPair> getCurStat(String curName1, String curName2){
        var tempCur = new Currency(curName1);
        var tempCur2 = new Currency(curName2);
        return getCurStat(tempCur.getSymbol() + tempCur2.getSymbol());
    }

    public Map<String, CurrencyPair> getCurStat(String pname) {
        var curs = new HashMap<String, CurrencyPair>();
        exchanges.forEach((key, val) -> {
            var p = val.getPair(pname);
            if(p != null) {
                curs.put(key, p);
                val.insertPair(p);
            } else {
                Optional<CurrencyPair> pair = Optional.empty();
                pair = exchangeService.findPair(pname, val);
                pair.ifPresent(currencyPair -> {
                    curs.put(key, currencyPair);
                    val.insertPair(currencyPair);
                });
            }
        });
        return curs;
    }


      //todo check for seconds limit
    public int calculatePairsSize(BasicExchange exchange){
        int counter = 0;
        var tLimits = new LinkedList<Integer>();
        try {
            if (props.isPersistenceStrategy()) return props.getMaxSize();

            var ammountReqs = exchange.getChangePeriods().size() + 1;
            for (Limit limit : exchange.getLimits()) {
                var l = (int) ((limit.getLimitValue() / (double) (limit.getInterval().getSeconds() / 60)) / ammountReqs);
                logger.debug("tLimit = " + l);
                tLimits.add(l);
            }

            for (Integer tLimit : tLimits) {
                counter += tLimit;
            }
        }catch (Exception e){
            logger.error("calculate pairs size ", e);
        }
        return counter / tLimits.size();
    }

    public void save(){
        exchanges.forEach((name, exch) -> exchangeService.update(exch));
    }



}
