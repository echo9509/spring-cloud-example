package cn.sh.feign.demo.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient("hello-service")
@Service
public interface HelloService {

    @GetMapping("/feignHello")
    String hello();
}
