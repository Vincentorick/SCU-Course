package com.scucourse.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @RequestMapping("/userLogin")
    public String userLogin(@RequestParam("username") String username,
                            @RequestParam("password") String password,
                            Model model) {
        try {
            String sql = "select password from user_info where username = \"" + username + "\"";
            String result = (String)jdbcTemplate.queryForObject(sql, String.class);
            if (password.equals(result))
                return "redirect:/index";
            else {
                model.addAttribute("msg", "密码错误！");
                return "login";
            }
        }
        catch (Exception e) {
            model.addAttribute("msg", "用户不存在或用户名输入错误！");
            return "login";
        }
    }
}
