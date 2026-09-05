package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.entity.UserProfileEntity;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.messenger.TextSpec;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.BotUserProfile;
import com.github.anicmv.telegrambot.model.InlineButton;
import com.github.anicmv.telegrambot.model.UpdateType;
import com.github.anicmv.telegrambot.repository.BotUserRepository;
import com.github.anicmv.telegrambot.repository.ChatMessageRepository;
import com.github.anicmv.telegrambot.repository.ProfileAllowUserRepository;
import com.github.anicmv.telegrambot.repository.UserProfileRepository;
import com.github.anicmv.telegrambot.service.ProfileAnalysisService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileCommandHandlerTest {

    @Mock
    private Messenger messenger;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private BotUserRepository botUserRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ProfileAnalysisService profileAnalysisService;

    @Mock
    private ProfileAllowUserRepository profileAllowUserRepository;

    @Mock
    private org.springframework.scheduling.TaskScheduler botScheduler;

    private BotProperties properties;
    private ProfileCommandHandler handler;

    @BeforeEach
    void setUp() {
        properties = new BotProperties();
        handler = new ProfileCommandHandler(messenger, userProfileRepository, botUserRepository,
                chatMessageRepository, profileAnalysisService, profileAllowUserRepository, properties,
                Runnable::run, botScheduler);
        // 默认视为已授权用户，走画像主流程；申请流程用例单独覆盖为 false
        lenient().when(profileAllowUserRepository.isApproved(anyLong())).thenReturn(true);
    }

    @Test
    void shouldShowFriendlyTextWhenNoProfileAndNoMessages() {
        when(messenger.sendReplyTextAndReturnMessageId(eq(-100123L), eq(7), anyString())).thenReturn(700);
        when(userProfileRepository.findByTelegramId(999L)).thenReturn(Optional.empty());
        when(profileAnalysisService.analyzeUser(999L, true)).thenReturn(ProfileAnalysisService.Result.SKIPPED);

        handler.execute(context("/profile"));

        verify(profileAnalysisService).analyzeUser(999L, true);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messenger, org.mockito.Mockito.atLeastOnce())
                .editMessageText(eq(-100123L), eq(700), captor.capture(), eq("HTML"));
        assertTrue(captor.getAllValues().getLast().contains("没有画像数据"));
    }

    @Test
    void shouldGenerateProfileOnDemandWhenMissing() {
        UserProfileEntity generated = new UserProfileEntity();
        generated.setTelegramUserId(999L);
        generated.setSummary("现场生成的画像正文");
        when(messenger.sendReplyTextAndReturnMessageId(eq(-100123L), eq(7), anyString())).thenReturn(700);
        when(userProfileRepository.findByTelegramId(999L))
                .thenReturn(Optional.empty(), Optional.of(generated));
        when(profileAnalysisService.analyzeUser(999L, true)).thenReturn(ProfileAnalysisService.Result.SUCCESS);
        when(botUserRepository.findByTelegramId(999L)).thenReturn(Optional.empty());
        when(chatMessageRepository.findStatsByUser(any(), eq(999L), any())).thenReturn(
                new ChatMessageRepository.UserMessageStats(1, LocalDateTime.now().minusDays(3), LocalDateTime.now()));

        handler.execute(context("/profile"));

        verify(profileAnalysisService).analyzeUser(999L, true);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messenger, org.mockito.Mockito.atLeastOnce())
                .editMessageText(eq(-100123L), eq(700), captor.capture(), eq("HTML"));
        String finalText = captor.getAllValues().getLast();
        assertTrue(finalText.contains("<blockquote>现场生成的画像正文"));
    }

    @Test
    void shouldFormatExistingProfile() {
        properties.getProfile().setRegenerateOnQuery(false);
        UserProfileEntity profile = new UserProfileEntity();
        profile.setTelegramUserId(999L);
        profile.setSummary("段落一\n\n段落二，群里的整活小王。");
        profile.setModel("qwen3.8-flash");
        profile.setTotalTokens(22867L);
        profile.setAnalyzedMessageCount(42);
        profile.setLastAnalyzedMessageId(100L);
        when(messenger.sendReplyTextAndReturnMessageId(eq(-100123L), eq(7), anyString())).thenReturn(700);
        when(userProfileRepository.findByTelegramId(999L)).thenReturn(Optional.of(profile));
        when(botUserRepository.findByTelegramId(999L))
                .thenReturn(Optional.of(new BotUserProfile(1L, "baaadcola", "某人", 999L, null, null)));
        when(chatMessageRepository.findStatsByUser(any(), eq(999L), eq(100L))).thenReturn(
                new ChatMessageRepository.UserMessageStats(2,
                        LocalDateTime.of(2026, 5, 31, 10, 0),
                        LocalDateTime.of(2026, 7, 24, 23, 0)));

        handler.execute(context("/profile"));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messenger).editMessageText(eq(-100123L), eq(700), captor.capture(), eq("HTML"));
        String text = captor.getValue();
        assertTrue(text.contains("用户画像："));
        assertTrue(text.contains("@baaadcola"));
        assertTrue(text.contains("样本 42 条 ｜ 群聊 2 个 ｜ 时间范围 05-31 ~ 07-24"));
        assertTrue(text.contains("<blockquote>段落一\n\n段落二"));
        assertTrue(text.contains("Powered by qwen3.8-flash ｜ 22,867 tokens"));
    }

    @Test
    void shouldScheduleAutoDeleteForProfileAndCommandMessages() {
        stubExistingProfileForDelivery();

        handler.execute(context("/profile"));

        ArgumentCaptor<java.time.Instant> instantCaptor = ArgumentCaptor.forClass(java.time.Instant.class);
        verify(botScheduler, org.mockito.Mockito.times(2))
                .schedule(any(Runnable.class), instantCaptor.capture());
        long delaySeconds = java.time.Duration.between(java.time.Instant.now(),
                instantCaptor.getValue()).getSeconds();
        assertTrue(delaySeconds >= 100 && delaySeconds <= 125, "expect ~120s delay, got " + delaySeconds);
    }

    @Test
    void shouldNotScheduleAutoDeleteWhenDisabled() {
        properties.getProfile().setAutoDeleteEnabled(false);
        stubExistingProfileForDelivery();

        handler.execute(context("/profile"));

        verify(botScheduler, never()).schedule(any(Runnable.class), any(java.time.Instant.class));
    }

    /** 存量画像 + 关闭现场重生的投递路径。 */
    private void stubExistingProfileForDelivery() {
        properties.getProfile().setRegenerateOnQuery(false);
        UserProfileEntity profile = new UserProfileEntity();
        profile.setTelegramUserId(999L);
        profile.setSummary("画像正文");
        profile.setAnalyzedMessageCount(10);
        profile.setLastAnalyzedMessageId(100L);
        when(messenger.sendReplyTextAndReturnMessageId(eq(-100123L), eq(7), anyString())).thenReturn(700);
        when(userProfileRepository.findByTelegramId(999L)).thenReturn(Optional.of(profile));
        when(botUserRepository.findByTelegramId(999L)).thenReturn(Optional.empty());
        when(chatMessageRepository.findStatsByUser(any(), eq(999L), eq(100L))).thenReturn(
                new ChatMessageRepository.UserMessageStats(0, null, null));
    }

    @Test
    void shouldForceRegenerateWhenExistingProfileAndSwitchOn() {
        UserProfileEntity stale = new UserProfileEntity();
        stale.setTelegramUserId(999L);
        stale.setSummary("旧版一句话人设");
        UserProfileEntity fresh = new UserProfileEntity();
        fresh.setTelegramUserId(999L);
        fresh.setSummary("重新生成的画像正文");
        when(messenger.sendReplyTextAndReturnMessageId(eq(-100123L), eq(7), anyString())).thenReturn(700);
        // 第一次查库存量画像，现场重新生成后再查返回新画像
        when(userProfileRepository.findByTelegramId(999L))
                .thenReturn(Optional.of(stale), Optional.of(fresh));
        when(profileAnalysisService.analyzeUser(999L, true)).thenReturn(ProfileAnalysisService.Result.SUCCESS);
        when(botUserRepository.findByTelegramId(999L)).thenReturn(Optional.empty());
        when(chatMessageRepository.findStatsByUser(any(), eq(999L), any())).thenReturn(
                new ChatMessageRepository.UserMessageStats(0, null, null));

        handler.execute(context("/profile"));

        verify(profileAnalysisService).analyzeUser(999L, true);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messenger, org.mockito.Mockito.atLeastOnce())
                .editMessageText(eq(-100123L), eq(700), captor.capture(), eq("HTML"));
        String finalText = captor.getAllValues().getLast();
        assertTrue(finalText.contains("重新生成的画像正文"));
        assertTrue(!finalText.contains("旧版一句话人设"));
    }

    @Test
    void unauthorizedUserShouldTriggerApprovalRequestWithButtons() {
        when(profileAllowUserRepository.isApproved(999L)).thenReturn(false);
        when(messenger.sendTextMessage(any(TextSpec.class))).thenReturn(801);

        handler.execute(context("/profile"));

        verify(profileAllowUserRepository).createOrResetRequest(999L);
        ArgumentCaptor<TextSpec> captor = ArgumentCaptor.forClass(TextSpec.class);
        verify(messenger).sendTextMessage(captor.capture());
        TextSpec spec = captor.getValue();
        assertEquals(7, spec.replyToMessageId());
        List<InlineButton> buttons = spec.callbackButtons();
        assertEquals(2, buttons.size());
        assertTrue(buttons.get(0).callbackData().startsWith("PROFILE_AUTH:Y:999"));
        assertTrue(buttons.get(1).callbackData().startsWith("PROFILE_AUTH:N:999"));
        // 申请按钮消息安排一次短延时清理（~30s）
        ArgumentCaptor<java.time.Instant> instantCaptor = ArgumentCaptor.forClass(java.time.Instant.class);
        verify(botScheduler, org.mockito.Mockito.times(1))
                .schedule(any(Runnable.class), instantCaptor.capture());
        long delaySeconds = java.time.Duration.between(java.time.Instant.now(), instantCaptor.getValue()).getSeconds();
        assertTrue(delaySeconds >= 20 && delaySeconds <= 35, "expect ~30s delay, got " + delaySeconds);
        verify(userProfileRepository, never()).findByTelegramId(anyLong());
    }

    @Test
    void adminShouldBypassDatabaseWhitelist() {
        properties.getProfile().getAdminUserIds().add(999L);
        when(messenger.sendReplyTextAndReturnMessageId(eq(-100123L), eq(7), anyString())).thenReturn(700);
        when(userProfileRepository.findByTelegramId(999L)).thenReturn(Optional.empty());
        when(profileAnalysisService.analyzeUser(999L, true)).thenReturn(ProfileAnalysisService.Result.SKIPPED);

        handler.execute(context("/profile"));

        // admin 短路放行，不查白名单库
        verify(profileAllowUserRepository, never()).isApproved(anyLong());
        verify(userProfileRepository).findByTelegramId(999L);
    }

    @Test
    void shouldRejectBotTarget() {
        Message replied = new Message();
        replied.setFrom(org.telegram.telegrambots.meta.api.objects.User.builder()
                .id(777L).firstName("SomeBot").userName("some_bot").isBot(true).build());
        Message command = new Message();
        command.setMessageId(7);
        command.setFrom(org.telegram.telegrambots.meta.api.objects.User.builder()
                .id(999L).firstName("caller").isBot(false).build());
        command.setReplyToMessage(replied);
        BotContext ctx = new BotContext(null, UpdateType.MESSAGE, -100123L, 999L, "/profile", command, null, null, null);

        handler.execute(ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messenger).sendReplyText(eq(-100123L), eq(7), captor.capture());
        assertTrue(captor.getValue().contains("机器人账号不支持"));
        verify(userProfileRepository, never()).findByTelegramId(anyLong());
        verify(profileAnalysisService, never()).analyzeUser(anyLong(), anyBoolean());
    }

    @Test
    void queryingOthersByUsernameShouldBeRejectedByDefault() {
        handler.execute(context("/profile @someone"));

        verify(userProfileRepository, never()).findByTelegramId(anyLong());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messenger).sendReplyText(eq(-100123L), eq(7), captor.capture());
        assertTrue(captor.getValue().contains("暂未开启"));
    }

    @Test
    void queryingOthersShouldWorkWhenEnabled() {
        properties.getProfile().setAllowQueryOthers(true);
        when(botUserRepository.findByUsername("someone"))
                .thenReturn(Optional.of(new BotUserProfile(1L, "someone", "某人", 888L, null, null)));
        when(messenger.sendReplyTextAndReturnMessageId(eq(-100123L), eq(7), anyString())).thenReturn(700);
        when(userProfileRepository.findByTelegramId(888L)).thenReturn(Optional.empty());

        handler.execute(context("/profile @someone"));

        verify(userProfileRepository, org.mockito.Mockito.atLeastOnce()).findByTelegramId(888L);
    }

    @Test
    void extractFirstArgumentShouldParseCommandArgument() {
        assertEquals("@abc", ProfileCommandHandler.extractFirstArgument("/profile @abc"));
        assertEquals("", ProfileCommandHandler.extractFirstArgument("/profile"));
        assertEquals("", ProfileCommandHandler.extractFirstArgument(null));
    }

    private BotContext context(String text) {
        Message message = new Message();
        message.setMessageId(7);
        return new BotContext(null, UpdateType.MESSAGE, -100123L, 999L, text, message, null, null, null);
    }
}
