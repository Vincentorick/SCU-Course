package com.scucourse.controller;

import com.scucourse.util.Formatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class IndexController {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping({"/index","/index.html"})
    public String index(Model model, HttpSession session) {
        model.addAttribute("username", session.getAttribute("currentUser"));
        String currentUser = (String)session.getAttribute("currentUser");
        String sql = String.format("SELECT course_created,course_joined FROM user_info WHERE(username = \"%s\")", currentUser);
        Map<String, Object> userInfo = jdbcTemplate.queryForMap(sql);
        model.addAttribute("userInfo", userInfo);

        sql = "SELECT course_name,num_students,creator,date_created FROM course_info";
        List<Map<String, Object>> courses = jdbcTemplate.queryForList(sql);
        model.addAttribute("courses", courses);

        return "index";
    }
}
