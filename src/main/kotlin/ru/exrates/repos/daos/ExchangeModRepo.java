package ru.exrates.repos.daos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import ru.exrates.entities.exchanges.BasicExchange;

import javax.persistence.NoResultException;

@NoRepositoryBean
public interface ExchangeModRepo extends JpaRepository<BasicExchange, Integer> {
    BasicExchange findByName(String name) throws NoResultException;
}
