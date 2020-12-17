package com.scucourse.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.util.Map;

@Controller
public class UserController {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping({"/login","/login.html"})
    public String login(HttpSession session) {
        if (session.getAttribute("currentUser") != null) {
            return "redirect:/index";
        } else
            return "login";
    }
    @GetMapping({"/register","/register.html"})
    public String register(HttpSession session) {
        if (session.getAttribute("currentUser") != null) {
            return "redirect:/index";
        } else
            return "register";
    }
    @GetMapping({"/forgot-password","/forgot-password.html"})
    public String forgotPassword(HttpSession session) {
        if (session.getAttribute("currentUser") != null) {
            return "redirect:/index";
        } else
            return "forgot-password";
    }

    @GetMapping("/userLogin")
    public String userLogin(@RequestParam("username") String username,
                            @RequestParam("password") String password,
                            @RequestParam(value = "remember", required = false) String remember,
                            Model model,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        try {
            String sql = String.format("SELECT id,password,user_type FROM user_info WHERE username = \"%s\"", username);
            Map<String, Object> result = jdbcTemplate.queryForMap(sql);

            if (password.equals(result.get("password"))) {
                session.setAttribute("currentUser", username);
                session.setAttribute("currentUserId", result.get("id")); //数据类型为long
                session.setAttribute("currentUserType", result.get("user_type"));

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

    @GetMapping("/userRegister")
    public String userRegister(@RequestParam("username") String username,
                               @RequestParam("email") String email,
                               @RequestParam("password") String password,
                               @RequestParam("repeatPassword") String repeatPassword,
                               Model model) {

        if (!password.equals(repeatPassword)) {

        }
        else {
            String sql = String.format("INSERT INTO user_info(username, password, email) VALUES(\"%s\", \"%s\", \"%s\")", username, password, email);
            jdbcTemplate.update(sql);
        }
        return "redirect:/login";
    }
}
