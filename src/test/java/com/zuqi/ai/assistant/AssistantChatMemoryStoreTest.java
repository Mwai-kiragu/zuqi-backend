package com.zuqi.ai.assistant;

import com.zuqi.domain.ai.ChatMessage;
import com.zuqi.domain.ai.ChatRole;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.ChatMessageRepository;
import com.zuqi.repository.DistributorRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AssistantChatMemoryStore}.
 *
 * Covers getMessages (empty, mapped, exception), updateMessages (new-only, skip,
 * tool-skip, no-context) and deleteMessages.
 */
@ExtendWith(MockitoExtension.class)
class AssistantChatMemoryStoreTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private DistributorRepository distributorRepository;

    @InjectMocks
    private AssistantChatMemoryStore store;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID distributorId  = UUID.randomUUID();
    private final UUID userId         = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        AssistantMemoryContext.clear();
    }

    // ── getMessages ───────────────────────────────────────────────────────────

    @Test
    void getMessages_returnsEmptyListForNewConversation() {
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(List.of());

        List<dev.langchain4j.data.message.ChatMessage> result = store.getMessages(conversationId);

        assertThat(result).isEmpty();
        verify(chatMessageRepository).findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Test
    void getMessages_mapsUserAndAssistantMessages() {
        ChatMessage userRow = buildRow(ChatRole.USER, "hello");
        ChatMessage assistantRow = buildRow(ChatRole.ASSISTANT, "hi");

        when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(List.of(userRow, assistantRow));

        List<dev.langchain4j.data.message.ChatMessage> result = store.getMessages(conversationId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) result.get(0)).singleText()).isEqualTo("hello");
        assertThat(result.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) result.get(1)).text()).isEqualTo("hi");
    }

    @Test
    void getMessages_handlesRepositoryException() {
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenThrow(new RuntimeException("DB unavailable"));

        List<dev.langchain4j.data.message.ChatMessage> result = store.getMessages(conversationId);

        assertThat(result).isEmpty();
    }

    // ── updateMessages ────────────────────────────────────────────────────────

    @Test
    void updateMessages_savesOnlyNewMessages() {
        AssistantMemoryContext.set(distributorId, userId, "qwen2.5-coder:32b");
        when(distributorRepository.findById(distributorId))
                .thenReturn(Optional.of(new Distributor()));

        // 1 message already in DB
        when(chatMessageRepository.countByConversationId(conversationId)).thenReturn(1L);

        // 3 messages in the full window: user + ai (already saved) + new ai reply
        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                UserMessage.from("hello"),
                AiMessage.from("hi"),
                AiMessage.from("how can I help?")
        );

        store.updateMessages(conversationId, messages);

        // Only the 2 new messages (index 1 and 2 beyond already-saved 1) should be saved
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
    }

    @Test
    void updateMessages_skipsWhenNoNewMessages() {
        AssistantMemoryContext.set(distributorId, userId, "qwen2.5-coder:32b");

        // DB already has 3 messages
        when(chatMessageRepository.countByConversationId(conversationId)).thenReturn(3L);

        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                UserMessage.from("hello"),
                AiMessage.from("hi"),
                AiMessage.from("sure")
        );

        store.updateMessages(conversationId, messages);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void updateMessages_skipsToolExecutionMessages() {
        AssistantMemoryContext.set(distributorId, userId, "qwen2.5-coder:32b");
        when(distributorRepository.findById(distributorId))
                .thenReturn(Optional.of(new Distributor()));

        // Nothing in DB yet
        when(chatMessageRepository.countByConversationId(conversationId)).thenReturn(0L);

        ToolExecutionResultMessage toolMsg =
                new ToolExecutionResultMessage("tool-id", "toolName", "result");

        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                UserMessage.from("run tool"),
                toolMsg,
                AiMessage.from("done")
        );

        store.updateMessages(conversationId, messages);

        // UserMessage + AiMessage saved; ToolExecutionResultMessage skipped
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));

        // Confirm the saved entities have only USER and ASSISTANT roles
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        List<ChatRole> roles = captor.getAllValues().stream()
                .map(ChatMessage::getRole)
                .toList();
        assertThat(roles).containsExactlyInAnyOrder(ChatRole.USER, ChatRole.ASSISTANT);
    }

    @Test
    void updateMessages_doesNothingWhenNoContext() {
        // Context deliberately NOT set
        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                UserMessage.from("hello"),
                AiMessage.from("hi")
        );

        store.updateMessages(conversationId, messages);

        verify(chatMessageRepository, never()).save(any());
        verify(chatMessageRepository, never()).countByConversationId(any());
    }

    // ── deleteMessages ────────────────────────────────────────────────────────

    @Test
    void deleteMessages_callsRepositoryDelete() {
        store.deleteMessages(conversationId);

        verify(chatMessageRepository).deleteByConversationId(conversationId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ChatMessage buildRow(ChatRole role, String content) {
        return ChatMessage.builder()
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .build();
    }
}
