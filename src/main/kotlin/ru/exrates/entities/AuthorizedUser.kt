package ru.exrates.entities

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
data class AuthorizedUser(val token: String, val email: String, val role: Role) {
    @Id
    @GeneratedValue
    private var id: Long = 0

}

enum class Role{ROLE_PREMIUM, ROLE_SIMPLE}