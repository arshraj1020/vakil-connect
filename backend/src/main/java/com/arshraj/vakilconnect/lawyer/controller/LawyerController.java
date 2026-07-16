package com.arshraj.vakilconnect.lawyer.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LawyerController {

    @GetMapping("/api/lawyer/dashboard")
    public String dashboard() {
        return "Welcome Lawyer!";
    }
}