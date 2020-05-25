package ru.exrates.repos

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import ru.exrates.entities.AuthorizedUser
import javax.transaction.Transactional

@Service @Transactional
class UserService(@Autowired val userRepository: UserRepository) {
    private val logger: Logger = LogManager.getLogger(ExchangeService::class)

    fun userIsExists(userToken: String): Boolean = userRepository.findByToken(userToken) != null

    fun saveUser(user: AuthorizedUser) = userRepository.save(user)


}