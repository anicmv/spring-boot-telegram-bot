package com.github.anicmv.telegrambot.handler.inline.provider.impl;

import com.github.anicmv.telegrambot.annotation.BotInline;
import com.github.anicmv.telegrambot.handler.inline.provider.InlineQueryResultProvider;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.model.BotContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;

import java.io.IOException;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 编程语言排行榜 inline 结果提供器。
 */
@BotInline(BotConstant.INLINE_ID_TOP_PROGRAMMING)
@Component
public class TopProgrammingLanguagesInlineQueryResultProvider implements InlineQueryResultProvider {

    @Override
    public boolean supports(BotContext context) {
        String query = context == null ? null : context.text();
        if (query == null || query.isBlank()) {
            return false;
        }
        String normalized = query.toLowerCase();
        return normalized.contains("top")
                || normalized.contains("programming")
                || normalized.contains("tiobe")
                || query.contains("编程")
                || query.contains("语言");
    }

    @Override
    public InlineQueryResult createResult(BotContext context) {
        String table = fetchTopLanguages();
        InputTextMessageContent content = InputTextMessageContent.builder()
                .messageText("<pre>" + table + "</pre>")
                .parseMode("HTML")
                .build();
        return InlineQueryResultArticle.builder()
                .id(sortId())
                .title("编程语言排行榜")
                .thumbnailUrl("https://jpg.moe/i/6oalto65.jpeg")
                .inputMessageContent(content)
                .build();
    }

    private String fetchTopLanguages() {
        try {
            Document doc = Jsoup.connect(BotConstant.API_TIOBE).get();
            Element table = doc.getElementById("top20");
            if (table == null) {
                return "未找到排行榜数据。";
            }
            Elements rows = table.select("tr");
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-22s %-10s %-8s%n", "Language", "Ratings", "Change"));
            sb.append("-".repeat(44)).append("\n");
            for (int i = 1; i < rows.size(); i++) {
                Elements columns = rows.get(i).select("td");
                if (columns.size() >= 7) {
                    sb.append(String.format(
                            "%-22s %-10s %-8s%n",
                            columns.get(4).text(),
                            columns.get(5).text(),
                            columns.get(6).text()
                    ));
                }
            }
            return sb.toString();
        } catch (IOException e) {
            return "抓取排行榜失败。";
        }
    }
}
