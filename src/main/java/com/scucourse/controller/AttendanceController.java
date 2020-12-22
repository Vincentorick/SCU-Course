package com.scucourse.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Controller
public class AttendanceController {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping({"attendance", "attendance.html"})
    public String attendance(Model model, HttpSession session) {
        try {
            String currentUserType = (String)session.getAttribute("currentUserType");
            String currentCourse = (String)session.getAttribute("currentCourse");
            long currentCourseId = (long)session.getAttribute("currentCourseId"); // 可能exception，未设置该属性

            model.addAttribute("currentUser", session.getAttribute("currentUser"));
            model.addAttribute("currentUserType", currentUserType);
            model.addAttribute("currentCourse", currentCourse);

            String sql = String.format("SELECT creator FROM course_info WHERE id = %d", currentCourseId);
            String creator = jdbcTemplate.queryForObject(sql, String.class);

            if (currentUserType.equals("admin") || creator.equals(session.getAttribute("currentUser"))) {
                // 教师页面
                sql = String.format("SELECT student_id FROM student_course WHERE course_id = %d AND is_creator = 0", currentCourseId);
                List<Map<String, Object>> students = jdbcTemplate.queryForList(sql);

                for (Map<String, Object> student : students) {
                    sql = String.format("SELECT username FROM user_info WHERE id = %d", (int) student.get("student_id"));
                    String name = jdbcTemplate.queryForObject(sql, String.class);
                    student.put("name", name);
                }
                model.addAttribute("students", students);

                sql = String.format("SELECT * FROM attendance_info WHERE course_id = %d", currentCourseId);
                List<Map<String, Object>> attendanceList = jdbcTemplate.queryForList(sql);
                for (Map<String, Object> attendance : attendanceList) {
                    attendance.replace("start_time", attendance.get("start_time").toString().substring(0, 16));
                    attendance.replace("end_time", attendance.get("end_time").toString().substring(0, 16));
                }
                model.addAttribute("attendanceList", attendanceList);
                model.addAttribute("memberType", "admin");
            }
            else {
                // 学生页面
                sql = String.format("SELECT * FROM attendance_info INNER JOIN student_attendance ON attendance_info.id = student_attendance.attendance_id " +
                        "WHERE student_id = %d", (long) session.getAttribute("currentUserId"));
                List<Map<String, Object>> attendanceList_stu = jdbcTemplate.queryForList(sql);

                Date date = new Date();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String currentTime = sdf.format(date);
                for (Map<String, Object> attendance : attendanceList_stu) {
                    attendance.put("status", " ");

                    if (currentTime.compareTo(attendance.get("start_time").toString()) < 0)
                        attendance.replace("status", "尚未开始");
                    else if (currentTime.compareTo(attendance.get("start_time").toString()) > 0 &&
                            currentTime.compareTo(attendance.get("end_time").toString()) < 0)
                        attendance.replace("status", "正在签到");
                    else
                        attendance.replace("status", "已经结束");

                    attendance.replace("start_time", attendance.get("start_time").toString().substring(0, 16));
                    attendance.replace("end_time", attendance.get("end_time").toString().substring(0, 16));
                    attendance.put("status_student", (int) attendance.get("attended") == 1 ? "已签到" : "未签到");
                }
                Collections.reverse(attendanceList_stu);
                model.addAttribute("attendanceList_stu", attendanceList_stu);
                model.addAttribute("memberType", "normal");
            }
        }
        catch (NullPointerException e) {
            return "redirect:blank";
        }
        return "attendance";
    }

    @GetMapping("attendanceCreate")
    public String attendanceCreate(@RequestParam("title") String title,
                                   @RequestParam("startTime") String startTime,
                                   @RequestParam("endTime") String endTime,
                                   HttpSession session) {
        String currentUser = (String)session.getAttribute("currentUser");
        long currentCourseId = (long)session.getAttribute("currentCourseId");

        String sql = String.format("SELECT num_students FROM course_info WHERE id = %d", currentCourseId);
        long num_total = (long)jdbcTemplate.queryForObject(sql, Integer.class);
        sql = String.format("INSERT INTO attendance_info(course_id,creator,title,start_time,end_time,num_total) VALUES(%d,\"%s\",\"%s\",\"%s\",\"%s\",%d)",
                currentCourseId, currentUser, title, startTime.replace('T',' '), endTime.replace('T',' '), num_total);
        jdbcTemplate.update(sql);

        sql = String.format("SELECT student_id FROM student_course WHERE course_id = %d AND is_creator = 0", currentCourseId);
        List<Map<String, Object>> students = jdbcTemplate.queryForList(sql);

        sql = String.format("SELECT MAX(id) FROM attendance_info WHERE course_id = %d", currentCourseId);
        long currentAttendanceId = jdbcTemplate.queryForObject(sql, Integer.class);

        for (Map<String, Object> student : students) {
            sql = String.format("INSERT INTO student_attendance(student_id,attendance_id) VALUES(%d, %d)",
                    (int)student.get("student_id"), currentAttendanceId);
            jdbcTemplate.update(sql);
        }
        return "redirect:attendance";
    }

    @GetMapping("attendanceDelete")
    public String attendanceDelete(@RequestParam("attendanceId") String attendanceId,
                                   HttpSession session) {
        String sql = String.format("DELETE FROM attendance_info WHERE id = %s", attendanceId);
        jdbcTemplate.update(sql);

        sql = String.format("DELETE FROM student_attendance WHERE attendance_id = %s", attendanceId);
        jdbcTemplate.update(sql);
        return "redirect:attendance";
    }

    @GetMapping("studentAttend")
    public String studentAttend(@RequestParam("attendanceId") String attendanceId,
                                HttpSession session) {
        long currentUserId = (long)session.getAttribute("currentUserId");

        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String sql = String.format("UPDATE student_attendance SET attended = 1, time = \"%s\" WHERE student_id = %d and attendance_id = %s", sdf.format(date), currentUserId, attendanceId);
        jdbcTemplate.update(sql);

        sql = String.format("UPDATE attendance_info SET num_attended = num_attended + 1 WHERE id = %s", attendanceId);
        jdbcTemplate.update(sql);

        return "redirect:attendance";
    }

    @PostMapping("attendance-detail")
    public String attendanceDetail(@RequestParam("attendanceId") String attendanceId,
                                   Model model, HttpSession session) {
        String currentUserType = (String)session.getAttribute("currentUserType");
        String currentCourse = (String)session.getAttribute("currentCourse");

        model.addAttribute("currentUser", session.getAttribute("currentUser"));
        model.addAttribute("currentUserType", currentUserType);
        model.addAttribute("currentCourse", currentCourse);

        String sql = String.format("SELECT * FROM attendance_info WHERE id = %s", attendanceId);
        Map<String, Object> attendanceDetail = jdbcTemplate.queryForMap(sql);
        model.addAttribute("attendanceDetail", attendanceDetail);

        attendanceDetail.replace("start_time", attendanceDetail.get("start_time").toString().substring(0, 16));
        attendanceDetail.replace("end_time", attendanceDetail.get("end_time").toString().substring(0, 16));

        sql = String.format("SELECT * FROM student_attendance INNER JOIN user_info ON student_attendance.student_id = user_info.id WHERE attendance_id = %s", attendanceId);
        List<Map<String, Object>> studentList = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> student : studentList) {
            try {
                student.replace("time", student.get("time").toString().substring(0, 16));
            }
            catch (NullPointerException e) {
                student.replace("time", " ");
            }
            student.replace("attended", (int)student.get("attended") == 1? "已签到" : "未签到");
        }
        model.addAttribute("studentList", studentList);
        return "attendance-detail";
    }
}