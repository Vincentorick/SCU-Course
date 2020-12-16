package com.scucourse.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Controller
public class CourseController {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping({"/course", "/course.html"})
    public String course(Model model, HttpSession session) {
        String currentCourse = (String)session.getAttribute("currentCourse");
        model.addAttribute("currentCourse", currentCourse);
        return "/course";
    }

    @GetMapping("/courseAction")
    public String courseAction(@RequestParam("courseName") String courseName,
                               @RequestParam("action") String action,
                               Model model, HttpSession session) {
        String sql;
        String currentUser = (String)session.getAttribute("currentUser");
        long currentUserId = (long)session.getAttribute("currentUserId");
        long courseId;

        switch (action) {
            case "create":
                // update table course_info
                Date date = new Date();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                try {
                    // 在course_info中增加条目
                    sql = String.format("INSERT INTO course_info(course_name,creator,date_created) VALUES(\"%s\", \"%s\", \"%s\")",
                        courseName, currentUser, sdf.format(date));
                    jdbcTemplate.update(sql);

                    // update table student_course
                    sql = String.format("SELECT id FROM course_info WHERE course_name = \"%s\"", courseName);
                    courseId = jdbcTemplate.queryForObject(sql, Integer.class);
                    sql = String.format("INSERT INTO student_course(student_id,course_id,is_creator) VALUES(%d,%d,1)", currentUserId, courseId);
                    jdbcTemplate.update(sql);

                    // update value course_created
                    sql = String.format("UPDATE user_info SET course_created = course_created + 1 WHERE username = \"%s\"", currentUser);
                    jdbcTemplate.update(sql);
                }
                catch (Exception e) {
                    System.out.println(e.getMessage());
                    return "redirect:index";
                }

                return "redirect:/index";

            case "join":
                // search for course_id
                sql = String.format("SELECT id FROM course_info WHERE course_name = \"%s\"", courseName);
                courseId = jdbcTemplate.queryForObject(sql, Integer.class);

                // update table course_info
                sql = String.format("INSERT INTO student_course(student_id,course_id) VALUES(%d,%d)", currentUserId, courseId);
                jdbcTemplate.update(sql);

                // update value course_joined in user_info
                sql = String.format("UPDATE user_info SET course_joined = course_joined + 1 WHERE username = \"%s\"", currentUser);
                jdbcTemplate.update(sql);

                // update value num_students in course_info
                sql = String.format("UPDATE course_info SET num_students = num_students + 1 WHERE course_name = \"%s\"", courseName);
                jdbcTemplate.update(sql);

                return "redirect:/index";

            case "enter":
                // search for course_id
                sql = String.format("SELECT id FROM course_info WHERE course_name = \"%s\"", courseName);
                courseId = jdbcTemplate.queryForObject(sql, Integer.class);

                session.setAttribute("currentCourse", courseName);
                session.setAttribute("currentCourseId", courseId);
                return "redirect:/course";

            default:
                return "redirect:/index";
        }
    }
}
