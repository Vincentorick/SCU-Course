package com.scucourse.controller;

import com.scucourse.storage.StorageFileNotFoundException;
import com.scucourse.storage.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Controller
public class HomeworkController {

    private final StorageService storageService;

    @Autowired
    public HomeworkController(StorageService storageService) {
        this.storageService = storageService;
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping({"homework", "homework.html"})
    public String homework(Model model, HttpSession session) {
        try {
            String currentUserType = (String) session.getAttribute("currentUserType");
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

                sql = String.format("SELECT * FROM homework_info WHERE course_id = %d", currentCourseId);
                List<Map<String, Object>> homeworkList = jdbcTemplate.queryForList(sql);
                for (Map<String, Object> homework : homeworkList) {
                    homework.replace("deadline", homework.get("deadline").toString().substring(0, 16));
                }
                model.addAttribute("homeworkList", homeworkList);
                model.addAttribute("memberType", "admin");
            }
            else {
                // 学生页面
                sql = String.format("SELECT * FROM homework_info INNER JOIN student_homework ON homework_info.id = student_homework.homework_id " +
                        "WHERE student_id = %d", (long) session.getAttribute("currentUserId"));
                List<Map<String, Object>> homeworkList_stu = jdbcTemplate.queryForList(sql);

                Date date = new Date();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String currentTime = sdf.format(date);
                for (Map<String, Object> homework : homeworkList_stu) {
                    homework.put("status", " ");

                    if (currentTime.compareTo(homework.get("deadline").toString()) < 0)
                        homework.replace("status", "未截止");
                    else
                        homework.replace("status", "已截止");

                    homework.replace("deadline", homework.get("deadline").toString().substring(0, 16));
                    homework.put("status_student", (int)homework.get("submitted") == 1 ? "已提交" : "未提交");

                    if ((int)homework.get("submitted") == 1) {
                        sql = String.format("SELECT * FROM file_info WHERE id = %d", (int) homework.get("file_id"));
                        Map<String, Object> fileInfo = jdbcTemplate.queryForMap(sql);
                        homework.put("fileId", (long) fileInfo.get("id"));
                        homework.put("fileName", (String) fileInfo.get("file_name"));
                        homework.put("fileUrl", "files/" + (String) fileInfo.get("file_name"));
                    }
                }
                Collections.reverse(homeworkList_stu);
                model.addAttribute("homeworkList_stu", homeworkList_stu);
                model.addAttribute("memberType", "normal");
            }
        }
        catch (NullPointerException e) {
            return "redirect:blank";
        }
        return "homework";
    }

    @GetMapping("homeworkCreate")
    public String homeworkCreate(@RequestParam("title") String title,
                                 @RequestParam("deadline") String deadline,
                                 @RequestParam("description") String description,
                                 HttpSession session) {
        String currentUser = (String)session.getAttribute("currentUser");
        long currentCourseId = (long)session.getAttribute("currentCourseId");

        String sql = String.format("SELECT num_students FROM course_info WHERE id = %d", currentCourseId);
        long num_total = (long)jdbcTemplate.queryForObject(sql, Integer.class);
        sql = String.format("INSERT INTO homework_info(course_id,creator,title,description,deadline,num_total) VALUES(%d,\"%s\",\"%s\",\"%s\",\"%s\",%d)",
                currentCourseId, currentUser, title, description, deadline.replace('T',' '), num_total);
        jdbcTemplate.update(sql);

        sql = String.format("SELECT student_id FROM student_course WHERE course_id = %d AND is_creator = 0", currentCourseId);
        List<Map<String, Object>> students = jdbcTemplate.queryForList(sql);

        sql = String.format("SELECT MAX(id) FROM homework_info WHERE course_id = %d", currentCourseId);
        long currentHomeworkId = jdbcTemplate.queryForObject(sql, Integer.class);

        for (Map<String, Object> student : students) {
            sql = String.format("INSERT INTO student_homework(student_id,homework_id) VALUES(%d, %d)",
                    (int)student.get("student_id"), currentHomeworkId);
            jdbcTemplate.update(sql);
        }
        return "redirect:homework";
    }

    @GetMapping("homeworkDelete")
    public String homeworkDelete(@RequestParam("homeworkId") String homeworkId) throws IOException {
        String sql = String.format("DELETE FROM homework_info WHERE id = %s", homeworkId);
        jdbcTemplate.update(sql);

        sql = String.format("SELECT file_id FROM student_homework WHERE homework_id = %s", homeworkId);
        List<Map<String, Object>> fileIds = jdbcTemplate.queryForList(sql);

        for (Map<String, Object> fileId : fileIds) {
            sql = String.format("SELECT file_name FROM file_info WHERE id = %d", (int)fileId.get("file_id"));
            String fileName = jdbcTemplate.queryForObject(sql, String.class);

            sql = String.format("DELETE FROM file_info WHERE id = %s", (int)fileId.get("file_id"));
            jdbcTemplate.update(sql);
            storageService.delete(fileName);
        }

        sql = String.format("DELETE FROM student_homework WHERE homework_id = %s", homeworkId);
        jdbcTemplate.update(sql);

        return "redirect:homework";
    }

    @GetMapping("homeworkDelete-stu")
    public String homeworkDeleteStu(@RequestParam("homeworkId") String homeworkId,
                                    @RequestParam("fileName") String fileName,
                                    @RequestParam("fileId") String fileId,
                                    HttpSession session) throws IOException {
        storageService.delete(fileName);
        String sql = String.format("DELETE FROM file_info WHERE id = %s", fileId);
        jdbcTemplate.update(sql);

        sql = String.format("UPDATE student_homework SET submitted = 0, time = 0, file_id = 0 WHERE student_id = %d and homework_id = %s",
                (long)session.getAttribute("currentUserId"), homeworkId);
        jdbcTemplate.update(sql);

        sql = String.format("UPDATE homework_info SET num_submitted = num_submitted - 1 WHERE id = %s", homeworkId);
        jdbcTemplate.update(sql);

        return "redirect:homework";
    }

    @PostMapping("homeworkSubmit")
    public String homeworkSumbit(@RequestParam("file") MultipartFile file,
                                 @RequestParam("homeworkId") String homeworkId,
                                 RedirectAttributes redirectAttributes,
                                 HttpSession session) {
        if (file.isEmpty()){
            redirectAttributes.addFlashAttribute("message", "请选择文件！");
            return "redirect:homework";
        }

        storageService.store(file);

        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String sql = String.format("INSERT INTO file_info(course_id,file_name,source,size,creator,date_created) VALUES(%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\")",
                (long) session.getAttribute("currentCourseId"), file.getOriginalFilename(), "student_homework", file.getSize(), session.getAttribute("currentUser"), sdf.format(date));
        jdbcTemplate.update(sql);

        sql = String.format("SELECT MAX(id) FROM file_info WHERE course_id = %d", (long)session.getAttribute("currentCourseId"));
        int fileId = (int)jdbcTemplate.queryForObject(sql, Integer.class);

        sql = String.format("UPDATE student_homework SET submitted = 1, time = \"%s\", file_id = %d WHERE student_id = %d and homework_id = %s",
                sdf.format(date), fileId, (long)session.getAttribute("currentUserId"), homeworkId);
        jdbcTemplate.update(sql);

        sql = String.format("UPDATE homework_info SET num_submitted = num_submitted + 1 WHERE id = %s", homeworkId);
        jdbcTemplate.update(sql);

        return "redirect:homework";
    }

    @PostMapping("homework-detail")
    public String homeworkDetail(@RequestParam("homeworkId") String homeworkId,
                                 Model model, HttpSession session) {
        String currentUserType = (String) session.getAttribute("currentUserType");
        String currentCourse = (String)session.getAttribute("currentCourse");

        model.addAttribute("currentUser", session.getAttribute("currentUser"));
        model.addAttribute("currentUserType", currentUserType);
        model.addAttribute("currentCourse", currentCourse);

        String sql = String.format("SELECT * FROM homework_info WHERE id = %s", homeworkId);
        Map<String, Object> homeworkDetail = jdbcTemplate.queryForMap(sql);
        model.addAttribute("homeworkDetail", homeworkDetail);

        homeworkDetail.replace("deadline", homeworkDetail.get("deadline").toString().substring(0, 16));

        sql = String.format("SELECT * FROM student_homework INNER JOIN user_info ON student_homework.student_id = user_info.id WHERE homework_id = %s", homeworkId);
        List<Map<String, Object>> studentList = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> student : studentList) {
            try {
                student.replace("time", student.get("time").toString().substring(0, 16));
            }
            catch (NullPointerException e) {
                student.replace("time", " ");
            }
            student.put("submit_status", (int)student.get("submitted") == 1? "已提交" : "未提交");

            if ((int)student.get("submitted") == 1) {
                sql = String.format("SELECT * FROM file_info WHERE id = %d", (int) student.get("file_id"));
                Map<String, Object> fileInfo = jdbcTemplate.queryForMap(sql);

                student.put("fileName", fileInfo.get("file_name"));
                student.put("fileUrl", "files/" + fileInfo.get("file_name"));
            }
        }
        model.addAttribute("studentList", studentList);
        return "homework-detail";
    }
}
