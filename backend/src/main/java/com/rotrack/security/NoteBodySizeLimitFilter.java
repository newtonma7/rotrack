package com.rotrack.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotrack.dto.ApiErrorResponse;
import com.rotrack.observability.RequestLogAttributes;
import com.rotrack.web.RouteTemplates;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bounds note mutation bytes before MVC/Jackson can deserialize private rich text.
 *
 * The fixed 1 MiB wire limit leaves room for JSON/envelope overhead around the canonical
 * 256 KiB rich-text limit. It is deliberately not configurable: changing a request boundary
 * requires reviewing the canonical document contract with it.
 */
public final class NoteBodySizeLimitFilter extends OncePerRequestFilter {
    static final long MAX_WIRE_BYTES = 1024L * 1024L;

    private final ObjectMapper objectMapper;

    public NoteBodySizeLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isAuthenticated() || !isNoteBodyMutation(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        long declaredLength = request.getContentLengthLong();
        if (declaredLength > MAX_WIRE_BYTES) {
            reject(request, response);
            return;
        }

        byte[] body = readAtMostLimit(request);
        if (body == null) {
            reject(request, response);
            return;
        }
        filterChain.doFilter(new BufferedBodyRequest(request, body), response);
    }

    private byte[] readAtMostLimit(HttpServletRequest request) throws IOException {
        try (var input = request.getInputStream()) {
            var output = new ByteArrayOutputStream(8192);
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_WIRE_BYTES) {
                    return null;
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private boolean isNoteBodyMutation(HttpServletRequest request) {
        String route = RouteTemplates.resolve(request);
        return (RouteTemplates.NOTES.equals(route) && "POST".equals(request.getMethod()))
                || (RouteTemplates.NOTE.equals(route) && "PUT".equals(request.getMethod()));
    }

    private boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken && authentication.isAuthenticated();
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setAttribute(RequestLogAttributes.ERROR_CODE, "PAYLOAD_TOO_LARGE");
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(
                "PAYLOAD_TOO_LARGE",
                "Request body is too large",
                Map.of(),
                RouteTemplates.resolve(request)
        ));
    }

    private static final class BufferedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private final ServletInputStream inputStream;

        private BufferedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
            this.inputStream = new BufferedBodyInputStream(body);
        }

        @Override
        public ServletInputStream getInputStream() {
            return inputStream;
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(
                    inputStream,
                    encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)
            ));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    private static final class BufferedBodyInputStream extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        private BufferedBodyInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return delegate.read(bytes, offset, length);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(jakarta.servlet.ReadListener readListener) {
            throw new UnsupportedOperationException("Async reads are not supported");
        }
    }
}
