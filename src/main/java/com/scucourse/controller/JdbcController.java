package com.scucourse.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

@RestController
public class JdbcController {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping("/dbtest")
    public String dbtest() {
        String username = "ro";
        String password = "1234";
        String sql = "select password from user_info where username = \"" + username + "\"";
        String result = (String)jdbcTemplate.queryForObject(sql, String.class);
        if (password.equals(result))
            return "OK";
        else
            return "Oops!";
    }

    @GetMapping("/addUser")
    public String addUser() {
        String sql = "insert into user_info(id, username, password) values(12, 'cent', '1234')";
        jdbcTemplate.update(sql);
        return "ok";
    }
}
