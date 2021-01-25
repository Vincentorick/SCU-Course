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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

@Controller
public class BulletinController {
    @Autowired
    JdbcTemplate jdbcTemplate;

    Log log = new Log();

    @GetMapping({"bulletin", "bulletin.html"})
    public String bulletin(Model model, HttpSession session) {
        try {
            String currentCourse = (String) session.getAttribute("currentCourse");
            String currentCourseId = session.getAttribute("currentCourseId").toString();
            String currentUserType = (String) session.getAttribute("currentUserType");

            model.addAttribute("currentUser", session.getAttribute("currentUser"));
            model.addAttribute("currentUserType", session.getAttribute("currentUserType"));
            model.addAttribute("currentCourse", currentCourse);

            String sql = String.format("SELECT * FROM bulletin_info WHERE course_id = %s", currentCourseId);
            List<Map<String, Object>> bulletins = jdbcTemplate.queryForList(sql);
            Collections.reverse(bulletins);

            for (Map<String, Object> bulletin : bulletins)
                bulletin.replace("date_created", bulletin.get("date_created").toString().substring(0, 16));

            model.addAttribute("bulletins", bulletins);

            sql = String.format("SELECT creator FROM course_info WHERE id = %s", currentCourseId);
            String creator = jdbcTemplate.queryForObject(sql, String.class);
            if (currentUserType.equals("admin") || creator.equals(session.getAttribute("currentUser")))
                model.addAttribute("memberType", "admin");
            else
                model.addAttribute("memberType", "normal");
        }
        catch (Exception e) {
            System.out.println(e);
            return "redirect:blank";
        }
        return "bulletin";
    }

    @GetMapping("bulletinCreate")
    public String bulletinCreate(@RequestParam("title") String title,
                                 @RequestParam("content") String content,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        if (title.equals("")) {
            redirectAttributes.addFlashAttribute("message", "请输入标题");
            return "redirect:bulletin";
        }

        String currentUser = (String) session.getAttribute("currentUser");
        String currentCourseId = session.getAttribute("currentCourseId").toString();

        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String sql = String.format("INSERT INTO bulletin_info(course_id,title,creator,date_created,content) VALUES(%s, \"%s\", \"%s\", \"%s\", \"%s\")",
                currentCourseId, title, currentUser, sdf.format(date), content);
        jdbcTemplate.update(sql);

        log.logAction(session.getAttribute("currentUserId").toString(), session.getAttribute("currentCourseId").toString(),
                "发布公告：" + title);

        return "redirect:bulletin";
    }

    @GetMapping("bulletinDelete")
    public String bulletinDelete(@RequestParam("bulletinId") String bulletinId,
                                 HttpSession session) {
        String sql = String.format("SELECT title FROM bulletin_info WHERE id = %s", bulletinId);
        String title = jdbcTemplate.queryForObject(sql, String.class);
        log.logAction(session.getAttribute("currentUserId").toString(), session.getAttribute("currentCourseId").toString(),
                "删除公告：" + title);

        sql = String.format("DELETE FROM bulletin_info WHERE id = %s", bulletinId);
        jdbcTemplate.update(sql);

        return "redirect:bulletin";
    }

    @GetMapping("bulletinExport")
    public ResponseEntity<Resource> courseStudentsExport(HttpSession session) {

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {

            String sql = String.format("SELECT * FROM bulletin_info WHERE course_id = %s", session.getAttribute("currentCourseId").toString());
            List<Map<String, Object>> bulletins = jdbcTemplate.queryForList(sql);
            Collections.reverse(bulletins);

            for (Map<String, Object> bulletin : bulletins)
                bulletin.replace("date_created", bulletin.get("date_created").toString().substring(0, 16));

            Sheet sheet = workbook.createSheet("Sheet");
            String[] header = {"标题", "发布者", "发布时间", "内容"};

            Row headerRow = sheet.createRow(0);

            for (int col = 0; col < header.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(header[col]);
            }

            int rowIdx = 1;
            for (Map<String, Object> bulletin : bulletins) {
                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue((String)bulletin.get("title"));
                row.createCell(1).setCellValue((String)bulletin.get("creator"));
                row.createCell(2).setCellValue((String)bulletin.get("date_created"));
                row.createCell(3).setCellValue((String)bulletin.get("content"));
            }

            workbook.write(out);
            InputStreamResource file = new InputStreamResource(new ByteArrayInputStream(out.toByteArray()));
            String fileName = "课程公告信息-" + (String)session.getAttribute("currentCourse") + ".xlsx";

            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + URLEncoder.encode(fileName, "UTF-8"))
                    .contentType(MediaType.parseMediaType("application/vnd.ms-excel")).body(file);

        } catch (IOException e) {
            throw new RuntimeException("Failed to export data to Excel file: " + e.getMessage());
        }
    }
}
