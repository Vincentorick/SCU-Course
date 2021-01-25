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
public class IndexController {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping({ "index", "index.html"})
    public String index(Model model, HttpSession session) {
        String currentUser = (String)session.getAttribute("currentUser");
        String currentUserId = session.getAttribute("currentUserId").toString();
        String currentUserType = (String)session.getAttribute("currentUserType");
        String currentCourse = (String)session.getAttribute("currentCourse");

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("currentCourse", currentCourse);
        model.addAttribute("currentUserType", session.getAttribute("currentUserType"));

        try {
            String sql = String.format("SELECT creator FROM course_info WHERE id = %s", session.getAttribute("currentCourseId").toString());
            String creator = jdbcTemplate.queryForObject(sql, String.class);

            if (currentUserType.equals("admin") || creator.equals(session.getAttribute("currentUser"))) {
                model.addAttribute("memberType", "admin");
            }
            else
                model.addAttribute("memberType", "normal");
        } catch (Exception ignored) {}

        String sql = String.format("SELECT course_created,course_joined FROM user_info WHERE(username = \"%s\")", currentUser);
        Map<String, Object> userInfo = jdbcTemplate.queryForMap(sql);
        model.addAttribute("userInfo", userInfo);

        sql = String.format("SELECT * FROM course_info WHERE id IN (SELECT course_id FROM student_course WHERE student_id = %s)", currentUserId);
        List<Map<String, Object>> courses = jdbcTemplate.queryForList(sql);
        sql = String.format("SELECT course_id FROM student_course WHERE (student_id = %s)", currentUserId);
        List<Map<String, Object>> coursesJoined = jdbcTemplate.queryForList(sql);

        for (Map<String, Object> course : courses) {
            course.put("joined", 0);
            course.replace("date_created", course.get("date_created").toString().substring(0, 16));
        }

        // 扫描courses列表，如果已经加入课程，则将课程信息中的joined设置为1
        for (Map<String, Object> course : courses) {
            int courseId = (int) course.get("id");
            for (Map<String, Object> stringObjectMap : coursesJoined) {
                if ((int) stringObjectMap.get("course_id") == courseId) {
                    course.replace("joined", 1);
                    break;
                }
            }
        }
        model.addAttribute("courses", courses);
        return "index";
    }

    @GetMapping({"course-center", "course-center.html"})
    public String courseCenter(Model model, HttpSession session) {
        String currentUser = (String)session.getAttribute("currentUser");
        String currentUserId = session.getAttribute("currentUserId").toString();
        String currentCourse = (String)session.getAttribute("currentCourse");
        String currentUserType = (String) session.getAttribute("currentUserType");

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("currentCourse", currentCourse);
        model.addAttribute("currentUserType", currentUserType);

        try {
            String sql = String.format("SELECT creator FROM course_info WHERE id = %s", session.getAttribute("currentCourseId").toString());
            String creator = jdbcTemplate.queryForObject(sql, String.class);

            if (currentUserType.equals("admin") || creator.equals(session.getAttribute("currentUser"))) {
                model.addAttribute("memberType", "admin");
            }
            else
                model.addAttribute("memberType", "normal");
        } catch (Exception ignored) {}

        String sql = "SELECT * FROM course_info";
        List<Map<String, Object>> courses = jdbcTemplate.queryForList(sql);
        sql = String.format("SELECT course_id FROM student_course WHERE (student_id = %s)", currentUserId);
        List<Map<String, Object>> coursesJoined = jdbcTemplate.queryForList(sql);

        for (Map<String, Object> course : courses) {
            course.put("joined", 0);
            course.replace("date_created", course.get("date_created").toString().substring(0, 16));
        }

        // 扫描courses列表，如果已经加入课程，则将课程信息中的joined设置为1
        for (Map<String, Object> course : courses) {
            int courseId = (int) course.get("id");
            for (Map<String, Object> stringObjectMap : coursesJoined) {
                if ((int) stringObjectMap.get("course_id") == courseId) {
                    course.replace("joined", 1);
                    break;
                }
            }
        }
        model.addAttribute("courses", courses);
        return "course-center";
    }
}
