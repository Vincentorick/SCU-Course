package com.scucourse.controller;

import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;


@Controller
public class TestController {
    @RequestMapping("/index")
    public String test(Model model) {
        model.addAttribute("msg", "<h1>hello, springboot</h1>");
        model.addAttribute("users", Arrays.asList("vincent", "vince", "illidan"));
        return "test";
    }
}
