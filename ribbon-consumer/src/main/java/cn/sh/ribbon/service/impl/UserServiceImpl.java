package cn.sh.ribbon.service.impl;

import cn.sh.common.entity.User;
import cn.sh.ribbon.service.UserService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public User find(Long id) {
        return restTemplate.getForObject("http://USER-SERVICE/users/{1}", User.class, id);
    }

    @Override
    public List<User> findAll(List<Long> idList) {
        return restTemplate.getForObject("http://USER-SERVICE/users?ids={1}", List.class, StringUtils.join(idList,","));
    }
}
