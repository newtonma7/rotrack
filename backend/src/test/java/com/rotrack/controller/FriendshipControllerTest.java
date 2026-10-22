package com.rotrack.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rotrack.config.RateLimitConfiguration;
import com.rotrack.config.SecurityConfig;
import com.rotrack.dto.FriendshipDTO.*;
import com.rotrack.exception.ConflictException;
import com.rotrack.exception.GlobalExceptionHandler;
import com.rotrack.exception.ResourceNotFoundException;
import com.rotrack.service.FriendshipService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FriendshipController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RateLimitConfiguration.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.test/jwks",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://example.test/issuer"
})
class FriendshipControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean FriendshipService service;
    @MockitoBean JwtDecoder decoder;

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor actor(UUID id) {
        return jwt().jwt(token -> token.subject(id.toString()));
    }

    @Test
    void everyRouteRequiresAuthentication() throws Exception {
        for (String route : List.of("/friends", "/friend-requests?direction=incoming", "/blocks", "/users/search?q=alice")) {
            mvc.perform(get("/api/v1" + route)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/friend-requests").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void bodyRejectsForgedFieldsAndRequiresUsernameOnlyShape() throws Exception {
        UUID owner = UUID.randomUUID();
        for (String body : List.of("", "null", "{\"username\":\"alice\",\"user_id\":\"forged\"}", "{\"username\":\"alice\",\"duration\":9}")) {
            mvc.perform(post("/api/v1/friend-requests").with(actor(owner)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void controllerPassesOnlyJwtViewerAndReturnsMinimalProjections() throws Exception {
        UUID owner = UUID.randomUUID(); UUID id = UUID.randomUUID();
        var row = new Relationship(id, "alice");
        when(service.request(owner, "alice")).thenReturn(row);
        mvc.perform(post("/api/v1/friend-requests").with(actor(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice\"}"))
                .andExpect(status().isCreated()).andExpect(content().json("{\"data\":{\"id\":\"" + id + "\",\"username\":\"alice\"}}"))
                .andExpect(jsonPath("$.data.*", org.hamcrest.Matchers.hasSize(2)));
        when(service.friends(owner, "cursor")).thenReturn(new RelationshipPage(List.of(row), null));
        mvc.perform(get("/api/v1/friends").queryParam("cursor", "cursor").with(actor(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].username").value("alice"));
        when(service.requests(owner, "incoming", null)).thenReturn(new RelationshipPage(List.of(row), null));
        mvc.perform(get("/api/v1/friend-requests?direction=incoming").with(actor(owner))).andExpect(status().isOk());
        when(service.search(owner, "ali")).thenReturn(new SearchPage(List.of(new SearchResult("alice", RelationshipState.FRIEND))));
        mvc.perform(get("/api/v1/users/search?q=ali").with(actor(owner)))
                .andExpect(status().isOk()).andExpect(content().json("{\"data\":{\"items\":[{\"username\":\"alice\",\"relationship\":\"FRIEND\"}]}}"))
                .andExpect(jsonPath("$.data.items[0].*", org.hamcrest.Matchers.hasSize(2)));
        when(service.blocks(owner, null)).thenReturn(new BlockPage(List.of(new Block("alice")), null));
        mvc.perform(get("/api/v1/blocks").with(actor(owner))).andExpect(status().isOk());
        when(service.accept(owner, id)).thenReturn(row);
        mvc.perform(post("/api/v1/friend-requests/" + id + "/accept").with(actor(owner))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/friend-requests/" + id).with(actor(owner))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/friends/" + id).with(actor(owner))).andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/blocks/alice").with(actor(owner))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/blocks/alice").with(actor(owner))).andExpect(status().isNoContent());
        verify(service).request(owner, "alice"); verify(service).accept(owner, id);
        verify(service).deleteRequest(owner, id); verify(service).removeFriend(owner, id);
        verify(service).block(owner, "alice"); verify(service).unblock(owner, "alice");
    }

    @Test
    void missingAndForeignUseSameSafeErrorAndPathsNeverEchoHandles() throws Exception {
        UUID owner = UUID.randomUUID(); UUID id = UUID.randomUUID();
        when(service.accept(owner, id)).thenThrow(new ResourceNotFoundException("Relationship or user not found"));
        mvc.perform(post("/api/v1/friend-requests/" + id + "/accept").with(actor(owner)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/friend-requests/{id}/accept"));
        doThrow(new ResourceNotFoundException("Relationship or user not found")).when(service).block(owner, "private_handle");
        mvc.perform(put("/api/v1/blocks/private_handle").with(actor(owner)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.path").value("/api/v1/blocks/{username}"));
        when(service.request(owner, "alice")).thenThrow(new ConflictException("FRIENDSHIP_CONFLICT", "Refresh the relationship"));
        mvc.perform(post("/api/v1/friend-requests").with(actor(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("FRIENDSHIP_CONFLICT"));
    }

    @Test
    void invalidSearchAttemptsConsumeSeparateThirtyRequestBudget() throws Exception {
        UUID owner = UUID.randomUUID(); UUID other = UUID.randomUUID();
        for (int i = 0; i < 30; i++) mvc.perform(get("/api/v1/users/search").with(actor(owner))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/search?q=alice").with(actor(owner)))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
        mvc.perform(get("/api/v1/users/search?q=alice").with(actor(other))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/blocks/alice").with(actor(owner))).andExpect(status().isNoContent());
        verify(service, times(30)).search(owner, null);
        verify(service, never()).search(owner, "alice");
    }
}
