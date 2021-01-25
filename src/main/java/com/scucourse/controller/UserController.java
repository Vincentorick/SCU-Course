package com.scucourse.controller;

import com.scucourse.util.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Random;

@Controller
public class UserController {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping("/")
    public String root() {
        return "redirect:index";
    }

    @GetMapping({"login","login.html"})
    public String login(HttpSession session) {
        if (session.getAttribute("currentUser") != null) {
            return "redirect:index";
        } else
            return "login";
    }
    @GetMapping({"register","register.html"})
    public String register(HttpSession session) {
        if (session.getAttribute("currentUser") != null) {
            return "redirect:index";
        } else
            return "register";
    }
    @GetMapping({"forgot-password","forgot-password.html"})
    public String forgotPassword(HttpSession session) {
        if (session.getAttribute("currentUser") != null) {
            return "redirect:index";
        } else
            return "forgot-password";
    }

    @GetMapping("userLogin")
    public String userLogin(@RequestParam("username") String username,
                            @RequestParam("password") String password,
                            @RequestParam(value = "remember", required = false) String remember,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        try {
            String sql = String.format("SELECT * FROM user_info WHERE username = \"%s\"", username);
            Map<String, Object> result = jdbcTemplate.queryForMap(sql);

            if (password.equals(result.get("password"))) {
                session.setAttribute("currentUser", username);
                session.setAttribute("currentUserId", result.get("id"));
                session.setAttribute("currentUserType", result.get("user_type"));

                if (remember != null) {
                    session.setMaxInactiveInterval(86400);
                }
                return "redirect:index";
            }
            else {
                redirectAttributes.addFlashAttribute("message", "密码错误");
                return "redirect:login";
            }
        }
        catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "用户名输入错误");
            return "redirect:login";
        }
    }

    @GetMapping("userLogout")
    public String userLogout(HttpSession session) {
        session.invalidate();
        return "redirect:login";
    }

    @GetMapping("userRegister")
    public String userRegister(@RequestParam("username") String username,
                               @RequestParam("email") String email,
                               @RequestParam("password") String password,
                               @RequestParam("repeatPassword") String repeatPassword,
                               @RequestParam("type") String type,
                               RedirectAttributes redirectAttributes) {
        String emailRule = "[a-zA-Z0-9_.-]+@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*\\.[a-zA-Z0-9]{2,6}$";
        if (username.equals("")) {
            redirectAttributes.addFlashAttribute("message", "注册失败：用户名不能为空");
            return "redirect:register";
        }
        if (email.equals("")) {
            redirectAttributes.addFlashAttribute("message", "注册失败：邮箱不能为空");
            return "redirect:register";
        }
        if (!email.matches(emailRule)) {
            redirectAttributes.addFlashAttribute("message", "注册失败：邮箱格式不正确");
            return "redirect:register";
        }
        if (password.equals("")) {
            redirectAttributes.addFlashAttribute("message", "注册失败：密码不能为空");
            return "redirect:register";
        }
        if (!password.equals(repeatPassword)) {
            redirectAttributes.addFlashAttribute("message", "注册失败：两次密码输入不一致");
            return "redirect:register";
        }

        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            String sql = String.format("INSERT INTO user_info(user_type, username, password, email, register_date) VALUES(\"%s\", \"%s\", \"%s\", \"%s\", \"%s\")",
                    type, username, password, email, sdf.format(date));
            jdbcTemplate.update(sql);
        }
        catch (DuplicateKeyException e) {
            redirectAttributes.addFlashAttribute("message", "注册失败：用户名已被占用");
        }

        redirectAttributes.addFlashAttribute("message", "注册成功，欢迎！");
        return "redirect:login";
    }

    @Autowired
    private JavaMailSender javaMailSender;

    @GetMapping("resetPassword")
    public String resetPassword(@RequestParam("username") String username,
                                @RequestParam("code") String code,
                                @RequestParam("newPassword") String newPassword,
                                @RequestParam("action") String action,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        if (username.equals("")) {
            redirectAttributes.addFlashAttribute("message", "请输入用户名");
            redirectAttributes.addFlashAttribute("lastUsername", username);
            return "redirect:forgot-password";
        }

        String sql, email;
        try {
            sql = String.format("SELECT email FROM user_info WHERE username = \"%s\"", username);
            email = jdbcTemplate.queryForObject(sql, String.class);
        }
        catch (EmptyResultDataAccessException e) {
            redirectAttributes.addFlashAttribute("message", "用户不存在");
            redirectAttributes.addFlashAttribute("lastUsername", username);
            return "redirect:forgot-password";
        }

        switch (action) {
            case "send":
                SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
                simpleMailMessage.setFrom("xm06_scu_class@163.com");
                simpleMailMessage.setTo(email);

                Random random = new Random();
                long verificationCode;
                do {
                    verificationCode = (long)(random.nextDouble() * 1000000);
                } while (verificationCode < 100000);

                session.setAttribute("code", verificationCode);

                simpleMailMessage.setSubject("SCU Course 重置密码身份验证");
                simpleMailMessage.setText(username + "：\n\t您好，用于SCU Course账号找回密码的验证码是 " + verificationCode + " ，有效期30分钟。如有其他问题欢迎联系 vincentorick@163.com 咨询。\n\nSCU Course团队");

                javaMailSender.send(simpleMailMessage);

                redirectAttributes.addFlashAttribute("message", "验证码发送成功");
                redirectAttributes.addFlashAttribute("lastUsername", username);
                return "redirect:forgot-password";

            case "reset":
                try {
                    if (code.equals("")) {
                        redirectAttributes.addFlashAttribute("message", "请输入验证码");
                        redirectAttributes.addFlashAttribute("lastUsername", username);
                        return "redirect:forgot-password";
                    }
                    if (!code.equals(session.getAttribute("code").toString())) {
                        redirectAttributes.addFlashAttribute("message", "验证码错误");
                        redirectAttributes.addFlashAttribute("lastUsername", username);
                        return "redirect:forgot-password";
                    }
                    if (newPassword.equals("")) {
                        redirectAttributes.addFlashAttribute("message", "请输入新密码");
                        redirectAttributes.addFlashAttribute("lastUsername", username);
                        return "redirect:forgot-password";
                    }

                    sql = String.format("UPDATE user_info SET password = \"%s\" WHERE username = \"%s\"", newPassword, username);
                    jdbcTemplate.update(sql);

                    session.removeAttribute("code");
                    redirectAttributes.addFlashAttribute("message", "密码重置成功，欢迎登录！");
                    return "redirect:login";
                }
                catch (NullPointerException e) {
                    redirectAttributes.addFlashAttribute("message", "尚未发送验证码");
                    redirectAttributes.addFlashAttribute("lastUsername", username);
                    return "redirect:forgot-password";
                }
        }
        return "redirect:forgot-password";
    }
}
