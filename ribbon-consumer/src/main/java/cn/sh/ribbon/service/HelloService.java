package cn.sh.ribbon.service;

import cn.sh.common.entity.User;
import cn.sh.ribbon.command.UserCommand;
import cn.sh.ribbon.command.UserObservableCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.ObservableExecutionMode;
import com.netflix.hystrix.contrib.javanica.command.AsyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action1;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

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


    /**
     * 第一种使用命令的方式
     * @param id
     * @return
     */
    public User getUserById(Long id) throws ExecutionException, InterruptedException {
        UserCommand userCommand = new UserCommand(restTemplate, id);
        // 同步执行获取结果
//        return userCommand.execute();

        // 异步执行获取结果
        Future<User> future = userCommand.queue();
        try {
            return future.get();
        } catch (Exception e) {
            logger.info("获取结果发生异常", e);
        }
        return null;
    }


    /**
     * 通过注解方式同步执行获取User
     * 注解方式服务降级
     * @param id
     * @return
     */
    @HystrixCommand(fallbackMethod = "getDefaultUser", ignoreExceptions = NullPointerException.class,
            commandKey = "findUserById", groupKey = "UserGroup", threadPoolKey = "findUserByIdThread")
    public User findUserById(Long id) {
        return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
    }

    /**
     * 通过注解方式异步执行获取User
     * @param id
     * @return
     */
    @HystrixCommand
    public Future<User> asyncFindUserFutureById(Long id) {
        return new AsyncResult<User>() {
            @Override
            public User invoke() {
                return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
            }
        };
    }

    /**
     * 通过响应方式执行命令
     * @param id
     * @return
     */
    public Observable<User> observableGetUserById(Long id) {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("userKey");
        UserCommand userCommand = new UserCommand(restTemplate, id);
        return userCommand.toObservable();
    }

    public Observable<User> observableCommandGetUserById(Long id) {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("userKey");
        com.netflix.hystrix.HystrixObservableCommand.Setter setter = com.netflix.hystrix.HystrixObservableCommand.Setter.withGroupKey(groupKey);
        UserObservableCommand userObservableCommand = new UserObservableCommand(setter, restTemplate ,id);
        return  userObservableCommand.observe();
    }

    /**
     * 使用注解实现响应式命令
     * @param id
     * @return
     */
    @HystrixCommand
//    @HystrixCommand(observableExecutionMode = ObservableExecutionMode.EAGER)
//    @HystrixCommand(observableExecutionMode = ObservableExecutionMode.LAZY)
    public Observable<User> observableGetUserId(Long id) {
        return Observable.create(subscriber -> {
            if (!subscriber.isUnsubscribed()) {
                User user = restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
                subscriber.onNext(user);
                subscriber.onCompleted();
            }
        });
    }


    private User getDefaultUser() {
        User user = new User();
        user.setId(2L);
        user.setName("sh");
        return user;
    }

}
