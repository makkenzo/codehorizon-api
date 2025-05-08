package com.makkenzo.codehorizon.controllers

import com.makkenzo.codehorizon.dtos.GlobalSearchResponseDTO
import com.makkenzo.codehorizon.services.SearchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/api")
@Tag(name = "Search", description = "Глобальный поиск")
class SearchController(private val searchService: SearchService) {
    @GetMapping("/search")
    @Operation(summary = "Выполнить глобальный поиск")
    fun globalSearch(@RequestParam(required = true) q: String): ResponseEntity<GlobalSearchResponseDTO> {
        val results = searchService.globalSearch(q)
        return ResponseEntity.ok(results)
    }
}