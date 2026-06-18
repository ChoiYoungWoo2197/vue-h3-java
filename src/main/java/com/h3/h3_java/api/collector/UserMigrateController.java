package com.h3.h3_java.api.collector;

import com.h3.h3_java.api.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/collector")
@RequiredArgsConstructor
public class UserMigrateController {

    private final UserService userService;

    @PostMapping("/users-migrate")
    public Map<String, Object> migrateUsers() {
        return userService.migrateUsers();
    }
}
