package ru.exrates.func

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONException
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.context.annotation.Scope
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import ru.exrates.entities.LimitType
import ru.exrates.entities.exchanges.BasicExchange
import ru.exrates.entities.exchanges.secondary.BanException
import ru.exrates.entities.exchanges.secondary.LimitExceededException
import java.net.ConnectException
import javax.persistence.Transient
import kotlin.reflect.KClass

@Service
@Scope("prototype")
class RestCore(endPoint: String, private val banCode: Int, private val limitCode: Int, private val serverError: Int) {

    var webClient: WebClient = WebClient.create(endPoint)

    @Autowired
    protected lateinit var logger: Logger


    fun <T: Any> request(uri: String, clazz: KClass<T>, connectionExceptionMessage: String = "Server error: $uri") : Mono<T> {
        logger.trace("Try request to : $uri")
        val resp = webClient.get().uri(uri).retrieve()
            .onStatus(HttpStatus::is4xxClientError) { resp ->
                logger.trace("RESPONSE of $uri: ${resp}")
                val ex = when(resp.statusCode().value()){
                    banCode -> BanException()
                    limitCode -> LimitExceededException(LimitType.WEIGHT) //todo add server error code p2p error 4001 / 503
                    serverError -> ConnectException(connectionExceptionMessage)

                    //null -> NullPointerException()
                    else -> IllegalStateException("Unexpected value: ${resp.statusCode().value()}")
                }
                Mono.error(ex) }

            .bodyToMono(clazz.java) //todo 1 - null compile notif? // 2 - FIXMe operate exception !!!
        return resp
    }


    fun stringRequest(uri: String, connectionExceptionMessage: String = "Server error: $uri") = request(uri, String::class, connectionExceptionMessage)

    fun <T : Any> blockingStringRequest(uri: String, jsonType: KClass<T>): T{
        val req = stringRequest(uri)
        val resp = req.block()
        try{
            return when(jsonType){
                JSONObject::class -> JSONObject(resp) as T
                JSONArray::class -> JSONArray(resp) as T
                else -> throw IllegalArgumentException("unsupported json object")
            }
        }catch (e: JSONException){
            logger.error("json create exception with body: $resp")
        }
        return when(jsonType){
            JSONObject::class -> JSONObject() as T
            else -> JSONArray() as T
        }
    }

}