package cn.sh.hello;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

/**
 * @author sh
 */
@EnableEurekaClient
@SpringBootApplication
public class StartHelloService {

    public static void main(String[] args) {
        SpringApplication.run(StartHelloService.class, args);
    }
}
