package ru.exrates.func

import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Service
import ru.exrates.configs.Properties
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
    fun awaitTasks(vararg task: () -> Unit){
        val queue = LinkedBlockingQueue<Future<*>>()
        task.forEach { queue.add(executor.submit(it))}
        var condition = true
        while (condition){
            queue.forEach {
                if (!it.isDone){
                    condition = true
                    return@forEach
                }
                condition = false
            }
        }
    }
    //todo pool size needs empiric tests
}