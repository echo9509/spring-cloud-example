package cn.sh.ribbon.command;

import cn.sh.common.entity.User;
import com.netflix.hystrix.HystrixObservableCommand;
import org.springframework.web.client.RestTemplate;
import rx.Observable;

/**
 * @author sh
 */
public class UserObservableCommand extends HystrixObservableCommand<User> {

    private RestTemplate restTemplate;

    private Long id;

    public UserObservableCommand (Setter setter, RestTemplate restTemplate, Long id) {
        super(setter);
        this.restTemplate = restTemplate;
        this.id = id;
    }

    @Override
    protected Observable<User> construct() {
        return Observable.create(subscriber -> {
            if (!subscriber.isUnsubscribed()) {
                User user = restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
                subscriber.onNext(user);
                subscriber.onCompleted();
            }
        });
    }

    @Override
    protected Observable<User> resumeWithFallback() {
        Observable<User> userObservable = Observable.create(subscriber -> {
            User user = new User();
            user.setId(1L);
            user.setName("sh");
            subscriber.onNext(user);
            subscriber.onCompleted();
        });
        return userObservable;
    }
}
