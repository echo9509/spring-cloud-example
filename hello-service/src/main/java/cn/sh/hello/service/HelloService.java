package cn.sh.hello.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * @author sh
 */
@Service
public class HelloService {

    private static final Logger logger = LoggerFactory.getLogger(HelloService.class);

    public String hello() {
        int sleep = new Random().nextInt(3000);
        logger.info("sleep time:{}", sleep);
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            logger.error("线程休眠发生异常，error:{}", e);
        }
        return "Hello, feign consumer";
    }

    public String hello(String name) {
        return "Hello, " + name;
    }
}
