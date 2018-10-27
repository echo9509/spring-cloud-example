package cn.sh.gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author sh
 */
@RestController
public class AccessController {

    @GetMapping("/hello")
    public String hello() {
        return "hello, i am local";
    }
}
