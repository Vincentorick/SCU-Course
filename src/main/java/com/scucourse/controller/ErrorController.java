package com.scucourse.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpSession;

@Controller
public class ErrorController {
    @GetMapping({"blank", "blank.html"})
    public String blank(Model model, HttpSession session) {
        String currentUser = (String)session.getAttribute("currentUser");
        model.addAttribute("currentUser", currentUser);

        return "blank";
    }
}
