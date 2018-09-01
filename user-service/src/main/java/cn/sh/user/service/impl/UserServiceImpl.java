package cn.sh.user.service.impl;

import cn.sh.common.entity.User;
import cn.sh.user.service.UserService;
import org.springframework.stereotype.Service;

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
}
