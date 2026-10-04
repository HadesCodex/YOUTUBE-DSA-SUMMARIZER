package com.dsasummariser.controller;

import com.dsasummariser.model.SummariseRequest;
import com.dsasummariser.model.SummariseResponse;
import com.dsasummariser.service.SummariseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SummariseController {

    private final SummariseService summariseService;

    public SummariseController(SummariseService summariseService) {
        this.summariseService = summariseService;
    }

    @PostMapping("/summarise")
    public SummariseResponse summarise(@Valid @RequestBody SummariseRequest request) {
        return summariseService.summarise(request);
    }
}
