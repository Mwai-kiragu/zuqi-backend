package com.zuqi.repository;

import com.zuqi.domain.ai.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * All messages in a conversation ordered chronologically (for context window assembly).
     */
    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    /**
     * Recent messages in a conversation, newest first, paginated (for truncated context).
     */
    Page<ChatMessage> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    /**
     * Distinct conversation IDs for a distributor + user, most recently active first.
     */
    @Query("SELECT DISTINCT m.conversationId FROM ChatMessage m " +
           "WHERE m.distributor.id = :distributorId AND m.userId = :userId " +
           "ORDER BY m.conversationId")
    List<UUID> findConversationIdsByDistributorAndUser(
            @Param("distributorId") UUID distributorId,
            @Param("userId") UUID userId,
            Pageable pageable);

    /**
     * Count messages in a conversation (for session length checks).
     */
    long countByConversationId(UUID conversationId);

    /**
     * Delete all messages for a conversation (session reset).
     */
    void deleteByConversationId(UUID conversationId);
}
