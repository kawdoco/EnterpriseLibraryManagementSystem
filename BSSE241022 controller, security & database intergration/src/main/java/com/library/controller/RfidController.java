package com.library.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Browser page for the RFID counter. All data arrives via the /api/rfid endpoints. */
@Controller
public class RfidController {

    @GetMapping("/rfid/counter")
    public String counter() {
        return "rfid/counter";
    }
}
