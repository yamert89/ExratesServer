package ru.exrates.func

import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONException
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.http.HttpHeaders
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

    private fun <T: Any> monoRequest(uri: String, clazz: KClass<T>, headers: HttpHeaders) : Pair<Mono<ClientResponse>, Mono<T>> {
        logger.trace("Try request to : $uri with headers: $headers")
        val resp = webClient.get().uri(uri).headers{h-> h.addAll(headers)}.exchange()
        return resp to resp.flatMap { r -> r.bodyToMono(clazz.java) }
    }

    private fun <T: Any> patchRequests(uries: List<String>, clazz: KClass<T>, interval : Duration = Duration.ZERO, headers: HttpHeaders = HttpHeaders.EMPTY): Flux<T> {
        return uries.toFlux().delayElements(interval, Schedulers.single()).flatMap {
            webClient.get().uri(it).headers { h -> h.addAll(headers) }.retrieve().bodyToMono(clazz.java).doOnEach {p ->
                if(p.isOnNext) logger.debug("patch request element: $it")
            }
        }.doOnNext { logger.debug("Patch response: $it") }
    }

    fun patchStringRequests(uries: List<String>, interval : Duration = Duration.ZERO, headers: HttpHeaders = HttpHeaders.EMPTY) = patchRequests(uries, String::class, interval, headers)

    private fun stringRequest(uri: String, headers: HttpHeaders) = monoRequest(uri, String::class, headers)


    @Suppress("UNCHECKED_CAST")
    fun <T : Any> blockingStringRequest(uri: String, jsonType: KClass<T>, headers: HttpHeaders = HttpHeaders.EMPTY): Pair<HttpStatus, T> {
        val req = stringRequest(uri, headers)
        val resp = req.second.block()
        logger.trace("Response of $uri\n$resp")
        val status = req.first.block()!!.statusCode()
        logger.trace("Response status of $uri : ${status}")
        try {
            return if (status != HttpStatus.OK || jsonType == JSONObject::class) status to JSONObject(resp) as T
            else  status to JSONArray(resp) as T
        }catch (e: JSONException){
            logger.error("json create exception with body: $resp")
        }
        return when(jsonType){
            JSONObject::class -> status to JSONObject() as T
            else -> status to JSONArray() as T
        }
    }

}