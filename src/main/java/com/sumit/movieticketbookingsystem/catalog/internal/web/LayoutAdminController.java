package com.sumit.movieticketbookingsystem.catalog.internal.web;

import com.sumit.movieticketbookingsystem.catalog.internal.service.LayoutAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
class LayoutAdminController {

    private final LayoutAdminService layoutService;

    LayoutAdminController(LayoutAdminService layoutService) {
        this.layoutService = layoutService;
    }

    @GetMapping("/screens/{screenId}/layouts")
    List<LayoutResponse> layouts(@PathVariable long screenId) {
        return layoutService.layouts(screenId).stream().map(LayoutResponse::from).toList();
    }

    @PostMapping("/screens/{screenId}/layouts")
    @ResponseStatus(HttpStatus.CREATED)
    LayoutResponse createDraft(@PathVariable long screenId, @Valid @RequestBody LayoutRequest request) {
        return LayoutResponse.from(layoutService.createDraft(screenId, request.toPlan()));
    }

    @PutMapping("/layouts/{id}")
    LayoutResponse editDraft(@PathVariable long id, @Valid @RequestBody LayoutRequest request) {
        return LayoutResponse.from(layoutService.editDraft(id, request.toPlan()));
    }

    @PostMapping("/layouts/{id}/activate")
    LayoutResponse activate(@PathVariable long id) {
        return LayoutResponse.from(layoutService.activate(id));
    }
}
