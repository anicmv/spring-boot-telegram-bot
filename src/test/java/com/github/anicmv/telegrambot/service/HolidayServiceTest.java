package com.github.anicmv.telegrambot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anicmv.telegrambot.config.HolidayProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HolidayServiceTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-06T00:00:00Z"), SHANGHAI);

    @Test
    void shouldChooseNearestFutureOffDay() {
        HolidayService service = new HolidayService(new ObjectMapper(), properties(), CLOCK,
                year -> """
                        {
                          "2026-09-20":{"date":"2026-09-20","name":"国庆节","isOffDay":true},
                          "2026-10-01":{"date":"2026-10-01","name":"国庆节","isOffDay":true}
                        }
                        """);

        HolidayService.HolidayResult result = service.query();

        assertEquals("2026-09-06", result.today().toString());
        assertEquals("2026-09-20", result.nextHoliday().date().toString());
        assertEquals("国庆节", result.nextHoliday().name());
        assertEquals(14, result.daysUntil());
    }

    @Test
    void shouldIgnoreWorkingDaysAndInvalidEntries() {
        HolidayService service = newService();
        String response = """
                {
                  "2026-01-01":{"date":"2026-01-01","name":"元旦","isOffDay":true},
                  "2026-05-01":{"date":"2026-05-01","name":"劳动节","isOffDay":false},
                  "bad":{"date":"not-a-date","name":"错误","isOffDay":true},
                  "2026-10-01":{"date":"2026-10-01","name":"国庆节","isOffDay":true}
                }
                """;

        List<HolidayService.HolidayDate> dates = service.parse(response);

        assertEquals(2, dates.size());
        assertEquals("2026-10-01", dates.getLast().date().toString());
    }

    @Test
    void malformedResponseShouldReturnEmptyList() {
        assertEquals(List.of(), newService().parse("not-json"));
        assertEquals(List.of(), newService().parse(null));
    }

    @Test
    void noFutureHolidayShouldReturnNullNextHoliday() {
        HolidayService service = new HolidayService(new ObjectMapper(), properties(), CLOCK,
                year -> "{\"2026-01-01\":{\"date\":\"2026-01-01\",\"name\":\"元旦\",\"isOffDay\":true}}");

        assertNull(service.query().nextHoliday());
    }

    private HolidayService newService() {
        return new HolidayService(new ObjectMapper(), properties(), CLOCK);
    }

    private HolidayProperties properties() {
        HolidayProperties properties = new HolidayProperties();
        properties.setHolidayApi("https://example.test/holidays/{year}");
        return properties;
    }
}
