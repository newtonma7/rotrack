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
    void namesNoteDeleteWithTheNoteRouteTemplate() {
        MockHttpServletRequest request = request("DELETE", "/api/v1/notes/22222222-2222-2222-2222-222222222222");
        assertThat(RouteTemplates.mutation(request))
                .contains(new RouteTemplates.MutationRoute("DELETE", RouteTemplates.NOTE));
    }

    @Test
    void includesHistoryCreateEditAndDeleteInStableMutationRoutes() {
        MockHttpServletRequest create = request("POST", "/api/v1/time-entries");
        MockHttpServletRequest history = request("GET", "/api/v1/time-entries/history");
        MockHttpServletRequest edit = request("PUT", "/api/v1/time-entries/22222222-2222-2222-2222-222222222222");
        MockHttpServletRequest delete = request("DELETE", "/api/v1/time-entries/22222222-2222-2222-2222-222222222222");

        assertThat(RouteTemplates.resolve(history)).isEqualTo(RouteTemplates.HISTORY);
        assertThat(RouteTemplates.mutation(create))
                .contains(new RouteTemplates.MutationRoute("POST", RouteTemplates.COLLECTION));
        assertThat(RouteTemplates.mutation(edit))
                .contains(new RouteTemplates.MutationRoute("PUT", RouteTemplates.ENTRY));
        assertThat(RouteTemplates.mutation(delete))
                .contains(new RouteTemplates.MutationRoute("DELETE", RouteTemplates.ENTRY));
    }

    @Test
    void socialMutationsShareTheBudgetWithoutLoggingIdsOrHandles() {
        for (String[] route : new String[][] {
                {"POST", "/api/v1/friend-requests", RouteTemplates.REQUESTS},
                {"POST", "/api/v1/friend-requests/not-a-uuid/accept", RouteTemplates.ACCEPT},
                {"DELETE", "/api/v1/friend-requests/not-a-uuid", RouteTemplates.REQUEST},
                {"DELETE", "/api/v1/friends/not-a-uuid", RouteTemplates.FRIEND},
                {"PUT", "/api/v1/blocks/private_handle", RouteTemplates.BLOCK},
                {"DELETE", "/api/v1/blocks/private_handle", RouteTemplates.BLOCK}
        }) {
            var request = request(route[0], route[1]);
            assertThat(RouteTemplates.resolve(request)).isEqualTo(route[2]);
            assertThat(RouteTemplates.mutation(request)).contains(new RouteTemplates.MutationRoute(route[0], route[2]));
            assertThat(RouteTemplates.isNoteMutation(request)).isFalse();
        }
        var search = request("GET", "/api/v1/users/search");
        assertThat(RouteTemplates.resolve(search)).isEqualTo(RouteTemplates.USER_SEARCH);
        assertThat(RouteTemplates.mutation(search)).isEmpty();
    }

    @Test
    void summaryRoutesNeverLogThePeerHandleBeforeOrAfterControllerMapping() {
        var summary = request("GET", "/api/v1/social/summaries/private_handle");
        assertThat(RouteTemplates.resolve(summary)).isEqualTo("/api/v1/social/summaries/{username}");
        assertThat(RouteTemplates.mutation(summary)).isEmpty();
        summary.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/v1/social/summaries/{username}");
        assertThat(RouteTemplates.resolve(summary)).isEqualTo("/api/v1/social/summaries/{username}");
    }

    @Test
    void presenceRoutesNeverLogThePeerBeforeOrAfterMapping() {
        var presence = request("GET", "/api/v1/social/presence/private_handle");
        assertThat(RouteTemplates.resolve(presence)).isEqualTo(RouteTemplates.STUDY_PRESENCE);
        assertThat(RouteTemplates.mutation(presence)).isEmpty();
        presence.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                RouteTemplates.STUDY_PRESENCE);
        assertThat(RouteTemplates.resolve(presence)).isEqualTo(RouteTemplates.STUDY_PRESENCE);
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
