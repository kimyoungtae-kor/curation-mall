package kr.co.petcuration.common.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {

    @Bean
    StorageService storageService(StorageProperties properties) {
        return new FileSystemStorageService(properties);
    }
}
