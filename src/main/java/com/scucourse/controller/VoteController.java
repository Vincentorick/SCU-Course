package com.scucourse.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@Controller
public class VoteController {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping({"vote", "vote.html"})
    public String vote(Model model, HttpSession session) {
        try {
            String currentCourse = (String)session.getAttribute("currentCourse");
            long currentCourseId = (long)session.getAttribute("currentCourseId"); // 可能exception，未设置该属性

            model.addAttribute("currentUser", session.getAttribute("currentUser"));
            model.addAttribute("currentCourse", currentCourse);

            String sql = String.format("SELECT * FROM course_info WHERE id = %d", currentCourseId);
            Map<String, Object> courseInfo = jdbcTemplate.queryForMap(sql);
            model.addAttribute("courseInfo", courseInfo);

            sql = String.format("SELECT student_id,grade FROM student_course WHERE course_id = %d AND is_creator = 0", currentCourseId);
            List<Map<String, Object>> students = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> student : students) {
                sql = String.format("SELECT username FROM user_info WHERE id = %d", (int) student.get("student_id"));
                String name = jdbcTemplate.queryForObject(sql, String.class);
                student.put("name", name);
            }
            model.addAttribute("students", students);

            if (courseInfo.get("creator").equals(session.getAttribute("currentUser")))
                model.addAttribute("memberType", "admin");
            else
                model.addAttribute("memberType", "normal");
        }
        catch (NullPointerException e) {
            return "redirect:blank";
        }
        return "vote";
    }
}
