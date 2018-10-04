package cn.sh.hello.controller;

import cn.sh.common.entity.User;
import cn.sh.hello.service.HelloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @author sh
 */
@RestController
public class HelloController {

    @Autowired
    private HelloService helloService;

    @GetMapping("/feignHello")
    public String hello() {
        return helloService.hello();
    }

    @GetMapping("/hello1")
    public String hello(@RequestParam String name) {
        return helloService.hello(name);
    }

    @GetMapping("/hello2")
    public User hello(@RequestHeader String name, @RequestHeader Integer age) {
        return new User(name, age);
    }

    @PostMapping("/hello3")
    public String hello(@RequestBody User user) {
        return "Hello " + user.getName() + ", " + user.getAge();
    }
}
