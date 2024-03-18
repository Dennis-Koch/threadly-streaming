package org.threadlys.utils.configuration;

import java.time.Duration;
import java.util.HashSet;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ConversionServiceFactoryBean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;

import org.threadlys.utils.FutureUtilImpl;
import org.threadlys.utils.ReflectUtilImpl;
import org.threadlys.utils.SneakyThrowUtilImpl;
import org.threadlys.utils.clone.CloneUtilImpl;

//intentionally NOT annotated with @Configuration to be invisible by classpath-scanning
@Import({ //
        CloneUtilImpl.class, //
        FutureUtilImpl.class, //
        ReflectUtilImpl.class, //
        SneakyThrowUtilImpl.class })
public class CommonsUtilsSpringConfig {
    public static class DurationEditor implements Converter<String, Duration> {
        @Override
        public Duration convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            return Duration.parse(source);
        }
    }

    @Bean
    protected ConversionService conversionService() {
        var factory = new ConversionServiceFactoryBean();
        var convSet = new HashSet<>();
        convSet.add(new DurationEditor());
        factory.setConverters(convSet);
        factory.afterPropertiesSet();
        return factory.getObject();
    }
}
