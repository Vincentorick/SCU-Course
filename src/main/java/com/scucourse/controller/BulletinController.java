package com.scucourse.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.*;

@Controller
public class BulletinController {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping({"/bulletin", "/bulletin.html"})
    public String bulletin(Model model, HttpSession session) {
        try {
            String currentCourse = (String)session.getAttribute("currentCourse");
            long currentCourseId = (long)session.getAttribute("currentCourseId");

            model.addAttribute("currentCourse", currentCourse);

            String sql = String.format("SELECT * FROM bulletin WHERE course_id = %d", currentCourseId);
            List<Map<String, Object>> bulletins = jdbcTemplate.queryForList(sql);
            Collections.reverse(bulletins);

            model.addAttribute("bulletins", bulletins);
            return "bulletin";
        }
        catch (Exception e) {
            return "redirect:/blank";
        }
    }

    @GetMapping("bulletinCreate")
    public String bulletinCreate(@RequestParam("title") String title,
                                 @RequestParam("content") String content,
                                 Model model, HttpSession session) {
        String currentUser = (String)session.getAttribute("currentUser");
        long currentCourseId = (long)session.getAttribute("currentCourseId");

        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String sql = String.format("INSERT INTO bulletin(course_id,title,creator,date_created,content) VALUES(%d, \"%s\", \"%s\", \"%s\", \"%s\")",
                currentCourseId, title, currentUser, sdf.format(date), content);
        jdbcTemplate.update(sql);

        return "redirect:/bulletin";
    }


}
