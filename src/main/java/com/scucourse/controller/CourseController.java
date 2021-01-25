package com.scucourse.controller;

import com.scucourse.storage.StorageService;
import com.scucourse.util.Log;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Controller
public class CourseController {

    private final StorageService storageService;
    Log log = new Log();

    @Autowired
    public CourseController(StorageService storageService) {
        this.storageService = storageService;
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping({"course", "course.html"})
    public String course(Model model, HttpSession session) {
        try {
            String currentCourse = (String) session.getAttribute("currentCourse");
            String currentCourseId = session.getAttribute("currentCourseId").toString(); // 可能exception，未设置该属性

            model.addAttribute("currentUser", session.getAttribute("currentUser"));
            model.addAttribute("currentUserType", session.getAttribute("currentUserType"));
            model.addAttribute("currentCourse", currentCourse);

            String sql = String.format("SELECT * FROM course_info WHERE id = %s", currentCourseId);
            Map<String, Object> courseInfo = jdbcTemplate.queryForMap(sql);
            courseInfo.replace("date_created", courseInfo.get("date_created").toString().substring(0, 16));
            model.addAttribute("courseInfo", courseInfo);

            sql = String.format("SELECT * FROM student_course WHERE course_id = %s AND is_creator = 0", currentCourseId);
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
        catch (Exception e) {
            System.out.println(e);
            return "redirect:blank";
        }
        return "course";
    }

    // 来自index页面的请求
    @GetMapping("courseAction")
    public String courseAction(@RequestParam("courseName") String courseName,
                               @RequestParam("action") String action,
                               @RequestParam(value = "courseIdToEnter", required = false) String courseIdToEnter,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        String sql;
        String currentUser = (String)session.getAttribute("currentUser");
        String currentUserId = session.getAttribute("currentUserId").toString();
        int courseId;

        switch (action) {
            case "create":
                if (courseName.equals("")) {
                    redirectAttributes.addFlashAttribute("message", "请输入课程名");
                    return "redirect:course-center";
                }

                sql = String.format("SELECT * FROM course_info WHERE course_name = \"%s\"", courseName);
                try {
                    if (!jdbcTemplate.queryForMap(sql).isEmpty()) {
                        redirectAttributes.addFlashAttribute("message", "课程名已被占用");
                        return "redirect:course-center";
                    }
                } catch (Exception ignored) {}

                Date date = new Date();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                sql = String.format("INSERT INTO course_info(course_name,creator,date_created) VALUES(\"%s\", \"%s\", \"%s\")",
                        courseName, currentUser, sdf.format(date));
                jdbcTemplate.update(sql);

                sql = String.format("SELECT id FROM course_info WHERE course_name = \"%s\"", courseName);
                courseId = jdbcTemplate.queryForObject(sql, Integer.class);
                sql = String.format("INSERT INTO student_course(student_id,course_id,is_creator) VALUES(%s,%d,1)", currentUserId, courseId);
                jdbcTemplate.update(sql);

                sql = String.format("UPDATE user_info SET course_created = course_created + 1 WHERE username = \"%s\"", currentUser);
                jdbcTemplate.update(sql);

                session.setAttribute("currentCourse", courseName);
                session.setAttribute("currentCourseId", courseId);

                redirectAttributes.addFlashAttribute("message", "创建成功");
                log.logAction(session.getAttribute("currentUserId").toString(), session.getAttribute("currentCourseId").toString(),
                        "创建课程：" + courseName);

                return "redirect:course-center";

            case "join":
                sql = String.format("SELECT id FROM course_info WHERE course_name = \"%s\"", courseName);
                courseId = jdbcTemplate.queryForObject(sql, Integer.class);

                sql = String.format("INSERT INTO student_course(student_id,course_id) VALUES(%s,%d)", currentUserId, courseId);
                jdbcTemplate.update(sql);

                sql = String.format("UPDATE user_info SET course_joined = course_joined + 1 WHERE username = \"%s\"", currentUser);
                jdbcTemplate.update(sql);

                sql = String.format("UPDATE course_info SET num_students = num_students + 1 WHERE course_name = \"%s\"", courseName);
                jdbcTemplate.update(sql);

                try {
                    sql = String.format("SELECT * FROM attendance_info WHERE course_id = %s", courseId);
                    List<Map<String, Object>> attendances = jdbcTemplate.queryForList(sql);

                    for (Map<String, Object> attendance : attendances) {
                        sql = String.format("INSERT INTO student_attendance(student_id,attendance_id) VALUES(%s, %s)",
                                currentUserId, attendance.get("id").toString());
                        jdbcTemplate.update(sql);
                    }

                    sql = String.format("SELECT * FROM homework_info WHERE course_id = %s", courseId);
                    List<Map<String, Object>> homeworks = jdbcTemplate.queryForList(sql);

                    for (Map<String, Object> homework : homeworks) {
                        sql = String.format("INSERT INTO student_homework(student_id,homework_id) VALUES(%s, %s)",
                                currentUserId, homework.get("id").toString());
                        jdbcTemplate.update(sql);
                    }
                } catch (Exception ignored) {}

                session.setAttribute("currentCourse", courseName);
                session.setAttribute("currentCourseId", courseIdToEnter);

                log.logAction(session.getAttribute("currentUserId").toString(), session.getAttribute("currentCourseId").toString(),
                        "加入课程：" + courseName);

                return "redirect:course-center";

            case "searchJoin":
                if (courseName.equals("")) {
                    redirectAttributes.addFlashAttribute("message", "请输入课程名");
                    return "redirect:course-center";
                }
                try {
                    sql = String.format("SELECT * FROM course_info WHERE course_name = \"%s\"", courseName);
                    jdbcTemplate.queryForMap(sql);
                } catch (DataAccessException e) {
                    redirectAttributes.addFlashAttribute("message", "课程不存在");
                    return "redirect:course-center";
                }

                try {
                    sql = String.format("SELECT id FROM course_info WHERE course_name = \"%s\"", courseName);
                    courseId = jdbcTemplate.queryForObject(sql, Integer.class);

                    sql = String.format("INSERT INTO student_course(student_id,course_id) VALUES(%s,%d)", currentUserId, courseId);
                    jdbcTemplate.update(sql);

                    sql = String.format("UPDATE user_info SET course_joined = course_joined + 1 WHERE username = \"%s\"", currentUser);
                    jdbcTemplate.update(sql);

                    sql = String.format("UPDATE course_info SET num_students = num_students + 1 WHERE course_name = \"%s\"", courseName);
                    jdbcTemplate.update(sql);

                    try {
                        sql = String.format("SELECT * FROM attendance_info WHERE course_id = %s", courseId);
                        List<Map<String, Object>> attendances = jdbcTemplate.queryForList(sql);

                        for (Map<String, Object> attendance : attendances) {
                            sql = String.format("INSERT INTO student_attendance(student_id,attendance_id) VALUES(%s, %s)",
                                    currentUserId, attendance.get("id").toString());
                            jdbcTemplate.update(sql);
                        }

                        sql = String.format("SELECT * FROM homework_info WHERE course_id = %s", courseId);
                        List<Map<String, Object>> homeworks = jdbcTemplate.queryForList(sql);

                        for (Map<String, Object> homework : homeworks) {
                            sql = String.format("INSERT INTO student_homework(student_id,homework_id) VALUES(%s, %s)",
                                    currentUserId, homework.get("id").toString());
                            jdbcTemplate.update(sql);
                        }
                    } catch (Exception ignored) {}

                    session.setAttribute("currentCourse", courseName);
                    session.setAttribute("currentCourseId", courseId);

                    redirectAttributes.addFlashAttribute("message", "加入成功");
                    log.logAction(session.getAttribute("currentUserId").toString(), session.getAttribute("currentCourseId").toString(),
                            "加入课程：" + courseName);
                } catch (Exception e) {
                    redirectAttributes.addFlashAttribute("message", "您已加入该课程");
                }

                return "redirect:course-center";

            case "enter":
                session.setAttribute("currentCourse", courseName);
                session.setAttribute("currentCourseId", courseIdToEnter);
                return "redirect:course";

            default:
                return "redirect:course-center";
        }
    }

    // 来自course页面的请求
    @GetMapping("courseUpdate")
    public String courseUpdate(@RequestParam("courseName") String courseName,
                               @RequestParam("maxNumStu") String maxNumStu,
                               @RequestParam("action") String action,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) throws IOException {
        String currentUserId = session.getAttribute("currentUserId").toString();
        String currentCourseId = session.getAttribute("currentCourseId").toString();
        String sql;

        switch (action) {
            case "save":
                if (courseName.equals("")) {
                    redirectAttributes.addFlashAttribute("message", "课程名不能为空");
                    return "redirect:course";
                }
                if (Integer.parseInt(maxNumStu) < 0) {
                    redirectAttributes.addFlashAttribute("message", "人数上限不能为负数");
                    return "redirect:course";
                }
                sql = String.format("SELECT * FROM course_info WHERE id != %s AND course_name = \"%s\"", currentCourseId, courseName);
                try {
                    if (!jdbcTemplate.queryForMap(sql).isEmpty()) {
                        redirectAttributes.addFlashAttribute("message", "课程名已被占用");
                        return "redirect:course";
                    }
                } catch (Exception ignored) {}
                sql = String.format("UPDATE course_info SET course_name = \"%s\", max_num_students = %s WHERE id = %s", courseName, maxNumStu, currentCourseId);
                jdbcTemplate.update(sql);
                session.setAttribute("currentCourse", courseName);

                log.logAction(session.getAttribute("currentUserId").toString(), session.getAttribute("currentCourseId").toString(),
                        "修改课程信息：" + courseName);
                return "redirect:course";

            case "delete":
                sql = String.format("UPDATE user_info SET course_created = course_created - 1 WHERE id = %s", currentUserId);
                jdbcTemplate.update(sql);

                sql = String.format("SELECT student_id FROM student_course WHERE (course_id = %s and is_creator = 0)", currentCourseId);
                List<Map<String, Object>> studentList = jdbcTemplate.queryForList(sql);
                for (Map<String, Object> student : studentList) {
                    sql = String.format("UPDATE user_info SET course_joined = course_joined - 1 WHERE id = %d", (int) student.get("student_id"));
                    jdbcTemplate.update(sql);
                }

                // 删除该课程下所有文件
                sql = String.format("SELECT file_name FROM file_info WHERE course_id = %s", currentCourseId);
                List<Map<String, Object>> fileNames = jdbcTemplate.queryForList(sql);

                for (Map<String, Object> fileName : fileNames) {
                    storageService.delete(fileName.get("file_name").toString());
                }

                sql = String.format("DELETE FROM course_info WHERE id = %s", currentCourseId);
                jdbcTemplate.update(sql);

                log.logAction(session.getAttribute("currentUserId").toString(), session.getAttribute("currentCourseId").toString(),
                        "删除课程：" + courseName);

                session.removeAttribute("currentCourse");
                session.removeAttribute("currentCourseId");
                return "redirect:index";

            case "quit":
                sql = String.format("DELETE FROM student_course WHERE student_id = %s and course_id = %s", currentUserId, currentCourseId);
                jdbcTemplate.update(sql);
                // update value course_joined in user_info
                sql = String.format("UPDATE user_info SET course_joined = course_joined - 1 WHERE id = %s", currentUserId);
                jdbcTemplate.update(sql);
                // update value num_students in course_info
                sql = String.format("UPDATE course_info SET num_students = num_students - 1 WHERE id = %s", currentCourseId);
                jdbcTemplate.update(sql);

                log.logAction(session.getAttribute("currentUserId").toString(), session.getAttribute("currentCourseId").toString(),
                        "退出课程：" + courseName);
                session.removeAttribute("currentCourse");
                session.removeAttribute("currentCourseId");
                return "redirect:index";

            default:
                return "redirect:course";
        }
    }

    @GetMapping("removeStudent")
    public String removeStudent(@RequestParam("studentName") String studentName,
                                HttpSession session) {
        String currentCourseId = session.getAttribute("currentCourseId").toString();
        String sql = String.format("SELECT id FROM user_info WHERE username = \"%s\"", studentName);
        int studentId = (int)jdbcTemplate.queryForObject(sql, Integer.class);

        sql = String.format("DELETE FROM student_course WHERE student_id = %d and course_id = %s", studentId, currentCourseId);
        jdbcTemplate.update(sql);

        sql = String.format("UPDATE user_info SET course_joined = course_joined - 1 WHERE id = %d", studentId);
        jdbcTemplate.update(sql);

        sql = String.format("UPDATE course_info SET num_students = num_students - 1 WHERE id = %s", currentCourseId);
        jdbcTemplate.update(sql);

        log.logAction(session.getAttribute("currentUserId").toString(), session.getAttribute("currentCourseId").toString(),
                "从课程 " + session.getAttribute("currentUserId").toString() + " 中移除学生：" + studentName);
        return "redirect:course";
    }

    @GetMapping("courseStudentsExport")
    public ResponseEntity<Resource> courseStudentsExport(HttpSession session) {

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {

            String sql = String.format("SELECT student_id,grade FROM student_course WHERE course_id = %s AND is_creator = 0", session.getAttribute("currentCourseId").toString());
            List<Map<String, Object>> students = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> student : students) {
                sql = String.format("SELECT username FROM user_info WHERE id = %d", (int) student.get("student_id"));
                String name = jdbcTemplate.queryForObject(sql, String.class);
                student.put("name", name);
            }

            Sheet sheet = workbook.createSheet("Sheet");
            String[] header = {"学生姓名", "成绩"};

            Row headerRow = sheet.createRow(0);

            for (int col = 0; col < header.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(header[col]);
            }

            int rowIdx = 1;
            for (Map<String, Object> student : students) {
                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue((String)student.get("name"));
                row.createCell(1).setCellValue((String)student.get("grade"));
            }

            workbook.write(out);
            InputStreamResource file = new InputStreamResource(new ByteArrayInputStream(out.toByteArray()));
            String fileName = "课程学生信息-" + (String)session.getAttribute("currentCourse") + ".xlsx";

            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + URLEncoder.encode(fileName, "UTF-8"))
                    .contentType(MediaType.parseMediaType("application/vnd.ms-excel")).body(file);

        } catch (IOException e) {
            throw new RuntimeException("Failed to export data to Excel file: " + e.getMessage());
        }
    }
}
