package com.rotrack.controller;

import com.rotrack.dto.ApiResponse;
import com.rotrack.dto.FriendRequest;
import com.rotrack.dto.FriendshipDTO.BlockPage;
import com.rotrack.dto.FriendshipDTO.Relationship;
import com.rotrack.dto.FriendshipDTO.RelationshipPage;
import com.rotrack.dto.FriendshipDTO.SearchPage;
import com.rotrack.exception.ValidationException;
import com.rotrack.service.FriendshipService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FriendshipController {
    private final FriendshipService service;
    public FriendshipController(FriendshipService service) { this.service = service; }

    @GetMapping("/users/search")
    public ApiResponse<SearchPage> search(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String q) {
        return ApiResponse.success(service.search(viewer(jwt), q));
    }

    @GetMapping("/friends")
    public ApiResponse<RelationshipPage> friends(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String cursor) {
        return ApiResponse.success(service.friends(viewer(jwt), cursor));
    }

    @GetMapping("/friend-requests")
    public ApiResponse<RelationshipPage> requests(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String direction, @RequestParam(required = false) String cursor) {
        return ApiResponse.success(service.requests(viewer(jwt), direction, cursor));
    }

    @PostMapping("/friend-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Relationship> request(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) FriendRequest body) {
        if (body == null || body.hasUnknownFields()) {
            throw new ValidationException("A username-only request body is required", Map.of("body", "A username-only request body is required"));
        }
        return ApiResponse.success(service.request(viewer(jwt), body.getUsername()));
    }

    @PostMapping("/friend-requests/{id}/accept")
    public ApiResponse<Relationship> accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ApiResponse.success(service.accept(viewer(jwt), id));
    }

    @DeleteMapping("/friend-requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRequest(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { service.deleteRequest(viewer(jwt), id); }

    @DeleteMapping("/friends/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { service.removeFriend(viewer(jwt), id); }

    @GetMapping("/blocks")
    public ApiResponse<BlockPage> blocks(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String cursor) {
        return ApiResponse.success(service.blocks(viewer(jwt), cursor));
    }

    @PutMapping("/blocks/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@AuthenticationPrincipal Jwt jwt, @PathVariable String username) { service.block(viewer(jwt), username); }

    @DeleteMapping("/blocks/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@AuthenticationPrincipal Jwt jwt, @PathVariable String username) { service.unblock(viewer(jwt), username); }

    private UUID viewer(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
