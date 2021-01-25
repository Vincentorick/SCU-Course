package com.scucourse.controller;

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

import javax.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

@Controller
public class LogController {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping({"log-user", "log-user.html"})
    public String loadUserLog(HttpSession session, Model model) {
        try {
            String currentUserId = session.getAttribute("currentUserId").toString();
            String currentCourseId = session.getAttribute("currentCourseId").toString();
            String currentUser = (String) session.getAttribute("currentUser");
            String currentUserType = (String) session.getAttribute("currentUserType");
            String currentCourse = (String) session.getAttribute("currentCourse");

            model.addAttribute("currentCourse", currentCourse);
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("currentUserType", currentUserType);

            String sql = String.format("SELECT creator FROM course_info WHERE id = %s", session.getAttribute("currentCourseId").toString());
            String creator = jdbcTemplate.queryForObject(sql, String.class);

            if (currentUserType.equals("admin") || creator.equals(session.getAttribute("currentUser")))
                model.addAttribute("memberType", "admin");
            else
                model.addAttribute("memberType", "normal");

            sql = String.format("SELECT * FROM log WHERE user_id = %s AND course_id = %s", currentUserId, currentCourseId);
            List<Map<String, Object>> logs = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> log : logs) {
                log.replace("time", log.get("time").toString().substring(0, 16));
            }
            model.addAttribute("logs", logs);

            return "log-user";
        } catch (Exception e) {
            System.out.println(e);
            return "redirect:blank";
        }
    }

    @GetMapping({"log-course", "log-course.html"})
    public String loadCourseLog(HttpSession session, Model model) {
        try {
            String currentCourseId = session.getAttribute("currentCourseId").toString();
            String currentUser = (String) session.getAttribute("currentUser");
            String currentUserType = (String) session.getAttribute("currentUserType");
            String currentCourse = (String) session.getAttribute("currentCourse");

            model.addAttribute("currentCourse", currentCourse);
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("currentUserType", currentUserType);


            String sql = String.format("SELECT creator FROM course_info WHERE id = %s", session.getAttribute("currentCourseId").toString());
            String creator = jdbcTemplate.queryForObject(sql, String.class);

            if (currentUserType.equals("admin") || creator.equals(session.getAttribute("currentUser"))) {
                model.addAttribute("memberType", "admin");
            }
            else
                model.addAttribute("memberType", "normal");

            sql = String.format("SELECT * FROM log,user_info,course_info WHERE course_id = %s AND user_id = user_info.id AND course_id = course_info.id",
                    currentCourseId);
            List<Map<String, Object>> logs = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> log : logs) {
                log.replace("time", log.get("time").toString().substring(0, 16));
            }
            model.addAttribute("logs", logs);

            return "log-course";
        } catch (Exception e) {
            System.out.println(e);
            return "redirect:blank";
        }
    }

    @GetMapping({"log-system", "log-system.html"})
    public String loadSystemLog(HttpSession session, Model model) {
        try {
            String currentUser = (String) session.getAttribute("currentUser");
            String currentUserType = (String) session.getAttribute("currentUserType");
            String currentCourse = (String) session.getAttribute("currentCourse");

            model.addAttribute("currentCourse", currentCourse);
            model.addAttribute("currentUser", currentUser);
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

            String sql = "SELECT * FROM log,user_info,course_info WHERE user_id = user_info.id AND course_id = course_info.id";
            List<Map<String, Object>> logs = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> log : logs) {
                log.replace("time", log.get("time").toString().substring(0, 16));
            }
            model.addAttribute("logs", logs);

            return "log-system";
        } catch (Exception e) {
            System.out.println(e);
            return "redirect:blank";
        }
    }

    @GetMapping("userLogExport")
    public ResponseEntity<Resource> userLogExport(HttpSession session) {

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {

            String sql = String.format("SELECT * FROM log WHERE user_id = %s AND course_id = %s",
                    session.getAttribute("currentUserId").toString(), session.getAttribute("currentCourseId"));
            List<Map<String, Object>> logs = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> log : logs) {
                log.replace("time", log.get("time").toString().substring(0, 16));
            }

            Sheet sheet = workbook.createSheet("Sheet");
            String[] header = {"时间", "内容"};

            Row headerRow = sheet.createRow(0);

            for (int col = 0; col < header.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(header[col]);
            }

            int rowIdx = 1;
            for (Map<String, Object> log : logs) {
                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue((String)log.get("time"));
                row.createCell(1).setCellValue((String)log.get("content"));

            }

            workbook.write(out);
            InputStreamResource file = new InputStreamResource(new ByteArrayInputStream(out.toByteArray()));
            String fileName = "用户日志-" + (String)session.getAttribute("currentCourse") + ".xlsx";

            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + URLEncoder.encode(fileName, "UTF-8"))
                    .contentType(MediaType.parseMediaType("application/vnd.ms-excel")).body(file);

        } catch (IOException e) {
            throw new RuntimeException("Failed to export data to Excel file: " + e.getMessage());
        }
    }

    @GetMapping("courseLogExport")
    public ResponseEntity<Resource> courseLogExport(HttpSession session) {

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {

            String sql = String.format("SELECT * FROM log,user_info,course_info WHERE course_id = %s AND user_id = user_info.id AND course_id = course_info.id",
                    session.getAttribute("currentCourseId"));
            List<Map<String, Object>> logs = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> log : logs) {
                log.replace("time", log.get("time").toString().substring(0, 16));
            }

            Sheet sheet = workbook.createSheet("Sheet");
            String[] header = {"时间", "用户ID", "用户名", "内容"};

            Row headerRow = sheet.createRow(0);

            for (int col = 0; col < header.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(header[col]);
            }

            int rowIdx = 1;
            for (Map<String, Object> log : logs) {
                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue((String)log.get("time"));
                row.createCell(1).setCellValue((int)log.get("user_id"));
                row.createCell(2).setCellValue((String)log.get("username"));
                row.createCell(3).setCellValue((String)log.get("content"));
            }

            workbook.write(out);
            InputStreamResource file = new InputStreamResource(new ByteArrayInputStream(out.toByteArray()));
            String fileName = "课程日志-" + (String)session.getAttribute("currentCourse") + ".xlsx";

            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + URLEncoder.encode(fileName, "UTF-8"))
                    .contentType(MediaType.parseMediaType("application/vnd.ms-excel")).body(file);

        } catch (IOException e) {
            throw new RuntimeException("Failed to export data to Excel file: " + e.getMessage());
        }
    }

    @GetMapping("systemLogExport")
    public ResponseEntity<Resource> systemLogExport(HttpSession session) {

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {

            String sql = "SELECT * FROM log, user_info, course_info WHERE user_id = user_info.id AND course_id = course_info.id";
            List<Map<String, Object>> logs = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> log : logs) {
                log.replace("time", log.get("time").toString().substring(0, 16));
            }

            Sheet sheet = workbook.createSheet("Sheet");
            String[] header = {"时间", "用户ID", "用户名", "课程ID", "课程名", "内容"};

            Row headerRow = sheet.createRow(0);

            for (int col = 0; col < header.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(header[col]);
            }

            int rowIdx = 1;
            for (Map<String, Object> log : logs) {
                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue((String)log.get("time"));
                row.createCell(1).setCellValue((int)log.get("user_id"));
                row.createCell(2).setCellValue((String)log.get("username"));
                row.createCell(3).setCellValue((int)log.get("course_id"));
                row.createCell(4).setCellValue((String)log.get("course_name"));
                row.createCell(5).setCellValue((String)log.get("content"));

            }

            workbook.write(out);
            InputStreamResource file = new InputStreamResource(new ByteArrayInputStream(out.toByteArray()));
            String fileName = "系统日志-" + (String)session.getAttribute("currentCourse") + ".xlsx";

            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + URLEncoder.encode(fileName, "UTF-8"))
                    .contentType(MediaType.parseMediaType("application/vnd.ms-excel")).body(file);

        } catch (IOException e) {
            throw new RuntimeException("Failed to export data to Excel file: " + e.getMessage());
        }
    }
}
