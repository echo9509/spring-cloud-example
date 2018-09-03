package cn.sh.ribbon.controller;

import cn.sh.common.entity.User;
import cn.sh.ribbon.service.HelloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rx.Observable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

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

    @GetMapping("/users/{id}")
    public User getUserById(@PathVariable Long id) throws ExecutionException, InterruptedException {
        // 使用命令方式调用服务
//        return helloService.getUserById(id);
        // 使用注解命令方式同步调用服务
//        return helloService.findUserById(id);
        // 使用注解命令方式异步调用服务
        Future<User> userFuture = helloService.asyncFindUserFutureById(id);
        return userFuture.get();
    }
}
