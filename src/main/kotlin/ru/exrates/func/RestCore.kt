package ru.exrates.func

import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONException
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import reactor.core.scheduler.Schedulers
import java.time.Duration
import kotlin.reflect.KClass

@Service
class RestCore() {

    var webClient: WebClient =  WebClient.builder()
        .exchangeStrategies { builder ->
            builder.codecs {
                it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)
            }
        }
        .build()

    @Autowired
    protected lateinit var logger: Logger

   /* private fun <T: Any> monoRequest(uri: String, clazz: KClass<T>) : Mono<T> {
        logger.trace("Try request to : $uri")
        val resp = webClient.get().uri(uri).retrieve()
            .onStatus(HttpStatus::is4xxClientError) { errorHandler(it) }
            .bodyToMono(clazz.java) //todo 1 - null compile notif? // 2 - FIXMe operate exception !!!
        return resp
    }*/

    private fun <T: Any> monoRequest(uri: String, clazz: KClass<T>) : Pair<HttpStatus, Mono<T>> {
        logger.trace("Try request to : $uri")
        var code = HttpStatus.BAD_REQUEST
        return code to webClient.get().uri(uri).exchange()
            .flatMap { resp ->
                code = resp.statusCode()
                resp.bodyToMono(clazz.java)
            }
    }

    private fun <T: Any> patchRequests(uries: List<String>, clazz: KClass<T>): Flux<T> {
        return uries.toFlux().delayElements(Duration.ofMillis((1000 / 3) * 2), Schedulers.single()).flatMap {//todo limit period
            webClient.get().uri(it).retrieve().bodyToMono(clazz.java).doOnEach {p ->
                if(p.isOnNext) logger.debug("patch request element: $it")
            }
        }.doOnNext { logger.debug("Patch response: $it") }
    }

    fun patchStringRequests(uries: List<String>) = patchRequests(uries, String::class)

    private fun stringRequest(uri: String) = monoRequest(uri, String::class)


    @Suppress("UNCHECKED_CAST")
    fun <T : Any> blockingStringRequest(uri: String, jsonType: KClass<T>): Pair<HttpStatus, T> {
        val req = stringRequest(uri)
        val resp = req.second.block()
        logger.trace("Response of $uri\n$resp")
        try {
            return if (req.first != HttpStatus.OK || jsonType == JSONObject::class) req.first to JSONObject(resp) as T
            else req.first to JSONArray(resp) as T
        }catch (e: JSONException){
            logger.error("json create exception with body: $resp")
        }
        return when(jsonType){
            JSONObject::class -> req.first to JSONObject() as T
            else -> req.first to JSONArray() as T
        }
    }

}