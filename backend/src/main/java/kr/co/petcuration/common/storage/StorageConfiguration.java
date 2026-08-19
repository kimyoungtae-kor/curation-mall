package kr.co.petcuration.common.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({StorageProperties.class, ImageUploadProperties.class})
public class StorageConfiguration {

    @Bean
    StorageService storageService(StorageProperties properties) {
        return new FileSystemStorageService(properties);
    }
}
