package com.akto.listener;

import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.DiskSpaceMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.File;

public class HealthListener implements ServletContextListener {
    public static PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private static final LoggerMaker loggerMaker = new LoggerMaker(HealthListener.class);
    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        try {
            new JvmThreadMetrics().bindTo(registry);
            new JvmGcMetrics().bindTo(registry);
            new JvmMemoryMetrics().bindTo(registry);
            new DiskSpaceMetrics(new File("/")).bindTo(registry);
            new ProcessorMetrics().bindTo(registry); // metrics related to the CPU stats
            new UptimeMetrics().bindTo(registry);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,"ERROR while setting up HealthListener", LogDb.DEPENDENCY_SERVICE); //Logger for DependencyService
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {

    }
}
