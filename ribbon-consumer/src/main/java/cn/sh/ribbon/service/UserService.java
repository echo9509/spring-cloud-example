package cn.sh.ribbon.service;

import cn.sh.common.entity.User;

import java.util.List;

/**
 * @author sh
 */
public interface UserService {

    User find(Long id);

    List<User> findAll(List<Long> idList);
}
