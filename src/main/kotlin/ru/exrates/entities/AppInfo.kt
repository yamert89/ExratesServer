package ru.exrates.entities

import org.springframework.stereotype.Service
import javax.persistence.ElementCollection
import javax.persistence.Entity

@Service
class AppInfo {

    var currentClientToken = "3f585cdf-6138-4b1a-88e1-40bd3ff0693b"

    var message: Pair<String, String> = "" to ""

    @ElementCollection
    val soppedClientIps: Set<String> = HashSet()

}