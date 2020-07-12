package ru.exrates.func

import kotlinx.coroutines.*
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Service
import ru.exrates.configs.Properties
import java.lang.Runnable
import java.util.concurrent.*

@Service
class TaskHandler {
    @Autowired
    private lateinit var properties: Properties
    @Autowired
    private lateinit var logger: Logger
    private lateinit var executor: ThreadPoolExecutor


    /**
    * run single task in async mode
     * */
    fun runTask(name: String = "unnamed", task: () -> Unit){
        if (!this::executor.isInitialized) executor =
            ThreadPoolExecutor(properties.initPoolSize(), properties.maxPoolSize(), 1, TimeUnit.MINUTES, LinkedBlockingQueue<Runnable>() )
        executor.execute(task)
        logger.debug("Task $name started....")
    }

    /**
     * run many tasks in sync block mode
     **/
    fun awaitTasks(vararg task: () -> Unit) = runBlocking{
        val queue = LinkedBlockingQueue<Deferred<Unit>>()
        withContext(executor.asCoroutineDispatcher()){
            task.forEach {
                val job = async{
                    it.invoke()
                }
                queue.add(job)
            }
            queue.forEach {
                logger.debug("awaiting task ${it.await()} done")
            }
        }
    }
    //todo pool size needs empiric tests
}