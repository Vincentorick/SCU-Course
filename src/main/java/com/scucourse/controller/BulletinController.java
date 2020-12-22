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

    @GetMapping({"bulletin", "bulletin.html"})
    public String bulletin(Model model, HttpSession session) {
        try {
            String currentCourse = (String) session.getAttribute("currentCourse");
            long currentCourseId = (long) session.getAttribute("currentCourseId");
            String currentUserType = (String) session.getAttribute("currentUserType");

            model.addAttribute("currentCourse", currentCourse);
            model.addAttribute("currentUser", (String) session.getAttribute("currentUser"));

            String sql = String.format("SELECT * FROM bulletin_info WHERE course_id = %d", currentCourseId);
            List<Map<String, Object>> bulletins = jdbcTemplate.queryForList(sql);
            Collections.reverse(bulletins);

            for (Map<String, Object> bulletin : bulletins)
                bulletin.replace("date_created", bulletin.get("date_created").toString().substring(0, 16));

            model.addAttribute("bulletins", bulletins);

            sql = String.format("SELECT creator FROM course_info WHERE id = %d", currentCourseId);
            String creator = jdbcTemplate.queryForObject(sql, String.class);
            if (currentUserType.equals("admin") || creator.equals(session.getAttribute("currentUser")))
                model.addAttribute("memberType", "admin");
            else
                model.addAttribute("memberType", "normal");
        }
        catch (NullPointerException e) {
            return "redirect:blank";
        }
        return "bulletin";
    }

    @GetMapping("bulletinCreate")
    public String bulletinCreate(@RequestParam("title") String title,
                                 @RequestParam("content") String content,
                                 HttpSession session) {
        String currentUser = (String)session.getAttribute("currentUser");
        long currentCourseId = (long)session.getAttribute("currentCourseId");

        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String sql = String.format("INSERT INTO bulletin_info(course_id,title,creator,date_created,content) VALUES(%d, \"%s\", \"%s\", \"%s\", \"%s\")",
                currentCourseId, title, currentUser, sdf.format(date), content);
        jdbcTemplate.update(sql);

        return "redirect:bulletin";
    }

    @GetMapping("bulletinDelete")
    public String bulletinDelete(@RequestParam("bulletinId") String bulletinId,
                                 HttpSession session) {
        String sql = String.format("DELETE FROM bulletin_info WHERE id = %s", bulletinId);
        jdbcTemplate.update(sql);
        return "redirect:bulletin";
    }
}
