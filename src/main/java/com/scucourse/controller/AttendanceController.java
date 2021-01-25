package com.scucourse.controller;

import com.scucourse.util.Log;
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
public class AttendanceController {

    Log log = new Log();

    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping({"attendance", "attendance.html"})
    public String attendance(Model model, HttpSession session) {
        try {
            String currentUserType = (String) session.getAttribute("currentUserType");
            String currentCourse = (String) session.getAttribute("currentCourse");
            String currentCourseId = session.getAttribute("currentCourseId").toString(); // 可能exception，未设置该属性

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

                sql = String.format("SELECT * FROM attendance_info WHERE course_id = %s", currentCourseId);
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
                        "WHERE student_id = %s", session.getAttribute("currentUserId").toString());
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
        catch (Exception e) {
            System.out.println(e);
            return "redirect:blank";
        }
        return "attendance";
    }

    @GetMapping("attendanceCreate")
    public String attendanceCreate(@RequestParam("title") String title,
                                   @RequestParam("startTime") String startTime,
                                   @RequestParam("endTime") String endTime,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        if (title.equals("")) {
            redirectAttributes.addFlashAttribute("message", "请输入标题");
            return "redirect:attendance";
        }
        if (startTime.equals("") || endTime.equals("")) {
            redirectAttributes.addFlashAttribute("message", "请选择时间");
            return "redirect:attendance";
        }
        if (startTime.compareTo(endTime) > 0) {
            redirectAttributes.addFlashAttribute("message", "结束时间不可早于开始时间");
            return "redirect:attendance";
        }

        String currentUser = (String) session.getAttribute("currentUser");
        String currentCourseId = session.getAttribute("currentCourseId").toString();

        String sql = String.format("SELECT num_students FROM course_info WHERE id = %s", currentCourseId);
        int num_total = (int)jdbcTemplate.queryForObject(sql, Integer.class);
        sql = String.format("INSERT INTO attendance_info(course_id,creator,title,start_time,end_time,num_total) VALUES(%s,\"%s\",\"%s\",\"%s\",\"%s\",%d)",
                currentCourseId, currentUser, title, startTime.replace('T',' '), endTime.replace('T',' '), num_total);
        jdbcTemplate.update(sql);

        sql = String.format("SELECT student_id FROM student_course WHERE course_id = %s AND is_creator = 0", currentCourseId);
        List<Map<String, Object>> students = jdbcTemplate.queryForList(sql);

        sql = String.format("SELECT MAX(id) FROM attendance_info WHERE course_id = %s", currentCourseId);
        int currentAttendanceId = jdbcTemplate.queryForObject(sql, Integer.class);

        for (Map<String, Object> student : students) {
            sql = String.format("INSERT INTO student_attendance(student_id,attendance_id) VALUES(%d, %d)",
                    (int)student.get("student_id"), currentAttendanceId);
            jdbcTemplate.update(sql);
        }

        log.logAction(session.getAttribute("currentUserId").toString(), session.getAttribute("currentCourseId").toString(),
                "在课程 " + session.getAttribute("currentCourse").toString() + " 中发布签到：" + title);
        return "redirect:attendance";
    }

    @GetMapping("attendanceDelete")
    public String attendanceDelete(@RequestParam("attendanceId") String attendanceId,
                                   HttpSession session) {
        String sql = String.format("SELECT title FROM attendance_info WHERE id = %s", attendanceId);
        String title = jdbcTemplate.queryForObject(sql, String.class);
        log.logAction(session.getAttribute("currentUserId").toString(), session.getAttribute("currentCourseId").toString(),
                "在课程 " + session.getAttribute("currentCourse").toString() + " 中删除签到：" + title);

        sql = String.format("DELETE FROM attendance_info WHERE id = %s", attendanceId);
        jdbcTemplate.update(sql);

        sql = String.format("DELETE FROM student_attendance WHERE attendance_id = %s", attendanceId);
        jdbcTemplate.update(sql);
        return "redirect:attendance";
    }

    @GetMapping("studentAttend")
    public String studentAttend(@RequestParam("attendanceId") String attendanceId,
                                HttpSession session) {
        String currentUserId = session.getAttribute("currentUserId").toString();

        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String sql = String.format("UPDATE student_attendance SET attended = 1, time = \"%s\" WHERE student_id = %s and attendance_id = %s", sdf.format(date), currentUserId, attendanceId);
        jdbcTemplate.update(sql);

        sql = String.format("UPDATE attendance_info SET num_attended = num_attended + 1 WHERE id = %s", attendanceId);
        jdbcTemplate.update(sql);

        sql = String.format("SELECT title FROM attendance_info WHERE id = %s", attendanceId);
        String title = jdbcTemplate.queryForObject(sql, String.class);
        log.logAction(session.getAttribute("currentUserId").toString(), session.getAttribute("currentCourseId").toString(),
                "在课程 " + session.getAttribute("currentCourse").toString() + " 中完成签到：" + title);
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

        String sql = String.format("SELECT creator FROM course_info WHERE id = %s", session.getAttribute("currentCourseId").toString());
        String creator = jdbcTemplate.queryForObject(sql, String.class);

        if (currentUserType.equals("admin") || creator.equals(session.getAttribute("currentUser")))
            model.addAttribute("memberType", "admin");
        else
            model.addAttribute("memberType", "normal");

        sql = String.format("SELECT * FROM attendance_info WHERE id = %s", attendanceId);
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

    @GetMapping("attendanceExport")
    public ResponseEntity<Resource> attendanceExport(HttpSession session) {
        String currentCourseId = session.getAttribute("currentCourseId").toString();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {

            String sql = String.format("SELECT * FROM attendance_info WHERE course_id = %s", currentCourseId);
            List<Map<String, Object>> attendanceList = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> attendance : attendanceList) {
                attendance.replace("start_time", attendance.get("start_time").toString().substring(0, 16));
                attendance.replace("end_time", attendance.get("end_time").toString().substring(0, 16));
            }

            Sheet sheet = workbook.createSheet("Sheet");
            String[] header = {"标题", "开始时间", "结束时间", "学生人数", "已签到人数"};

            Row headerRow = sheet.createRow(0);

            for (int col = 0; col < header.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(header[col]);
            }

            int rowIdx = 1;
            for (Map<String, Object> attendance : attendanceList) {
                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue((String)attendance.get("title"));
                row.createCell(1).setCellValue((String)attendance.get("start_time"));
                row.createCell(2).setCellValue((String)attendance.get("end_time"));
                row.createCell(3).setCellValue((int)attendance.get("num_total"));
                row.createCell(4).setCellValue((int)attendance.get("num_attended"));
            }

            workbook.write(out);
            InputStreamResource file = new InputStreamResource(new ByteArrayInputStream(out.toByteArray()));
            String fileName = "课程签到记录-" + (String)session.getAttribute("currentCourse") + ".xlsx";

            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + URLEncoder.encode(fileName, "UTF-8"))
                    .contentType(MediaType.parseMediaType("application/vnd.ms-excel")).body(file);

        } catch (IOException e) {
            throw new RuntimeException("Failed to export data to Excel file: " + e.getMessage());
        }
    }

    @GetMapping("attendanceDetailExport")
    public ResponseEntity<Resource> attendanceDetailExport(@RequestParam("attendanceId") String attendanceId,
                                                           HttpSession session) {

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {

            String sql = String.format("SELECT * FROM attendance_info WHERE id = %s", attendanceId);
            Map<String, Object> attendanceDetail = jdbcTemplate.queryForMap(sql);

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

            Sheet sheet = workbook.createSheet("Sheet");
            String[] header = {"学生姓名", "签到状态", "签到时间"};

            Row headerRow = sheet.createRow(0);

            for (int col = 0; col < header.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(header[col]);
            }

            int rowIdx = 1;
            for (Map<String, Object> student : studentList) {
                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue((String)student.get("username"));
                row.createCell(1).setCellValue((String)student.get("attended"));
                row.createCell(2).setCellValue((String)student.get("time"));
            }

            workbook.write(out);
            InputStreamResource file = new InputStreamResource(new ByteArrayInputStream(out.toByteArray()));
            String fileName = "签到详情-" + (String)session.getAttribute("currentCourse") + ".xlsx";

            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + URLEncoder.encode(fileName, "UTF-8"))
                    .contentType(MediaType.parseMediaType("application/vnd.ms-excel")).body(file);

        } catch (IOException e) {
            throw new RuntimeException("Failed to export data to Excel file: " + e.getMessage());
        }
    }
}