package cn.sh.feign.demo.service;

import cn.sh.common.entity.User;
import cn.sh.feign.demo.fallback.HelloServiceFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * @author sh
 */
@FeignClient(value = "hello-service", fallback = HelloServiceFallback.class)
public interface HelloService {

    @GetMapping("/feignHello")
    String hello();

    @GetMapping("/hello1")
    String hello(@RequestParam("name") String name);

    @GetMapping("/hello2")
    User hello(@RequestHeader("name") String name, @RequestHeader("age") Integer age);

    @PostMapping("/hello3")
    String hello(@RequestBody User user);
}
