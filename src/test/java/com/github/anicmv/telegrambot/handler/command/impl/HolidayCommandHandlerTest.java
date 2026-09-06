package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import com.github.anicmv.telegrambot.service.HolidayService;
import com.github.anicmv.telegrambot.service.OneYanService;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HolidayCommandHandlerTest {

    @Test
    void shouldReplyWithHolidayAndOneYan() {
        Messenger messenger = mock(Messenger.class);
        HolidayService holidayService = mock(HolidayService.class);
        OneYanService oneYanService = mock(OneYanService.class);
        HolidayService.HolidayDate holiday = new HolidayService.HolidayDate(
                LocalDate.of(2026, 10, 1), "国庆节");
        when(holidayService.query()).thenReturn(new HolidayService.HolidayResult(
                LocalDate.of(2026, 9, 6), holiday));
        when(oneYanService.fetch()).thenReturn("今天也要开心。");
        Message command = new Message();
        command.setMessageId(7);
        BotContext context = new BotContext(null, UpdateType.MESSAGE, 1L, 2L,
                "/holiday", command, null, null, null);

        new HolidayCommandHandler(messenger, holidayService, oneYanService).execute(context);

        verify(messenger).sendReplyHtmlText(eq(1L), eq(7),
                org.mockito.ArgumentMatchers.argThat(text -> text.contains("<blockquote>今天也要开心。</blockquote>")
                        && text.contains("2026年09月06日")
                        && text.contains("国庆节") && text.contains("还有 25 天")));
    }

    @Test
    void holidayFailureShouldStillReplyWithOneYan() {
        Messenger messenger = mock(Messenger.class);
        HolidayService holidayService = mock(HolidayService.class);
        OneYanService oneYanService = mock(OneYanService.class);
        when(holidayService.query()).thenThrow(new IllegalStateException("offline"));
        when(oneYanService.fetch()).thenReturn("fallback");
        BotContext context = new BotContext(null, UpdateType.MESSAGE, 1L, 2L,
                "/holiday", null, null, null, null);

        new HolidayCommandHandler(messenger, holidayService, oneYanService).execute(context);

        verify(messenger).sendHtmlText(eq(1L), org.mockito.ArgumentMatchers.argThat(
                text -> text.contains("日期获取失败") && text.contains("<blockquote>fallback</blockquote>")));
    }
}
