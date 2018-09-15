package cn.sh.user.controller;

import cn.sh.common.entity.User;
import cn.sh.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author sh
 */
@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/users/{id}")
    public User getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @GetMapping("/users")
    public List<User> findAllUser(@RequestParam("ids") String ids) {
        List<Long> idList = new ArrayList<>();
        Arrays.stream(ids.split(",")).forEach(id -> idList.add(Long.valueOf(id)));
        return userService.findAll(idList);
    }
}
