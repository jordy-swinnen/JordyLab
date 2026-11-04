package dev.jordy.jordylab.identity.rest.controller;

import dev.jordy.jordylab.identity.CurrentUserResolver;
import dev.jordy.jordylab.identity.domain.MachineToken;
import dev.jordy.jordylab.identity.rest.controller.model.CreateMachineRequest;
import dev.jordy.jordylab.identity.rest.controller.model.CreateMachineResponse;
import dev.jordy.jordylab.identity.rest.controller.model.MachineView;
import dev.jordy.jordylab.identity.rest.controller.model.MachinesResponse;
import dev.jordy.jordylab.identity.service.MachineTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Machine management for the scanner (spec 019 US5): add a machine (the token is shown once), list the machines with
 * their last-used time, and revoke. Admin-only by the security matrix; tokens never appear in the list.
 */
@RestController
@RequestMapping("/api/identity/machines")
@RequiredArgsConstructor
public class MachineTokenController {

    private final MachineTokenService machineTokenService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public MachinesResponse list() {
        return new MachinesResponse(machineTokenService.list().stream()
                .map(MachineView::from)
                .toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateMachineResponse create(@RequestBody CreateMachineRequest request) {
        MachineTokenService.CreatedMachineToken created = machineTokenService.create(
                UUID.fromString(currentUserResolver.requireCurrentUserSubject()), request.name());

        return new CreateMachineResponse(created.token().getId(), created.token().getName(), created.plaintext());
    }

    @PostMapping("/{id}/revoke")
    public void revoke(@PathVariable UUID id) {
        machineTokenService.revoke(id);
    }
}
