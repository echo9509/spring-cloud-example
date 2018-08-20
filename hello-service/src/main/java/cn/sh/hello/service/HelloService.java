package cn.sh.hello.service;

import org.springframework.stereotype.Service;

/**
 * @author sh
 */
@Service
public class HelloService {

    public String hello(String name) {
        return "Hello, " + name;
    }
}
