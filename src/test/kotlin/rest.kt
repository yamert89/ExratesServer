import org.apache.logging.log4j.LogManager
import org.junit.Test
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.time.Duration

fun main() {
    rest().threads()
}

class rest {

    fun flux(){

        val flux = Flux.just(1, 2, 3, 4, 5, 6).delayElements(Duration.ofSeconds(1)).doOnEach { println(it) }
        flux.collectList().block()
    }

    fun threads(){
        val logger = LogManager.getLogger(rest::class)
        val f = Flux.fromIterable(listOf(1, 2, 3, 4, 5)).delayElements(Duration.ofMillis(1000), Schedulers.single()).doOnEach {logger.debug(it.get()) }
        f.collectList().block()
    }
}