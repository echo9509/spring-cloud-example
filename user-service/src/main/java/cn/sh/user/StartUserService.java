package cn.sh.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

/**
 * @author sh
 */
@EnableEurekaClient
@SpringBootApplication
public class StartUserService {

    public static void main(String[] args) {
        SpringApplication.run(StartUserService.class, args);
    }
}
