package com.scucourse.controller;

import com.scucourse.util.Formatter;
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

    @GetMapping({"/index","/index.html"})
    public String index(Model model, HttpSession session) {
        model.addAttribute("username", session.getAttribute("currentUser"));
        String currentUser = (String)session.getAttribute("currentUser");
        long currentUserId = (long)session.getAttribute("currentUserId");

        String sql = String.format("SELECT course_created,course_joined FROM user_info WHERE(username = \"%s\")", currentUser);
        Map<String, Object> userInfo = jdbcTemplate.queryForMap(sql);
        model.addAttribute("userInfo", userInfo);

        sql = "SELECT id,course_name,num_students,creator,date_created FROM course_info";
        List<Map<String, Object>> courses = jdbcTemplate.queryForList(sql);
        sql = String.format("SELECT course_id FROM student_course WHERE (student_id = %d)", currentUserId);
        List<Map<String, Object>> coursesCreated = jdbcTemplate.queryForList(sql);

        for (int i = 0; i < courses.size(); ++i) {
            courses.get(i).put("joined", 0);
        }

        // 扫描courses列表，如果已经加入课程，则将课程信息中的joined设置为1
        for (int i = 0; i < courses.size(); ++i) {
            long currentCourseId = (long)courses.get(i).get("id");
            for (int j = 0; j < coursesCreated.size(); ++j) {
                if ((int)coursesCreated.get(j).get("course_id") == currentCourseId) {
                    courses.get(i).replace("joined", 1);
                    break;
                }
            }
        }

        model.addAttribute("courses", courses);
        return "index";
    }
}
