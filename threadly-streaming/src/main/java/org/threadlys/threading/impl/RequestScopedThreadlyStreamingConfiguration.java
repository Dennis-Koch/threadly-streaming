package org.threadlys.threading.impl;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class RequestScopedThreadlyStreamingConfiguration {
    private ThreadingConfigurationValues configuration = new ThreadingConfigurationValues();
    
    public ThreadingConfigurationValues getCurrentThreadlyStreamingConfiguration() {
        // ensure that this value can never be null
        return configuration;
    }
    
    public void applyCurrentThreadlyStreamingConfiguration(ThreadingConfigurationValues currentThreadlyStreamingConfiguration) {
        this.configuration = currentThreadlyStreamingConfiguration != null ? currentThreadlyStreamingConfiguration : new ThreadingConfigurationValues();
    }
}
