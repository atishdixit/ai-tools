package com.example.chatbot.api;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {

    /** Runs streamed answers off the request thread; a virtual thread per answer is cheap even though the model itself is slow. */
    @Bean(destroyMethod = "close")
    public ExecutorService streamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
