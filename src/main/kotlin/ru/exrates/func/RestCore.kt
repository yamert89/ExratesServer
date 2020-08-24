package ru.exrates.func

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.configurationprocessor.json.JSONArray
import org.springframework.boot.configurationprocessor.json.JSONException
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import reactor.core.scheduler.Schedulers
import ru.exrates.entities.LimitType
import ru.exrates.entities.exchanges.secondary.BanException
import ru.exrates.entities.exchanges.secondary.LimitExceededException
import java.net.ConnectException
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import kotlin.reflect.KClass

/*@Service
@Scope("prototype")*/
class RestCore(endPoint: String, private val errorHandler: (ClientResponse) -> Mono<Throwable>) {

    var webClient: WebClient =  WebClient.builder()
        .baseUrl(endPoint)
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

    private fun <T: Any> monoRequest(uri: String, clazz: KClass<T>) : Mono<T> {
        logger.trace("Try request to : $uri")
        val resp = webClient.get().uri(uri).exchange()
            .flatMap { resp ->
                resp.bodyToMono(clazz.java)
            }
        return resp
    }

    fun <T: Any> patchRequests(uries: List<String>, clazz: KClass<T>): Flux<T> {
        return uries.toFlux().delayElements(Duration.ofMillis((1000 / 3) * 2), Schedulers.single()).flatMap {//todo limit period
            webClient.get().uri(it).retrieve().bodyToMono(clazz.java).doOnEach {p ->
                if(p.isOnNext) logger.debug("patch request element: $it")
            }
        }.doOnNext { logger.debug("Patch response: $it") }
    }

    fun patchStringRequests(uries: List<String>) = patchRequests(uries, String::class)


    fun stringRequest(uri: String) = monoRequest(uri, String::class)


    @Suppress("UNCHECKED_CAST")
    fun <T : Any> blockingStringRequest(uri: String, jsonType: KClass<T>): T{
        val req = stringRequest(uri)
        val resp = req.block()
        logger.trace("Response of $uri\n$resp")
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