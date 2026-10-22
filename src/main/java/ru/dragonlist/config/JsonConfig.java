package ru.dragonlist.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class JsonConfig {
  @Bean
  Jackson2ObjectMapperBuilderCustomizer limits() {
    return builder ->
        builder.postConfigurer(
            mapper ->
                mapper
                    .getFactory()
                    .setStreamReadConstraints(
                        StreamReadConstraints.builder()
                            .maxNestingDepth(32)
                            .maxStringLength(20000)
                            .build()));
  }
}
