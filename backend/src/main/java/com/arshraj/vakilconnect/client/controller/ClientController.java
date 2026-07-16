package com.arshraj.vakilconnect.client.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientController {

    @GetMapping("/api/client/profile")
    public String profile() {
        return "Welcome Client!";
    }
}