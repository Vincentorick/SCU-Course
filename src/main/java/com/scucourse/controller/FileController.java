package com.scucourse.controller;

import com.scucourse.storage.StorageFileNotFoundException;
import com.scucourse.storage.StorageService;
import com.scucourse.util.Formatter;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class FileController {

	private final StorageService storageService;

	Log log = new Log();

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	public FileController(StorageService storageService) {
		this.storageService = storageService;
	}

	@GetMapping({"file","file.html"})
	public String listUploadedFiles(Model model, HttpSession session) throws IOException {
		try {
			String currentUserType = (String) session.getAttribute("currentUserType");
			model.addAttribute("currentUser", session.getAttribute("currentUser"));
			model.addAttribute("currentUserType", currentUserType);

			String currentCourse = (String) session.getAttribute("currentCourse");
			String currentCourseId = session.getAttribute("currentCourseId").toString();
			model.addAttribute("currentCourse", currentCourse);
			model.addAttribute("currentCourseId", currentCourseId);

			String sql = String.format("SELECT * FROM file_info WHERE course_id = %s and source = \"course_file\"", currentCourseId);
			List<Map<String, Object>> files = jdbcTemplate.queryForList(sql);

			List fileUrls = storageService.loadAll().map(path -> MvcUriComponentsBuilder.fromMethodName(FileController.class,
					"serveFile", path.getFileName().toString()).build().toUri().toString()).collect(Collectors.toList());

			// 修改文件大小格式，并添加url
			int index = 0;
			for (Map<String, Object> file : files) {
				file.replace("size", Formatter.formetFileSize((long) file.get("size")));
				file.put("url", fileUrls.get(index++));
				file.replace("date_created", file.get("date_created").toString().substring(0, 16));
			}
			model.addAttribute("files", files);

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
		return "file";
	}

	@GetMapping("file/{filename:.+}")
	@ResponseBody
	public ResponseEntity<Resource> serveFile(@PathVariable String filename) throws UnsupportedEncodingException {

		Resource file = storageService.loadAsResource(filename);

		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
				"attachment; filename=\"" + URLEncoder.encode(file.getFilename(), "UTF-8") + "\"").body(file);
	}

	@PostMapping("fileUpload")
	public String handleFileUpload(@RequestParam("file") MultipartFile file,
								   @RequestParam("source") String source,
								   HttpSession session,
								   RedirectAttributes redirectAttributes) throws IOException {

		if (file.isEmpty()){
			redirectAttributes.addFlashAttribute("message", "请选择文件！");
			return "redirect:file";
		}

		storageService.store(file);
		redirectAttributes.addFlashAttribute("message", file.getOriginalFilename() + " 上传成功!");

		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String sql = String.format("INSERT INTO file_info(course_id,file_name,source,size,creator,date_created) VALUES(%s,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\")",
				session.getAttribute("currentCourseId").toString(), file.getOriginalFilename(), source, file.getSize(), session.getAttribute("currentUser"), sdf.format(date));
		jdbcTemplate.update(sql);

		log.logAction(session.getAttribute("currentUserId").toString(), session.getAttribute("currentCourseId").toString(),
				"在课程 " + session.getAttribute("currentCourse").toString() + " 中上传文件：" + file.getOriginalFilename());

		return "redirect:file";
	}

	@GetMapping("fileDelete")
	public String fileDelete(@RequestParam("fileId") String fileId,
							 @RequestParam("fileName") String fileName,
							 HttpSession session) throws IOException {
		log.logAction(session.getAttribute("currentUserId").toString(), session.getAttribute("currentCourseId").toString(),
				"在课程 " + session.getAttribute("currentCourse").toString() + " 中删除文件：" + fileName);
		storageService.delete(fileName);
		String sql = String.format("DELETE FROM file_info WHERE id = %s", fileId);
		jdbcTemplate.update(sql);
		return "redirect:file";
	}

	@ExceptionHandler(StorageFileNotFoundException.class)
	public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
		return ResponseEntity.notFound().build();
	}

	@GetMapping("filesExport")
	public ResponseEntity<Resource> fileExport(HttpSession session) {

		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {

			String sql = String.format("SELECT * FROM file_info WHERE course_id = %s and source = \"course_file\"", session.getAttribute("currentCourseId").toString());
			List<Map<String, Object>> files = jdbcTemplate.queryForList(sql);

			List fileUrls = storageService.loadAll().map(path -> MvcUriComponentsBuilder.fromMethodName(FileController.class,
					"serveFile", path.getFileName().toString()).build().toUri().toString()).collect(Collectors.toList());

			// 修改文件大小格式，并添加url
			int index = 0;
			for (Map<String, Object> file : files) {
				file.replace("size", Formatter.formetFileSize((long) file.get("size")));
				file.put("url", fileUrls.get(index++));
				file.replace("date_created", file.get("date_created").toString().substring(0, 16));
			}

			Sheet sheet = workbook.createSheet("Sheet");
			String[] header = {"文件名", "大小", "上传者", "上传时间"};

			Row headerRow = sheet.createRow(0);

			for (int col = 0; col < header.length; col++) {
				Cell cell = headerRow.createCell(col);
				cell.setCellValue(header[col]);
			}

			int rowIdx = 1;
			for (Map<String, Object> file : files) {
				Row row = sheet.createRow(rowIdx++);

				row.createCell(0).setCellValue((String)file.get("file_name"));
				row.createCell(1).setCellValue((String)file.get("size"));
				row.createCell(2).setCellValue((String)file.get("creator"));
				row.createCell(3).setCellValue((String)file.get("date_created"));
			}

			workbook.write(out);
			InputStreamResource file = new InputStreamResource(new ByteArrayInputStream(out.toByteArray()));
			String fileName = "课程文件信息-" + (String)session.getAttribute("currentCourse") + ".xlsx";

			return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
					"attachment; filename=\"" + URLEncoder.encode(fileName, "UTF-8"))
					.contentType(MediaType.parseMediaType("application/vnd.ms-excel")).body(file);

		} catch (IOException e) {
			throw new RuntimeException("Failed to export data to Excel file: " + e.getMessage());
		}
	}
}
