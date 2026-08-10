package de.videostorm.indexing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Infrastructure the indexing service leans on. The executor is deliberately single-threaded: a
 * run is handed off to it and the request returns at once, and the single thread means two scans
 * can never touch the catalogue tables at the same time even if the service's own guard were
 * bypassed. Shutdown drains it so a scan in flight is not killed mid-write on redeploy.
 */
@Configuration
public class IndexingConfiguration {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService indexingExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "indexing");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
