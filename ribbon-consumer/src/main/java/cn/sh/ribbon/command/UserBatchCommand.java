package cn.sh.ribbon.command;

import cn.sh.common.entity.User;
import cn.sh.ribbon.service.UserService;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;

import java.util.List;

public class UserBatchCommand extends HystrixCommand<List<User>> {

    private UserService userService;

    private List<Long> idList;

    public UserBatchCommand(UserService userService, List<Long> idList) {
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("userBatchGroup")));
        this.userService = userService;
        this.idList = idList;
    }

    @Override
    protected List<User> run() throws Exception {
        return userService.findAll(idList);
    }
}
