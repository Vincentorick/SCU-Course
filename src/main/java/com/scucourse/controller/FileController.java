package com.scucourse.controller;

import com.scucourse.storage.StorageFileNotFoundException;
import com.scucourse.storage.StorageService;
import com.scucourse.util.Formatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
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

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	public FileController(StorageService storageService) {
		this.storageService = storageService;
	}

	@GetMapping({"files","files.html"})
	public String listUploadedFiles(Model model, HttpSession session) throws IOException {
		try {
			String currentUserType = (String) session.getAttribute("currentUserType");
			long currentCourseId = (long)session.getAttribute("currentCourseId");

			model.addAttribute("currentUser", session.getAttribute("currentUser"));
			String currentCourse = (String) session.getAttribute("currentCourse");
			model.addAttribute("currentCourse", currentCourse);
			model.addAttribute("currentCourseId", currentCourseId);

			String sql = String.format("SELECT * FROM file_info WHERE course_id = %d and source = \"course_file\"", currentCourseId);
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

			sql = String.format("SELECT creator FROM course_info WHERE id = %d", currentCourseId);
			String creator = jdbcTemplate.queryForObject(sql, String.class);

			if (currentUserType.equals("admin") || creator.equals(session.getAttribute("currentUser")))
				model.addAttribute("memberType", "admin");
			else
				model.addAttribute("memberType", "normal");
		}
		catch (NullPointerException e) {
			return "redirect:blank";
		}
		return "files";
	}

	@GetMapping("files/{filename:.+}")
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
								   RedirectAttributes redirectAttributes) {

		if (file.isEmpty()){
			redirectAttributes.addFlashAttribute("message", "请选择文件！");
			return "redirect:files";
		}

		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String sql = String.format("INSERT INTO file_info(course_id,file_name,source,size,creator,date_created) VALUES(%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\")",
				(long) session.getAttribute("currentCourseId"), file.getOriginalFilename(), source, file.getSize(), session.getAttribute("currentUser"), sdf.format(date));
		jdbcTemplate.update(sql);

		storageService.store(file);
		redirectAttributes.addFlashAttribute("message", file.getOriginalFilename() + " 上传成功!");

		return "redirect:files";
	}

	@GetMapping("fileDelete")
	public String fileDelete(@RequestParam("fileId") String fileId,
							 @RequestParam("fileName") String fileName) throws IOException {
		storageService.delete(fileName);
		String sql = String.format("DELETE FROM file_info WHERE id = %s", fileId);
		jdbcTemplate.update(sql);
		return "redirect:files";
	}

	@ExceptionHandler(StorageFileNotFoundException.class)
	public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
		return ResponseEntity.notFound().build();
	}
}
