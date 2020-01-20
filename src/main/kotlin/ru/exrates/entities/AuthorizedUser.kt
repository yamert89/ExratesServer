package ru.exrates.entities

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
class AuthorizedUser(val token: String) {
    @Id
    @GeneratedValue
    private var id: Long = 0


}