package cn.sh.feign.demo.controller;

import cn.sh.common.entity.User;
import cn.sh.feign.demo.service.HelloService;
import com.alibaba.fastjson.JSON;
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

    @GetMapping(value = "/feignHello2")
    public String helloConsumer2() {
        StringBuilder sb = new StringBuilder();
        sb.append(helloService.hello()).append("\r\n");
        sb.append(helloService.hello("test")).append("\r\n");
        sb.append(JSON.toJSONString(helloService.hello("sh", 23))).append("\r\n");
        sb.append(helloService.hello(new User("postSh", 45)));
        return sb.toString();
    }

}
