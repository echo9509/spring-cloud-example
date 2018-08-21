package cn.sh.ribbon.service;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * @author sh
 */
@Service
public class HelloService {

    private static final Logger logger = LoggerFactory.getLogger(HelloService.class);

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 使用@HystrixCommand注解指定回调方法
     * @param name
     * @return
     */
    @HystrixCommand(fallbackMethod = "ribbonHelloFallback", commandKey = "helloKey")
    public String ribbonHello(String name) {
        long start = System.currentTimeMillis();
        String result = restTemplate.getForObject("http://HELLO-SERVICE/hello?name=" + name, String.class);
        long end = System.currentTimeMillis();
        logger.info("Spend Time:" + (end - start));
        return result;
    }

    public String ribbonHelloFallback(String name) {
        return "Hello, this is fallback";
    }
}
