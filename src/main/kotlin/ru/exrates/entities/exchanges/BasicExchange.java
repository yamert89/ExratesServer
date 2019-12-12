package ru.exrates.entities.exchanges;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.SortComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.exrates.entities.CurrencyPair;
import ru.exrates.entities.TimePeriod;
import ru.exrates.entities.exchanges.secondary.*;
import ru.exrates.utils.JsonSerializers;

import javax.annotation.PostConstruct;
import javax.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Entity @Inheritance(strategy = InheritanceType.SINGLE_TABLE) @DiscriminatorColumn(name = "EXCHANGE_TYPE")
@JsonIgnoreProperties({"id", "limits", "limitcode", "banCode", "sleepValueSeconds", "updatePeriod", "temporary"})
public abstract class BasicExchange implements Exchange {

    @Id @GeneratedValue

    private Integer id;

    boolean temporary = true;
    private final static Logger logger = LogManager.getLogger(BasicExchange.class);

    static String URL_ENDPOINT, URL_CURRENT_AVG_PRICE, URL_INFO, URL_PRICE_CHANGE, URL_PING, URL_ORDER;

    @ManyToMany(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)

    //@JsonIgnore //TODO delete
    @JsonSerialize(using = JsonSerializers.TimePeriodListSerializer.class)
    List<TimePeriod> changePeriods;

    @OneToMany(orphanRemoval = true, cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)

    Set<Limit> limits;

    int limitCode, banCode, sleepValueSeconds = 30;

    Duration updatePeriod;

    @Getter
    String name;


    @OneToMany(cascade = {CascadeType.PERSIST}, fetch = FetchType.EAGER)
    @Getter
    @SortComparator(CurrencyPair.CurComparator.class)
    //@org.hibernate.annotations.OrderBy(clause = "last_use desc")
    //@Column(name="last_use")
    //@OrderColumn(name = "last_use", nullable = false)
    protected final SortedSet<CurrencyPair> pairs = new TreeSet<>();

    @Transient
    private Properties props;

    @Transient
    RestTemplateImpl restTemplate;

    @Transient
    WebClient webClient;

    
    @Autowired
    public void setRestTemplate(RestTemplateImpl restTemplate) {
        this.restTemplate = restTemplate;
    }


    @Autowired
    public void setProps(Properties props) {
        this.props = props;
    }



    BasicExchange() {
        // init pairs

    }

    @PostConstruct
    protected void init(){
        logger.debug("Postconstruct " + name);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                try {
                    task();
                }catch (RuntimeException e){
                    this.cancel();
                    logger.error(e);
                    logger.debug(e.getMessage());
                }
            }
        };
        Timer timer = new Timer();
        updatePeriod = Duration.ofMillis(props.getTimerPeriod());
        timer.schedule(task, 10000, props.getTimerPeriod());
        //timer.cancel();
    }

    @Override
    public void insertPair(CurrencyPair pair){
        pairs.add(pair);
        if (pairs.size() > props.getMaxSize()) pairs.remove(pairs.first());
    }

    @Override
    public CurrencyPair getPair(Currency c1, Currency c2){
        final CurrencyPair[] pair = {null};
        pairs.spliterator().forEachRemaining(el ->  {
            if (el.getSymbol().equals(c1.getSymbol() + c2.getSymbol())) pair[0] = el;
        });
        return pair[0];
    }

    @Override
    public CurrencyPair getPair(String pairName){
        final CurrencyPair[] p = {null};
        pairs.spliterator().forEachRemaining(el ->  {
            if (el.getSymbol().equals(pairName)) p[0] = el;
        });
        return p[0];

    }

    public boolean dataElapsed(CurrencyPair pair, Duration timeout, int idx){
        logger.debug(String.format("Pair %1$s was updated on field %2$s %3$s | current time = %4$s", pair.getSymbol(), idx, Instant.ofEpochMilli(pair.getUpdateTimes()[idx]), Instant.now()));
        return Instant.now().isAfter(Instant.ofEpochMilli(pair.getUpdateTimes()[idx] + timeout.toMillis()));
    }


    protected void task() throws RuntimeException{
        logger.debug( name + " task started....");
        CurrencyPair pair = null;
        synchronized (pairs){
            for (CurrencyPair p : pairs) {
                try {
                    //p.getPriceHistory().add(3.2);
                    //p.getPriceHistory().add(3.5);
                    currentPrice(p, updatePeriod);
                    priceChange(p, updatePeriod);
                } catch (JSONException e) {
                    logger.error("task JS ex", e);
                } catch (LimitExceededException e){
                    logger.error(e.getMessage());
                    try {
                        sleepValueSeconds *= 2;
                        Thread.sleep(sleepValueSeconds);
                    } catch (InterruptedException ex) {
                        logger.error("Interrupt ", ex);
                    }
                    task();
                    return;
                } catch (ErrorCodeException e){
                    logger.error(e.getMessage());
                } catch (BanException e){
                    logger.error(e.getMessage());
                    throw new RuntimeException("You are banned from " + this.name);
                } catch (Exception e){
                    throw new RuntimeException("Unknown error", e);
                }
            }
        }

    };

    <T> T request(String uri, Class<T> tClass) throws BanException, LimitExceededException, IllegalStateException{
        return webClient.get().uri(uri).retrieve().onStatus(HttpStatus::is4xxClientError, resp ->{
            Exception ex = null;
            switch(resp.statusCode().value()){
                case 418: ex = new BanException();
                    break;
                case 429: ex = new LimitExceededException(LimitType.WEIGHT);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + resp.statusCode().value());
            }
            return Mono.error(ex);
        }).bodyToMono(tClass).block();
    }

    public abstract void currentPrice(CurrencyPair pair, Duration timeout) throws
            JSONException, NullPointerException, LimitExceededException, ErrorCodeException, BanException;

    public abstract void priceChange(CurrencyPair pair, Duration timeout, Map<String, String> uriVariables) throws
            JSONException, LimitExceededException, ErrorCodeException, BanException;

    public abstract void priceChange(CurrencyPair pair, Duration timeout) throws
            JSONException, LimitExceededException, ErrorCodeException, BanException;



    @Override
    public String toString() {
        return "BasicExchange{" +
                "id=" + id +
                ", temporary=" + temporary +
                ", changePeriods=" + changePeriods +
                ", limits=" + limits +
                ", limitCode=" + limitCode +
                ", banCode=" + banCode +
                ", sleepValueSeconds=" + sleepValueSeconds +
                ", updatePeriod=" + updatePeriod +
                ", name='" + name + '\'' +
                ", pairs=" + pairs +
                ", props=" + props +
                ", restTemplate=" + restTemplate +
                '}';
    }
}
