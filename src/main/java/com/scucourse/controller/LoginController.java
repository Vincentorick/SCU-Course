package com.scucourse.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;

@Controller
public class LoginController {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping("/userLogin")
    public String userLogin(@RequestParam("username") String username,
                            @RequestParam("password") String password,
                            @RequestParam(value = "remember", required = false) String remember,
                            Model model,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        try {
            String sql = String.format("select password from user_info where username = \"%s\"", username);
            String result = (String)jdbcTemplate.queryForObject(sql, String.class);
            if (password.equals(result)) {
                session.setAttribute("loginUser", username);
                if (remember != null) {
                    session.setMaxInactiveInterval(86400);
                }
                return "redirect:/index";
            }
            else {
                redirectAttributes.addFlashAttribute("msg", "密码错误");
                return "redirect:/login";
            }
        }
        catch (Exception e) {
            redirectAttributes.addFlashAttribute("msg", "用户不存在或用户名输入错误！");
            return "redirect:/login";
        }
    }

    @GetMapping("/userLogout")
    public String userLogout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
