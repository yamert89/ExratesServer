package ru.exrates.func

import kotlinx.coroutines.*
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import ru.exrates.configs.Properties
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct

@Service
class TaskHandler {
    @Autowired
    private lateinit var properties: Properties
    @Autowired
    private lateinit var logger: Logger
    private lateinit var executor: ThreadPoolExecutor

    @PostConstruct
    fun init(){
        executor = ThreadPoolExecutor(properties.initPoolSize(), properties.maxPoolSize(), 1, TimeUnit.MINUTES, LinkedBlockingQueue<Runnable>())
    }


    /**
    * run single task in async mode
     * */
    fun runTask(name: String = "unnamed", task: () -> Unit){
        executor.execute(task)
        logger.debug("Task $name started....")
    }

    /**
     * run many tasks in async mode with interval
     **/

    fun runTasks(interval: Long = 0L, vararg tasks: () -> Unit) = runBlocking {
        withContext(getExecutorContext()){
            tasks.forEach {
                launch { it()}
                logger.trace("delay of $interval")
                delay(interval)
            }
        }

    }


    /**
     * run many tasks in sync block mode with interval
     **/
    fun awaitTasks(interval: Long, vararg task: () -> Unit) = runBlocking{
        val queue = LinkedBlockingQueue<Deferred<Unit>>()
        withContext(getExecutorContext()){
            task.forEach {
                val job = async{
                    it()
                }
                if (interval > 0) delay(interval)
                queue.add(job)
            }
            queue.forEach {
                logger.debug("awaiting task ${it.await()} done")
            }
        }
    }

    fun awaitTasks(vararg task: () -> Unit) = awaitTasks(0L, *task)

    fun getExecutorContext() = executor.asCoroutineDispatcher()
    //todo pool size needs empiric tests
}