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
        try {
            String currentCourse = (String)session.getAttribute("currentCourse");
            long currentCourseId = (long)session.getAttribute("currentCourseId"); // 可能exception，未设置该属性

            model.addAttribute("currentUser", session.getAttribute("currentUser"));
            model.addAttribute("currentCourse", currentCourse);

            String sql = String.format("SELECT id,course_name,num_students,max_num_students,creator,date_created FROM course_info WHERE id = %d", currentCourseId);
            Map<String, Object> courseInfo = jdbcTemplate.queryForMap(sql);
            model.addAttribute("courseInfo", courseInfo);

            sql = String.format("SELECT student_id,grade FROM student_course WHERE (course_id = %d AND is_creator = 0)", currentCourseId);
            List<Map<String, Object>> students = jdbcTemplate.queryForList(sql);

            for (int i = 0; i < students.size(); ++i) {
                sql = String.format("SELECT username FROM user_info WHERE id = %d", (int)students.get(i).get("student_id"));
                String name = jdbcTemplate.queryForObject(sql, String.class);
                students.get(i).put("name", name);
            }
            model.addAttribute("students", students);

            if (courseInfo.get("creator").equals(session.getAttribute("currentUser"))) {
                model.addAttribute("memberType", "admin");
            }
            else {
                model.addAttribute("memberType", "normal");
            }
        }
        catch (Exception e) {
            return "redirect:/blank";
        }
        return "course";
    }

    @GetMapping("/removeStudent")
    public String removeStudent(@RequestParam("studentName") String studentName,
                                Model model, HttpSession session) {
        long currentCourseId = (long)session.getAttribute("currentCourseId");
        String sql = String.format("SELECT id FROM user_info WHERE username = \"%s\"", studentName);
        long studentId = (long)jdbcTemplate.queryForObject(sql, Integer.class);

        sql = String.format("DELETE FROM student_course WHERE student_id = %d and course_id = %d", studentId, currentCourseId);
        jdbcTemplate.update(sql);

        sql = String.format("UPDATE user_info SET course_joined = course_joined - 1 WHERE id = %d", studentId);
        jdbcTemplate.update(sql);

        sql = String.format("UPDATE course_info SET num_students = num_students - 1 WHERE id = %d", currentCourseId);
        jdbcTemplate.update(sql);

        return "redirect:/course";
    }

    @GetMapping("/courseUpdate")
    public String courseUpdate(@RequestParam("courseName") String courseName,
                               @RequestParam("maxNumStu") String maxNumStu,
                               @RequestParam("action") String action,
                               Model model, HttpSession session) {
        long currentUserId = (long)session.getAttribute("currentUserId");
        long currentCourseId = (long)session.getAttribute("currentCourseId");
        String sql;

        switch (action) {
            case "save":
                sql = String.format("UPDATE course_info SET course_name = \"%s\", max_num_students = %s WHERE id = %d", courseName, maxNumStu, currentCourseId);
                jdbcTemplate.update(sql);
                return "redirect:/course";
            case "delete":
                sql = String.format("DELETE FROM course_info WHERE id = %s", currentCourseId);
                jdbcTemplate.update(sql);

                sql = String.format("SELECT student_id FROM student_course WHERE (course_id = %d and is_creator = 0)", currentCourseId);
                List<Map<String, Object>> studentList = jdbcTemplate.queryForList(sql);
                for (int i = 0; i < studentList.size(); ++i) {
                    sql = String.format("UPDATE user_info SET course_joined = course_joined - 1 WHERE id = %d", (int)studentList.get(i).get("student_id"));
                    jdbcTemplate.update(sql);
                }

                sql = String.format("DELETE FROM student_course WHERE course_id = %s", currentCourseId);
                jdbcTemplate.update(sql);

                // update value course_created in user_info
                sql = String.format("UPDATE user_info SET course_created = course_created - 1 WHERE id = %d", currentUserId);
                jdbcTemplate.update(sql);

                return "redirect:/index";
            case "quit":
                sql = String.format("DELETE FROM student_course WHERE student_id = %d and course_id = %d", currentUserId, currentCourseId);
                jdbcTemplate.update(sql);
                // update value course_joined in user_info
                sql = String.format("UPDATE user_info SET course_joined = course_joined - 1 WHERE id = %d", currentUserId);
                jdbcTemplate.update(sql);
                // update value num_students in course_info
                sql = String.format("UPDATE course_info SET num_students = num_students - 1 WHERE id = %d", currentCourseId);
                jdbcTemplate.update(sql);
                return "redirect:/index";

            default:
                return "redirect:/course";

        }

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
                    return "redirect:/index";
                }

                return "redirect:/index";

            case "join":
                // search for course_id
                sql = String.format("SELECT id FROM course_info WHERE course_name = \"%s\"", courseName);
                courseId = jdbcTemplate.queryForObject(sql, Integer.class);

                // update table student_course
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
