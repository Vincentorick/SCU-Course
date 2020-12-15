package com.scucourse.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpSession;

@Controller
public class IndexController {
    @GetMapping({"/index","/index.html"})
    public String index(Model model, HttpSession session) {
        model.addAttribute("username", session.getAttribute("loginUser"));
        return "index";
    }
}
