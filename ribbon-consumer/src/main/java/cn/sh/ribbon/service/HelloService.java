package cn.sh.ribbon.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * @author sh
 */
@Service
public class HelloService {

    @Autowired
    private RestTemplate restTemplate;

    public String ribbonHello(String name) {
        return restTemplate.getForObject("http://HELLO-SERVICE/hello?name=" + name, String.class);
    }
}
