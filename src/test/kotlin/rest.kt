import org.junit.Test
import reactor.core.publisher.Flux
import java.time.Duration

fun main() {
    rest().flux()
}

class rest {

    fun flux(){
        val flux = Flux.just(1, 2, 3, 4, 5, 6).delayElements(Duration.ofSeconds(1)).doOnEach { println(it) }
        flux.collectList().block()
    }
}