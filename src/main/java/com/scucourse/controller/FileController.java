//package com.scucourse.controller;
//
//import org.springframework.stereotype.Controller;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.File;
//import java.io.IOException;
//
//@Controller
//public class FileController {
//    @PostMapping("/upload")
//    public String upload(@RequestParam("fileName") MultipartFile file) {
//        if(file.isEmpty()){
//            return "false";
//        }
//        String fileName = file.getOriginalFilename();
//        int size = (int) file.getSize();
//        System.out.println(fileName + "-->" + size);
//
//        String path = "F:/test" ;
//        File dest = new File(path + "/" + fileName);
//        if(!dest.getParentFile().exists()){ //判断文件父目录是否存在
//            dest.getParentFile().mkdir();
//        }
//        try {
//            file.transferTo(dest); //保存文件
//            return "true";
//        } catch (IllegalStateException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//            return "false";
//        } catch (IOException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//            return "false";
//        }
//    }
//
////    @PostMapping
////    public String
//}
