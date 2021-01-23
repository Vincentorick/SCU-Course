package com.scucourse.controller;

import com.scucourse.storage.StorageService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
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
            String currentCourse = (String) session.getAttribute("currentCourse");
            String currentCourseId = (String) session.getAttribute("currentCourseId"); // 可能exception，未设置该属性

            model.addAttribute("currentUser", session.getAttribute("currentUser"));
            model.addAttribute("currentUserType", currentUserType);
            model.addAttribute("currentCourse", currentCourse);

            String sql = String.format("SELECT creator FROM course_info WHERE id = %s", currentCourseId);
            String creator = jdbcTemplate.queryForObject(sql, String.class);

            if (currentUserType.equals("admin") || creator.equals(session.getAttribute("currentUser"))) {
                // 教师页面
                sql = String.format("SELECT student_id FROM student_course WHERE course_id = %s AND is_creator = 0", currentCourseId);
                List<Map<String, Object>> students = jdbcTemplate.queryForList(sql);

                for (Map<String, Object> student : students) {
                    sql = String.format("SELECT username FROM user_info WHERE id = %d", (int) student.get("student_id"));
                    String name = jdbcTemplate.queryForObject(sql, String.class);
                    student.put("name", name);
                }
                model.addAttribute("students", students);

                sql = String.format("SELECT * FROM homework_info WHERE course_id = %s", currentCourseId);
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
                        "WHERE student_id = %s", session.getAttribute("currentUserId").toString());
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
                        homework.put("fileId", fileInfo.get("id"));
                        homework.put("fileName", fileInfo.get("file_name"));
                        homework.put("fileUrl", "files/" + fileInfo.get("file_name"));
                    }
                }
                Collections.reverse(homeworkList_stu);
                model.addAttribute("homeworkList_stu", homeworkList_stu);
                model.addAttribute("memberType", "normal");
            }
        }
        catch (Exception e) {
            return "redirect:blank";
        }
        return "homework";
    }

    @GetMapping("homeworkCreate")
    public String homeworkCreate(@RequestParam("title") String title,
                                 @RequestParam("deadline") String deadline,
                                 @RequestParam("description") String description,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        if (title.equals("")) {
            redirectAttributes.addFlashAttribute("message", "请输入标题");
            return "redirect:homework";
        }
        if (deadline.equals("")) {
            redirectAttributes.addFlashAttribute("message", "请选择时间");
            return "redirect:homework";
        }

        String currentUser = (String) session.getAttribute("currentUser");
        String currentCourseId = (String) session.getAttribute("currentCourseId");

        String sql = String.format("SELECT num_students FROM course_info WHERE id = %s", currentCourseId);
        int num_total = (int)jdbcTemplate.queryForObject(sql, Integer.class);
        sql = String.format("INSERT INTO homework_info(course_id,creator,title,description,deadline,num_total) VALUES(%s,\"%s\",\"%s\",\"%s\",\"%s\",%d)",
                currentCourseId, currentUser, title, description, deadline.replace('T',' '), num_total);
        jdbcTemplate.update(sql);

        sql = String.format("SELECT student_id FROM student_course WHERE course_id = %s AND is_creator = 0", currentCourseId);
        List<Map<String, Object>> students = jdbcTemplate.queryForList(sql);

        sql = String.format("SELECT MAX(id) FROM homework_info WHERE course_id = %s", currentCourseId);
        int currentHomeworkId = jdbcTemplate.queryForObject(sql, Integer.class);

        for (Map<String, Object> student : students) {
            sql = String.format("INSERT INTO student_homework(student_id,homework_id) VALUES(%d, %d)",
                    (int)student.get("student_id"), currentHomeworkId);
            jdbcTemplate.update(sql);
        }
        return "redirect:homework";
    }

    @GetMapping("homeworkDelete")
    public String homeworkDelete(@RequestParam("homeworkId") String homeworkId) throws IOException {

        String sql = String.format("SELECT file_id FROM student_homework WHERE homework_id = %s", homeworkId);
        List<Map<String, Object>> fileIds = jdbcTemplate.queryForList(sql);

        for (Map<String, Object> fileId : fileIds) {
            sql = String.format("SELECT file_name FROM file_info WHERE id = %d", (int)fileId.get("file_id"));
            String fileName = jdbcTemplate.queryForObject(sql, String.class);

            sql = String.format("DELETE FROM file_info WHERE id = %s", (int)fileId.get("file_id"));
            jdbcTemplate.update(sql);
            storageService.delete(fileName);
        }

        sql = String.format("DELETE FROM homework_info WHERE id = %s", homeworkId);
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

        sql = String.format("UPDATE student_homework SET submitted = 0, time = 0, file_id = 0 WHERE student_id = %s and homework_id = %s",
                session.getAttribute("currentUserId").toString(), homeworkId);
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
        String sql = String.format("INSERT INTO file_info(course_id,file_name,source,size,creator,date_created) VALUES(%s,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\")",
                (String) session.getAttribute("currentCourseId"), file.getOriginalFilename(), "student_homework", file.getSize(), session.getAttribute("currentUser"), sdf.format(date));
        jdbcTemplate.update(sql);

        sql = String.format("SELECT MAX(id) FROM file_info WHERE course_id = %s", (String) session.getAttribute("currentCourseId"));
        int fileId = (int)jdbcTemplate.queryForObject(sql, Integer.class);

        sql = String.format("UPDATE student_homework SET submitted = 1, time = \"%s\", file_id = %d WHERE student_id = %s and homework_id = %s",
                sdf.format(date), fileId, session.getAttribute("currentUserId").toString(), homeworkId);
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

    @GetMapping("homeworkExport")
    public ResponseEntity<Resource> homeworkExport(HttpSession session) {
        String currentCourseId = (String) session.getAttribute("currentCourseId");

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {

            String sql = String.format("SELECT student_id FROM student_course WHERE course_id = %s AND is_creator = 0", currentCourseId);
            List<Map<String, Object>> students = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> student : students) {
                sql = String.format("SELECT username FROM user_info WHERE id = %d", (int) student.get("student_id"));
                String name = jdbcTemplate.queryForObject(sql, String.class);
                student.put("name", name);
            }

            sql = String.format("SELECT * FROM homework_info WHERE course_id = %s", currentCourseId);
            List<Map<String, Object>> homeworkList = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> homework : homeworkList) {
                homework.replace("deadline", homework.get("deadline").toString().substring(0, 16));
            }

            Sheet sheet = workbook.createSheet("Sheet");
            String[] header = {"标题", "截止时间", "学生人数", "已提交人数"};

            Row headerRow = sheet.createRow(0);

            for (int col = 0; col < header.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(header[col]);
            }

            int rowIdx = 1;
            for (Map<String, Object> homework : homeworkList) {
                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue((String)homework.get("title"));
                row.createCell(1).setCellValue((String)homework.get("deadline"));
                row.createCell(2).setCellValue((int)homework.get("num_total"));
                row.createCell(3).setCellValue((int)homework.get("num_submitted"));
            }

            workbook.write(out);
            InputStreamResource file = new InputStreamResource(new ByteArrayInputStream(out.toByteArray()));
            String fileName = "课程作业记录-" + (String)session.getAttribute("currentCourse") + ".xlsx";

            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + URLEncoder.encode(fileName, "UTF-8"))
                    .contentType(MediaType.parseMediaType("application/vnd.ms-excel")).body(file);

        } catch (IOException e) {
            throw new RuntimeException("Failed to export data to Excel file: " + e.getMessage());
        }
    }

    @GetMapping("homeworkDetailExport")
    public ResponseEntity<Resource> homeworkDetailExport(@RequestParam("homeworkId") String homeworkId,
                                                         HttpSession session) {

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {

            String sql = String.format("SELECT * FROM homework_info WHERE id = %s", homeworkId);
            Map<String, Object> homeworkDetail = jdbcTemplate.queryForMap(sql);

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

            Sheet sheet = workbook.createSheet("Sheet");
            String[] header = {"学生姓名", "提交状态", "提交时间"};

            Row headerRow = sheet.createRow(0);

            for (int col = 0; col < header.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(header[col]);
            }

            int rowIdx = 1;
            for (Map<String, Object> student : studentList) {
                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue((String)student.get("username"));
                row.createCell(1).setCellValue((String)student.get("submit_status"));
                row.createCell(2).setCellValue((String)student.get("time"));
            }

            workbook.write(out);
            InputStreamResource file = new InputStreamResource(new ByteArrayInputStream(out.toByteArray()));
            String fileName = "作业提交详情-" + (String)session.getAttribute("currentCourse") + ".xlsx";

            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + URLEncoder.encode(fileName, "UTF-8"))
                    .contentType(MediaType.parseMediaType("application/vnd.ms-excel")).body(file);

        } catch (IOException e) {
            throw new RuntimeException("Failed to export data to Excel file: " + e.getMessage());
        }
    }
}
