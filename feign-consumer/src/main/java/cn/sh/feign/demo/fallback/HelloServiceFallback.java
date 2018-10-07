package cn.sh.feign.demo.fallback;

import cn.sh.common.entity.User;
import cn.sh.feign.demo.service.HelloService;
import org.springframework.stereotype.Component;

/**
 * @author sh
 */
@Component
public class HelloServiceFallback implements HelloService {

    @Override
    public String hello() {
        return "error";
    }

    @Override
    public String hello(String name) {
        return "error";
    }

    @Override
    public User hello(String name, Integer age) {
        return new User("UNKNOWN", 0);
    }

    @Override
    public String hello(User user) {
        return "error";
    }
}
