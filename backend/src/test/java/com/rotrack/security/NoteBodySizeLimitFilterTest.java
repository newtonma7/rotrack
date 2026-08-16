package com.rotrack.security;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class NoteBodySizeLimitFilterTest {
    private static final UUID USER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String NOTE_ID = "22222222-2222-4222-8222-222222222222";
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsDeclaredOversizedPostBeforeReadingOrCallingDownstream() throws Exception {
        NoteBodySizeLimitFilter filter = new NoteBodySizeLimitFilter(objectMapper);
        BodyRequest request = request("POST", "/api/v1/notes", "private-body");
        request.setDeclaredContentLength(NoteBodySizeLimitFilter.MAX_WIRE_BYTES + 1);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean downstreamCalled = new AtomicBoolean();

        filter.doFilter(request, response, (wrappedRequest, wrappedResponse) -> downstreamCalled.set(true));

        assertThat(downstreamCalled).isFalse();
        assertTooLarge(response, "/api/v1/notes");
    }

    @Test
    void rejectsUnknownLengthChunkedStyleStreamAfterBoundedRead() throws Exception {
        NoteBodySizeLimitFilter filter = new NoteBodySizeLimitFilter(objectMapper);
        byte[] body = new byte[(int) NoteBodySizeLimitFilter.MAX_WIRE_BYTES + 1];
        java.util.Arrays.fill(body, (byte) 'x');
        BodyRequest request = request("PUT", "/api/v1/notes/" + NOTE_ID, body);
        request.setUnknownContentLength();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean downstreamCalled = new AtomicBoolean();

        filter.doFilter(request, response, (wrappedRequest, wrappedResponse) -> downstreamCalled.set(true));

        assertThat(downstreamCalled).isFalse();
        assertTooLarge(response, "/api/v1/notes/{id}");
    }

    @Test
    void doesNotAffectNoteReadsOrDeletes() throws Exception {
        NoteBodySizeLimitFilter filter = new NoteBodySizeLimitFilter(objectMapper);
        for (String method : List.of("GET", "DELETE")) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean downstreamCalled = new AtomicBoolean();
            filter.doFilter(
                    request(method, "/api/v1/notes/" + NOTE_ID,
                            new byte[(int) NoteBodySizeLimitFilter.MAX_WIRE_BYTES + 1]),
                    response,
                    (wrappedRequest, wrappedResponse) -> downstreamCalled.set(true)
            );
            assertThat(downstreamCalled).as(method).isTrue();
            assertThat(response.getStatus()).as(method).isEqualTo(200);
        }
    }

    private BodyRequest request(String method, String path, String body) {
        return request(method, path, body.getBytes(StandardCharsets.UTF_8));
    }

    private BodyRequest request(String method, String path, byte[] body) {
        BodyRequest request = new BodyRequest(method, path, body);
        request.setServletPath(path);
        authenticate();
        return request;
    }

    private static final class BodyRequest extends MockHttpServletRequest {
        private long declaredContentLength;
        private boolean unknownContentLength;

        private BodyRequest(String method, String path, byte[] body) {
            super(method, path);
            this.declaredContentLength = body.length;
            setContent(body);
        }

        void setDeclaredContentLength(long length) {
            declaredContentLength = length;
        }

        void setUnknownContentLength() {
            unknownContentLength = true;
        }

        @Override
        public int getContentLength() {
            return unknownContentLength ? -1 : (int) Math.min(declaredContentLength, Integer.MAX_VALUE);
        }

        @Override
        public long getContentLengthLong() {
            return unknownContentLength ? -1 : declaredContentLength;
        }
    }

    private void authenticate() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "ES256")
                .subject(USER.toString())
                .claim("aud", "authenticated")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private void assertTooLarge(MockHttpServletResponse response, String path) throws IOException {
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode error = objectMapper.readTree(response.getContentAsString());
        assertThat(error.at("/error/code").asText()).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat(error.at("/error/message").asText()).isEqualTo("Request body is too large");
        assertThat(error.at("/error/fieldErrors").isObject()).isTrue();
        assertThat(error.at("/path").asText()).isEqualTo(path);
        assertThat(response.getContentAsString()).doesNotContain("private-body", "Bearer", USER.toString());
    }
}
