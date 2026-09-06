package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.messenger.Replier;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.service.HolidayService;
import com.github.anicmv.telegrambot.service.OneYanService;
import com.github.anicmv.telegrambot.utils.BotUtil;
import java.time.format.DateTimeFormatter;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * /holiday：显示今天日期、下一个法定节假日倒计时和一言。
 */
@Log4j2
@Component
@BotCommand(value = BotConstant.CMD_HOLIDAY, description = "查看今天日期、下个法定节假日倒计时和一言")
public class HolidayCommandHandler implements BotCommandHandler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    private final Messenger messenger;
    private final HolidayService holidayService;
    private final OneYanService oneYanService;

    public HolidayCommandHandler(Messenger messenger, HolidayService holidayService, OneYanService oneYanService) {
        this.messenger = messenger;
        this.holidayService = holidayService;
        this.oneYanService = oneYanService;
    }

    @Override
    public void execute(BotContext context) {
        HolidayService.HolidayResult result;
        try {
            result = holidayService.query();
        } catch (RuntimeException e) {
            log.warn("查询节假日失败", e);
            result = null;
        }

        String oneYan = oneYanService.fetch();
        StringBuilder message = new StringBuilder("<blockquote>")
                .append(BotUtil.escapeHtml(oneYan))
                .append("</blockquote>\n\n");
        if (result == null) {
            message.append("今天：日期获取失败\n");
        } else {
            message.append("今天：").append(result.today().format(DATE_FORMATTER)).append('\n');
            if (result.nextHoliday() == null) {
                message.append("下一个法定节假日：暂时无法获取\n");
            } else {
                message.append("下一个法定节假日：")
                        .append(result.nextHoliday().name()).append('（')
                        .append(result.nextHoliday().date().format(DATE_FORMATTER)).append("）\n")
                        .append("还有 ").append(result.daysUntil()).append(" 天\n");
            }
        }
        Replier.of(context, messenger).html(message.toString());
    }
}
