package com.pluchon.meng.companion;

import static org.junit.jupiter.api.Assertions.*;

import com.pluchon.meng.companion.converter.CompanionSources;
import com.pluchon.meng.companion.service.CompanionLimits;
import com.pluchon.meng.companion.service.ReplyService;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CompanionSearchTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void searchSettlementRequiresAReservationAndCountsOnlyActualSuccess() {
        var reserved = new CompanionLimits.Reservation(1,"2026-10-30","r",List.of("message","search"),true);
        var result = json.readTree("""
            {"toolUsage":[{"name":"web_search","success":true,"searchCalls":1}]}
            """);
        assertEquals(List.of("message","search"), ReplyService.usedKinds(result,reserved,true));
        assertEquals(List.of(), ReplyService.usedKinds(result,reserved,false));
        for (String flag : List.of("\"cached\":true", "\"success\":false")) {
            var free = json.readTree("{\"toolUsage\":[{\"name\":\"web_search\",\"searchCalls\":1,"+flag+"}]}");
            assertEquals(List.of("message"), ReplyService.usedKinds(free,reserved,true));
        }
        var noSearch = new CompanionLimits.Reservation(1,"2026-10-30","r",List.of("message"),true);
        assertThrows(IllegalArgumentException.class, () -> ReplyService.usedKinds(result,noSearch,true));
    }

    @Test
    void legacyPostsAndWebSourcesUseTheSameValidationForStorageAndResponses() {
        var sources = CompanionSources.read(json.readTree("""
            [{"postId":1,"title":"旧帖子"},{"type":"WEB","title":"公告",
              "url":"https://example.com/news","site":"官网","privateMemory":"不得输出"}]
            """));
        assertEquals("POST", sources.get(0).type());
        assertEquals(1L, sources.get(0).postId());
        assertNull(sources.get(1).postId());
        assertFalse(json.writeValueAsString(sources).contains("privateMemory"));
        for (String url : List.of("http://example.com", "javascript:alert(1)", "https://user@example.com")) {
            assertThrows(IllegalArgumentException.class, () -> CompanionSources.read(json.readTree(
                "[{\"type\":\"WEB\",\"title\":\"标题\",\"site\":\"来源\",\"url\":\""+url+"\"}]")));
        }
    }
}
