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

	@GetMapping({"/files","/files.html"})
	public String listUploadedFiles(Model model, HttpSession session) throws IOException {
		model.addAttribute("username", session.getAttribute("currentUser"));
		String currentCourse = (String)session.getAttribute("currentCourse");
		model.addAttribute("currentCourse", currentCourse);


		String sql = "SELECT file_name,size,creator,date_created FROM file_info";
		List<Map<String, Object>> files = jdbcTemplate.queryForList(sql);

		List fileUrls = storageService.loadAll().map(path -> MvcUriComponentsBuilder.fromMethodName(FileController.class,
				"serveFile", path.getFileName().toString()).build().toUri().toString()).collect(Collectors.toList());

		// 修改文件大小格式，并添加url
		for (int i = 0; i < files.size(); ++i) {
			files.get(i).replace("size", Formatter.formetFileSize((long)files.get(i).get("size")));
			files.get(i).put("url", fileUrls.get(i));
		}
		model.addAttribute("files", files);

		return "files";
	}

	@GetMapping("/files/{filename:.+}")
	@ResponseBody
	public ResponseEntity<Resource> serveFile(@PathVariable String filename) {

		Resource file = storageService.loadAsResource(filename);
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
				"attachment; filename=\"" + file.getFilename() + "\"").body(file);
	}

	@PostMapping("/fileUpload")
	public String handleFileUpload(@RequestParam("file") MultipartFile file, HttpSession session,
			RedirectAttributes redirectAttributes) {
		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String sql = String.format("INSERT INTO file_info(name,size,creator,date_created) VALUES(\"%s\",\"%s\",\"%s\",\"%s\")",
				file.getOriginalFilename(), file.getSize(), session.getAttribute("currentUser"), sdf.format(date));
		jdbcTemplate.update(sql);

		storageService.store(file);
		redirectAttributes.addFlashAttribute("message", file.getOriginalFilename() + " 上传成功!");

		return "redirect:/files";
	}

	@ExceptionHandler(StorageFileNotFoundException.class)
	public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
		return ResponseEntity.notFound().build();
	}
}
