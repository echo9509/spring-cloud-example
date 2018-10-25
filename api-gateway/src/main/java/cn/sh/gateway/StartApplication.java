package cn.sh.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.cloud.client.SpringCloudApplication;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.netflix.zuul.filters.discovery.PatternServiceRouteMapper;
import org.springframework.context.annotation.Bean;

/**
 * @author sh
 */
@EnableZuulProxy
@SpringCloudApplication
public class StartApplication {

    @Bean
    public PatternServiceRouteMapper serviceRouteMapper() {
        return new PatternServiceRouteMapper(
                "(?<name>^.+)-(?<version>v.+$)", "${version}/${name}");
    }

    public static void main(String[] args) {
        SpringApplication.run(StartApplication.class, args);
    }
}
