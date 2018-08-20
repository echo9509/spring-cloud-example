package cn.sh.ribbon.controller;

import cn.sh.ribbon.service.HelloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author sh
 */
@RestController
public class HelloController {

    @Autowired
    private HelloService helloService;

    @GetMapping("/ribbon-hello")
    public String ribbonHello(@RequestParam String name) {
        return helloService.ribbonHello(name);
    }
}
