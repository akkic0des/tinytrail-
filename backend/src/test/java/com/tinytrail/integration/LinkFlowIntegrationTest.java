package com.tinytrail.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinytrail.repository.LinkRepository;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Full stack against real MySQL + Redis (Testcontainers). Needs Docker running. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LinkFlowIntegrationTest {

    @Container @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Container @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean LinkRepository linkRepository;

    @BeforeEach
    void clean() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll(); // resets rate limits + cache
        jdbc.update("delete from click_events");
        jdbc.update("delete from links");
        clearInvocations(linkRepository);
    }

    private ResultActions create(String body) throws Exception {
        return mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createCode(String body) throws Exception {
        JsonNode node = json.readTree(create(body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return node.get("shortCode").asText();
    }

    @Test
    void createThenRedirectReturns302WithLocation() throws Exception {
        String code = createCode("{\"url\":\"https://example.com/page?a=1\"}");

        mvc.perform(get("/" + code).header("Referer", "https://news.site"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/page?a=1"));
    }

    @Test
    void customAliasWorksAndDuplicateIs409() throws Exception {
        create("{\"url\":\"https://example.com\",\"customAlias\":\"my-link\"}").andExpect(status().isCreated());
        create("{\"url\":\"https://other.com\",\"customAlias\":\"my-link\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void aliasesDifferingOnlyByCaseAreDifferentLinks() throws Exception {
        // Guards the ascii_bin column collation: with MySQL's default collation the second call would be a 409.
        create("{\"url\":\"https://a.com\",\"customAlias\":\"AbC\"}").andExpect(status().isCreated());
        create("{\"url\":\"https://b.com\",\"customAlias\":\"abc\"}").andExpect(status().isCreated());
        mvc.perform(get("/AbC")).andExpect(header().string("Location", "https://a.com"));
        mvc.perform(get("/abc")).andExpect(header().string("Location", "https://b.com"));
    }

    @Test
    void invalidInputsAre400() throws Exception {
        create("{\"url\":\"ftp://example.com\"}").andExpect(status().isBadRequest());
        create("{\"url\":\"javascript:alert(1)\"}").andExpect(status().isBadRequest());
        create("{\"url\":\"https://example.com\",\"customAlias\":\"ab\"}").andExpect(status().isBadRequest());
        create("{\"url\":\"https://example.com/" + "a".repeat(2048) + "\"}").andExpect(status().isBadRequest());
        create("not json").andExpect(status().isBadRequest());
    }

    @Test
    void unknownCodeIs404() throws Exception {
        mvc.perform(get("/doesnotexist")).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void expiredLinkIs410() throws Exception {
        String code = createCode("{\"url\":\"https://example.com\",\"expiresInDays\":1}");
        jdbc.update("update links set expires_at = date_sub(utc_timestamp(), interval 1 hour) where short_code = ?", code);
        mvc.perform(get("/" + code)).andExpect(status().isGone());
    }

    @Test
    void inactiveLinkIs410() throws Exception {
        String code = createCode("{\"url\":\"https://example.com\"}");
        jdbc.update("update links set active = false where short_code = ?", code);
        mvc.perform(get("/" + code)).andExpect(status().isGone());
    }

    @Test
    void secondRedirectIsServedFromCache() throws Exception {
        String code = createCode("{\"url\":\"https://example.com\"}");
        clearInvocations(linkRepository);

        mvc.perform(get("/" + code)).andExpect(status().isFound());        // miss -> DB -> fills cache
        verify(linkRepository).findByShortCode(code);
        assertThat(redisTemplate.opsForValue().get("link:" + code)).isEqualTo("https://example.com");
        assertThat(redisTemplate.getExpire("link:" + code)).isBetween(86_000L, 86_400L); // ~24h TTL

        clearInvocations(linkRepository);
        mvc.perform(get("/" + code)).andExpect(status().isFound());        // hit -> no DB lookup
        verify(linkRepository, never()).findByShortCode(code);
    }

    @Test
    void eleventhCreationInAMinuteIs429WithRetryAfter() throws Exception {
        for (int i = 0; i < 10; i++) {
            create("{\"url\":\"https://example.com/" + i + "\"}").andExpect(status().isCreated());
        }
        create("{\"url\":\"https://example.com/11\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void clicksAreRecordedAsynchronouslyAndShowUpInStatsAndAnalytics() throws Exception {
        String code = createCode("{\"url\":\"https://example.com\"}");
        mvc.perform(get("/" + code).header("Referer", "https://news.site")).andExpect(status().isFound());
        mvc.perform(get("/" + code)).andExpect(status().isFound());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mvc.perform(get("/api/links/" + code + "/stats"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.clickCount").value(2)));

        mvc.perform(get("/api/links/" + code + "/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(2))
                .andExpect(jsonPath("$.clicksPerDay.length()").value(7))
                .andExpect(jsonPath("$.clicksPerDay[6].clicks").value(2))
                .andExpect(jsonPath("$.topReferrers[0].clicks").value(1));

        // Privacy: only a 64-char hash is stored, never the address MockMvc reports (127.0.0.1).
        assertThat(JdbcTestUtils.countRowsInTableWhere(jdbc, "click_events", "ip_hash like '%127.0.0.1%'")).isZero();
    }

    @Test
    void statsForUnknownCodeIs404() throws Exception {
        mvc.perform(get("/api/links/nope/stats")).andExpect(status().isNotFound());
        mvc.perform(get("/api/links/nope/analytics")).andExpect(status().isNotFound());
    }
}
