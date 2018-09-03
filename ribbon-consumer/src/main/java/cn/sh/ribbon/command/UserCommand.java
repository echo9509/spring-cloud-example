package cn.sh.ribbon.command;

import cn.sh.common.entity.User;
import com.netflix.hystrix.HystrixCommand;
import org.springframework.web.client.RestTemplate;

/**
 * @author sh
 */
public class UserCommand extends HystrixCommand<User> {

    private RestTemplate restTemplate;

    private Long id;

    public UserCommand(Setter setter, RestTemplate restTemplate, Long id) {
        super(setter);
        this.restTemplate = restTemplate;
        this.id = id;
    }

    @Override
    protected User run() throws Exception {
        return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
    }

    @Override
    protected User getFallback() {
        User user = new User();
        user.setId(1L);
        user.setName("sh");
        return user;
    }
}
