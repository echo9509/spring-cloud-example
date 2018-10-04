package cn.sh.feign.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * @author sh
 */
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication
public class StartFeignConsumer {

    public static void main(String[] args) {
        SpringApplication.run(StartFeignConsumer.class, args);
    }
}
