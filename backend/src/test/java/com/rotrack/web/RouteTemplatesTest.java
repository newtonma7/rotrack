package com.rotrack.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RouteTemplatesTest {

    @Test
    void namesPreferencePutWithItsStableRouteTemplate() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/preferences");
        request.setServletPath("/api/v1/preferences");

        assertThat(RouteTemplates.resolve(request)).isEqualTo(RouteTemplates.PREFERENCES);
        assertThat(RouteTemplates.mutation(request))
                .contains(new RouteTemplates.MutationRoute("PUT", RouteTemplates.PREFERENCES));
    }

    @Test
    void keepsResourceIdsOutOfTheStopRouteLabel() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PUT", "/api/v1/time-entries/22222222-2222-2222-2222-222222222222/stop");
        request.setServletPath(request.getRequestURI());

        assertThat(RouteTemplates.resolve(request)).isEqualTo(RouteTemplates.STOP);
        assertThat(RouteTemplates.mutation(request))
                .contains(new RouteTemplates.MutationRoute("PUT", RouteTemplates.STOP));
    }

    @Test
    void includesHistoryCreateEditAndDeleteInStableMutationRoutes() {
        MockHttpServletRequest create = request("POST", "/api/v1/time-entries");
        MockHttpServletRequest edit = request("PUT", "/api/v1/time-entries/22222222-2222-2222-2222-222222222222");
        MockHttpServletRequest delete = request("DELETE", "/api/v1/time-entries/22222222-2222-2222-2222-222222222222");

        assertThat(RouteTemplates.mutation(create))
                .contains(new RouteTemplates.MutationRoute("POST", RouteTemplates.HISTORY));
        assertThat(RouteTemplates.mutation(edit))
                .contains(new RouteTemplates.MutationRoute("PUT", RouteTemplates.ENTRY));
        assertThat(RouteTemplates.mutation(delete))
                .contains(new RouteTemplates.MutationRoute("DELETE", RouteTemplates.ENTRY));
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
