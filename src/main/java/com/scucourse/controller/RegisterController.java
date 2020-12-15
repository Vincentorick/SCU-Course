package com.scucourse.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RegisterController {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping("userRegister")
    public String userRegister(@RequestParam("username") String username,
                               @RequestParam("email") String email,
                               @RequestParam("password") String password,
                               @RequestParam("repeatPassword") String repeatPassword,
                               Model model) {

        if (!password.equals(repeatPassword)) {

        }
        else {
            String sql = String.format("insert into user_info(username, email, password) values(\"%s\", \"%s\", \"%s\")", username, email, password);
            jdbcTemplate.update(sql);
        }

        return "redirect:/login";
    }
}
