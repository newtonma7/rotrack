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
}
