package cn.sh.user.service.impl;

import cn.sh.common.entity.User;
import cn.sh.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author sh
 */
@Service
public class UserServiceImpl implements UserService {

    @Override
    public User getUserById(Long id) {
        User user = new User();
        user.setId(id);
        user.setName("test");
        return user;
    }

    @Override
    public List<User> findAll(List<Long> idList) {
        List<User> userList = new ArrayList<>();
        idList.forEach(id -> {
            User user = new User();
            user.setId(id);
            user.setName("sh" + id);
            userList.add(user);
        });
        return userList;
    }
}
