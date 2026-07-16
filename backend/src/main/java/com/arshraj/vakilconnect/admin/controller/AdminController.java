package com.arshraj.vakilconnect.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

    @GetMapping("/api/admin/dashboard")
    public String dashboard() {
        return "Welcome Admin!";
    }
}