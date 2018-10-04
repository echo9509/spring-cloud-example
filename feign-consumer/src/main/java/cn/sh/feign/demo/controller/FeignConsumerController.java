package cn.sh.feign.demo.controller;

import cn.sh.feign.demo.service.HelloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author sh
 */
@RestController
public class FeignConsumerController {

    @Autowired
    private HelloService helloService;

    @GetMapping(value = "/feignHello")
    public String helloConsumer() {
        return helloService.hello();
    }
}
