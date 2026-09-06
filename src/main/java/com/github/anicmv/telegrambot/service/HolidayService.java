package com.github.anicmv.telegrambot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anicmv.telegrambot.config.HolidayProperties;
import com.github.anicmv.telegrambot.utils.HttpUtil;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 查询下一个中国法定节假日并计算倒计时。
 */
@Log4j2
@Service
public class HolidayService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_HOLIDAY_NAME_LENGTH = 50;

    private final ObjectMapper objectMapper;
    private final HolidayProperties properties;
    private final Clock clock;
    private final Function<Integer, String> yearFetcher;

    @Autowired
    public HolidayService(ObjectMapper objectMapper, HolidayProperties properties) {
        this(objectMapper, properties, Clock.system(ZoneId.of(properties.getTimezone())), year -> {
            String api = properties.getHolidayApi().replace("{year}", String.valueOf(year));
            return HttpUtil.get(api);
        });
    }

    HolidayService(ObjectMapper objectMapper, HolidayProperties properties, Clock clock) {
        this(objectMapper, properties, clock, year -> {
            String api = properties.getHolidayApi().replace("{year}", String.valueOf(year));
            return HttpUtil.get(api);
        });
    }

    HolidayService(ObjectMapper objectMapper, HolidayProperties properties, Clock clock,
                   Function<Integer, String> yearFetcher) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
        this.yearFetcher = yearFetcher;
    }

    public HolidayResult query() {
        LocalDate today = LocalDate.now(clock);
        List<HolidayDate> dates = new ArrayList<>();
        loadYear(today.getYear(), dates);
        if (dates.stream().noneMatch(date -> date.date().isAfter(today))) {
            loadYear(today.getYear() + 1, dates);
        }
        return new HolidayResult(today, dates.stream()
                .filter(date -> date.date().isAfter(today))
                .min(Comparator.comparing(HolidayDate::date))
                .orElse(null));
    }

    List<HolidayDate> parse(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            if (!root.isObject()) {
                return List.of();
            }
            List<HolidayDate> result = new ArrayList<>();
            root.fields().forEachRemaining(entry -> {
                JsonNode item = entry.getValue();
                if (!item.path("isOffDay").asBoolean(false)) {
                    return;
                }
                String dateText = item.path("date").asText(entry.getKey());
                String name = item.path("name").asText("").strip();
                try {
                    LocalDate date = LocalDate.parse(dateText, DATE_FORMATTER);
                    if (name.isBlank()) {
                        name = "法定节假日";
                    }
                    if (name.length() > MAX_HOLIDAY_NAME_LENGTH) {
                        name = name.substring(0, MAX_HOLIDAY_NAME_LENGTH);
                    }
                    result.add(new HolidayDate(date, name));
                } catch (RuntimeException ignored) {
                    // 忽略接口中的非日期键或异常日期。
                }
            });
            return result.stream().distinct().sorted(Comparator.comparing(HolidayDate::date)).toList();
        } catch (Exception e) {
            log.warn("节假日 API 响应解析失败", e);
            return List.of();
        }
    }

    List<HolidayDate> loadYearData(int year) {
        return parse(yearFetcher.apply(year));
    }

    private void loadYear(int year, List<HolidayDate> dates) {
        dates.addAll(loadYearData(year));
    }

    public record HolidayResult(LocalDate today, HolidayDate nextHoliday) {
        public long daysUntil() {
            return nextHoliday == null ? -1 : ChronoUnit.DAYS.between(today, nextHoliday.date());
        }
    }

    public record HolidayDate(LocalDate date, String name) {
    }
}
