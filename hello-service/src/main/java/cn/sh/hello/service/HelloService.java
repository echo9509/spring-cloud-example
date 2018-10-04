package cn.sh.hello.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * @author sh
 */
@Service
public class HelloService {

    private static final Logger logger = LoggerFactory.getLogger(HelloService.class);

    public String hello() {
        return "Hello, feign consumer";
    }

    public String hello(String name) {
        return "Hello, " + name;
    }
}
