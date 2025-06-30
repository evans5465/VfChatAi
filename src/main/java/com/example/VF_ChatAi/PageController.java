package com.example.VF_ChatAi;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "redirect:/landingpage.html";
    }

    @GetMapping("/chat")
    public String chat() {
        return "redirect:/chat.html";
    }

    @GetMapping("/admin")
    public String admin() {
        return "redirect:/adminpanel.html";
    }

    @GetMapping("/analytics")
    public String analytics() {
        return "redirect:/analytics.html";
    }
}