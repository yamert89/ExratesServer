package ru.exrates.func

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Service
import ru.exrates.configs.Properties
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Service
class TaskHandler {
    @Autowired
    private lateinit var properties: Properties
    private lateinit var executor: ThreadPoolExecutor


    fun runTask(task: () -> Unit){
        if (!this::executor.isInitialized) executor =
            ThreadPoolExecutor(properties.initPoolSize(), properties.maxPoolSize(), 1, TimeUnit.MINUTES, LinkedBlockingQueue<Runnable>() )
        executor.execute(task)
    }




}