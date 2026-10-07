package com.dadcoach.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** D-005 sign-in: links single use and short-lived, sessions revocable, no enumeration, rate limits, CSRF. */
class AuthFlowTest extends AbstractWebIntegrationTest {

    @Test
    @DisplayName("the ops API is closed without its key, and with a wrong one")
    void opsNeedsItsKey() throws Exception {
        String body = "{\"phone\":\"+19990000001\"}";
        mvc.perform(post("/api/ops/login-links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/ops/login-links").header("X-API-Key", "wrong-key-wrong-key-wrong-key")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a link signs in once: HttpOnly SameSite cookie, /api/me answers; the same link again is refused")
    void linkIsSingleUse() throws Exception {
        long father = newFather("יואב");
        String token = issueToken(phoneOf(father));
        MvcResult first = mvc.perform(post("/api/auth/consume-link").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("FATHER"))
                .andExpect(jsonPath("$.name").value("יואב"))
                .andExpect(jsonPath("$.capabilities[0]").value("father"))
                .andReturn();
        String setCookie = first.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("DADCOACH_SESSION=").contains("HttpOnly").contains("SameSite=Lax");
        Cookie session = first.getResponse().getCookie("DADCOACH_SESSION");
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.fatherId").value(father));

        mvc.perform(post("/api/auth/consume-link").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_LOGIN_LINK"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_link WHERE father_id = ? AND used_at IS NOT NULL", Long.class, father))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_link WHERE token_hash = ?", Long.class, token))
                .as("only the hash is stored").isZero();
    }

    @Test
    @DisplayName("an expired link is refused")
    void expiredLink() throws Exception {
        long father = newFather("דן");
        String token = issueToken(phoneOf(father));
        jdbc.update("UPDATE login_link SET expires_at = now() - interval '1 minute' WHERE father_id = ?", father);
        mvc.perform(post("/api/auth/consume-link").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_LOGIN_LINK"));
    }

    @Test
    @DisplayName("'send me a link' answers the same for a father, an unknown number and garbage; only the father gets a link")
    void noEnumeration() throws Exception {
        long father = newFather("רון");
        String known = request(phoneOf(father)).getResponse().getContentAsString();
        String unknown = request("+19998887777").getResponse().getContentAsString();
        String garbage = request("hello").getResponse().getContentAsString();
        assertThat(known).isEqualTo(unknown).isEqualTo(garbage).contains("SENT_IF_REGISTERED").contains("+19995550100");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_link WHERE father_id = ?", Long.class, father)).isEqualTo(1);
        // No WhatsApp channel endpoint in the test database: recorded as a failed delivery, visible to the admin.
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM login_link WHERE father_id = ?", String.class, father))
                .isEqualTo("FAILED");
    }

    @Test
    @DisplayName("at most 3 links per person per 15 minutes - silently")
    void rateLimited() throws Exception {
        long father = newFather("עמית");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/request-link").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phone\":\"" + phoneOf(father) + "\"}")).andExpect(status().isOk());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_link WHERE father_id = ?", Long.class, father)).isEqualTo(3);
    }

    @Test
    @DisplayName("an Israeli local number reaches the father stored in E.164")
    void localNumberForm() throws Exception {
        String phone = "+97255" + String.format("%07d", (int) (Math.random() * 9_999_999));
        jdbc.update("INSERT INTO father (phone, display_name, status) VALUES (?, 'מקומי', 'ACTIVE')", phone);
        request("0" + phone.substring(4, 6) + "-" + phone.substring(6));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_link l JOIN father f ON f.id = l.father_id WHERE f.phone = ?",
                Long.class, phone)).isEqualTo(1);
    }

    @Test
    @DisplayName("logout ends this browser's session; logout everywhere ends all of them")
    void logoutAndLogoutEverywhere() throws Exception {
        long father = newFather("אורי");
        Browser a = signInFather(father);
        Browser b = signInFather(father);
        Browser c = signInFather(father);
        mvc.perform(a.on(post("/api/auth/logout"))).andExpect(status().isNoContent());
        mvc.perform(a.on(get("/api/me"))).andExpect(status().isUnauthorized());
        mvc.perform(b.on(get("/api/me"))).andExpect(status().isOk());

        mvc.perform(b.on(post("/api/auth/logout-all"))).andExpect(status().isNoContent());
        mvc.perform(b.on(get("/api/me"))).andExpect(status().isUnauthorized());
        mvc.perform(c.on(get("/api/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a write without the CSRF echo is refused; with it, it goes through")
    void csrfDoubleSubmit() throws Exception {
        long father = newFather("גיל");
        Browser browser = signInFather(father);
        mvc.perform(browser.withoutCsrf(post("/api/father/children")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"נועה\",\"age\":7}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
        mvc.perform(browser.on(post("/api/father/children")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"נועה\",\"age\":7}")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("no session: 401 everywhere in the dashboard API")
    void unauthenticated() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/father/home")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(new Cookie("DADCOACH_SESSION", "forged"))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a deactivated father gets no link and his open session stops working")
    void deactivatedFather() throws Exception {
        long father = newFather("שי");
        Browser browser = signInFather(father);
        jdbc.update("UPDATE father SET status = 'PAUSED' WHERE id = ?", father);
        mvc.perform(browser.on(get("/api/me"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ops/login-links").header("X-API-Key", OPS_KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phoneOf(father) + "\"}")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a team member signs in as ADMIN; one who is also a father has both areas")
    void staffSignIn() throws Exception {
        String staffPhone = newStaff("אורן");
        mvc.perform(signIn(staffPhone).on(get("/api/me")))
                .andExpect(jsonPath("$.kind").value("ADMIN")).andExpect(jsonPath("$.capabilities[0]").value("admin"));

        long father = newFather("אבא וגם צוות");
        mvc.perform(post("/api/ops/bootstrap-admin").header("X-API-Key", OPS_KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phoneOf(father) + "\",\"name\":\"צוות\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/ops/bootstrap-admin").header("X-API-Key", OPS_KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phoneOf(father) + "\",\"name\":\"צוות\"}")).andExpect(status().isOk());
        mvc.perform(signInFather(father).on(get("/api/me")))
                .andExpect(jsonPath("$.kind").value("ADMIN"))
                .andExpect(jsonPath("$.capabilities").value(org.hamcrest.Matchers.containsInAnyOrder("father", "admin")));
    }

    private MvcResult request(String phone) throws Exception {
        return mvc.perform(post("/api/auth/request-link").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\"}")).andExpect(status().isOk()).andReturn();
    }
}
