package ru.exrates.controllers

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import ru.exrates.entities.AuthorizedUser
import ru.exrates.entities.Role
import ru.exrates.repos.UserService

@Controller
class Profiles(@Autowired val userService: UserService) {

    @GetMapping("/reg")
    @ResponseBody
    fun registration(@RequestParam token: String, @RequestParam(required = false) email: String?): String{
        if(userService.userIsExists(token)) return "user alredy exists" //todo
        userService.saveUser(AuthorizedUser(token, email ?: "", Role.ROLE_PREMIUM))
        return ""// todo

    }
}