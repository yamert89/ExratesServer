package ru.exrates.repos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.exrates.entities.CurrencyPair;
import ru.exrates.entities.exchanges.BasicExchange;
import ru.exrates.repos.daos.CurrencyRepository;
import ru.exrates.repos.daos.ExchangeRepository;

import javax.persistence.NoResultException;
import java.util.Optional;

@Service
@Transactional
public class ExchangeService {
    private final static Logger logger = LogManager.getLogger(ExchangeService.class);

    private ExchangeRepository exchangeRepository;
    private CurrencyRepository currencyRepository;
    @Autowired
    public void setCurrencyRepository(CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
    }

    @Autowired
    public void setExchangeRepository(ExchangeRepository exchangeRepository) {
        this.exchangeRepository = exchangeRepository;
    }

    @Transactional
    @Nullable
    public BasicExchange find(int id){
        var exch = exchangeRepository.findById(id);
        return exch.orElse(null);
    }

    @Transactional
    @Nullable
    public BasicExchange find(String name){
        try {
            return exchangeRepository.findByName(name);
        }catch (NoResultException e){
            return null;
        }
    }

    @Transactional
    public BasicExchange merge(BasicExchange exchange){
       return exchangeRepository.save(exchange);
    }

    @Transactional
    public BasicExchange update(BasicExchange exchange){
        logger.debug(exchange.getName() + " updated");
        exchange.getPairs().forEach(p -> currencyRepository.save(p));
        return exchangeRepository.save(exchange);
    }

    @Transactional
    public BasicExchange persist(BasicExchange exchange){
        return exchangeRepository.save(exchange);
    }

    @Transactional
    public Page<CurrencyPair> fillPairs(int amount){
        return currencyRepository.findAll(PageRequest.of(1, amount));
    }

    @Transactional
    public Optional<CurrencyPair> findPair(String symbol, BasicExchange exchange){
        return currencyRepository.findBySymbolAndExchange(symbol, exchange);
    }





}
