package org.threadlys.threading.impl;

import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ThreadingConfigurationValues {

    private Boolean filterActive;

    private Boolean taskExecutorActive;

    private Boolean parallelActive;

    private Boolean poolPerRequest;

    private Integer maximumPoolSize;

    private Integer poolSize;

    private Duration timeout;

    private Duration gracePeriod;

    private Duration workerTimeout;

    @JsonIgnore
    private Boolean threadlyStreamingHeaderPermitted;
}
