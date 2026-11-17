package com.devsocial.service;

import com.devsocial.dto.MessageDtos.*;
import com.devsocial.exception.ApiException;
import com.devsocial.model.Conversation;
import com.devsocial.model.Message;
import com.devsocial.model.User;
import com.devsocial.repository.ConversationRepository;
import com.devsocial.repository.FollowRepository;
import com.devsocial.repository.MessageRepository;
import com.devsocial.security.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Direct messages.
 *
 * Who can message whom: you can start a conversation with someone you follow; once they have
 * written to you, you can always reply (so the person you followed can answer without following
 * back). Blocking (either way) and suspended accounts stop messaging entirely.
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int PAGE = 50;
    private static final int INBOX_SIZE = 100;

    private final PushService pushService;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final FollowRepository follows;
    private final UserService userService;
    private final BlockService blockService;
    private final RateLimiter rateLimiter;

    /** Null when {@code me} may send to {@code other}; otherwise a user-facing reason. */
    public String cannotSendReason(User me, User other, Conversation existing) {
        if (me.getId().equals(other.getId())) return "You can't message yourself.";
        if (other.isSuspended()) return "This account isn't available.";
        if (blockService.eitherBlocked(me, other)) return "You can't message this account.";
        if (follows.existsByFollowerIdAndFollowingId(me.getId(), other.getId())) return null;
        if (existing != null && messages.existsByConversationIdAndSenderId(existing.getId(), other.getId())) return null;
        return "Follow @" + other.getUsername() + " to send them a message.";
    }

    private Optional<Conversation> find(User a, User b) {
        long lo = Math.min(a.getId(), b.getId()), hi = Math.max(a.getId(), b.getId());
        return conversations.findByUserAIdAndUserBId(lo, hi);
    }

    @Transactional
    public MessageResponse send(String meUsername, String otherUsername, String content) {
        User me = userService.getEntity(meUsername);
        User other = userService.getVisible(otherUsername, me);
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Write a message first.");
        if (text.length() > Message.MAX_LENGTH) throw new ApiException(HttpStatus.BAD_REQUEST, "Messages can be up to " + Message.MAX_LENGTH + " characters.");
        userService.requireVerified(me);
        Conversation conversation = find(me, other).orElse(null);
        String reason = cannotSendReason(me, other, conversation);
        if (reason != null) throw new ApiException(HttpStatus.FORBIDDEN, reason);
        rateLimiter.check("messages-minute", meUsername, 30, Duration.ofMinutes(1));
        rateLimiter.check("messages-hour", meUsername, 300, Duration.ofHours(1));

        if (conversation == null) conversation = conversations.save(new Conversation(me, other));
        Message m = new Message();
        m.setConversation(conversation);
        m.setSender(me);
        m.setContent(text);
        messages.save(m);
        conversation.setLastMessageAt(m.getCreatedAt());
        pushService.notify(other, new PushService.Push(me.getNameToShow(), text,
                "/messages/" + me.getUsername(), "dm-" + me.getUsername(), true));
        return toResponse(m, me);
    }

    /**
     * The chat with {@code otherUsername}: the newest 50 messages, or older ones ({@code before}),
     * or only new ones since {@code after} (used while the chat is open). Marks received messages read.
     */
    @Transactional
    public ThreadResponse thread(String meUsername, String otherUsername, Long after, Long before) {
        User me = userService.getEntity(meUsername);
        User other = userService.getVisible(otherUsername, me);
        Conversation conversation = find(me, other).orElse(null);
        boolean following = follows.existsByFollowerIdAndFollowingId(me.getId(), other.getId());
        String reason = cannotSendReason(me, other, conversation);
        if (conversation == null) {
            return new ThreadResponse(null, userService.author(other), following, List.of(), false, null, reason == null, reason);
        }
        List<Message> page;
        boolean hasOlder = false;
        if (after != null) {
            page = messages.findByConversationIdAndIdGreaterThanOrderByIdAsc(conversation.getId(), after, PageRequest.of(0, 200));
        } else {
            List<Message> desc = before == null
                    ? messages.findByConversationIdOrderByIdDesc(conversation.getId(), PageRequest.of(0, PAGE + 1))
                    : messages.findByConversationIdAndIdLessThanOrderByIdDesc(conversation.getId(), before, PageRequest.of(0, PAGE + 1));
            hasOlder = desc.size() > PAGE;
            page = new ArrayList<>(desc.subList(0, Math.min(PAGE, desc.size())));
            Collections.reverse(page);
        }
        messages.markRead(conversation.getId(), me.getId(), Instant.now());
        Long seenUpTo = messages.lastReadIdSentBy(conversation.getId(), me.getId());
        return new ThreadResponse(conversation.getId(), userService.author(other), following,
                page.stream().map(m -> toResponse(m, me)).toList(), hasOlder, seenUpTo, reason == null, reason);
    }

    /** Your conversations, newest first, with the last message and unread count. Hides blocked/suspended people. */
    @Transactional(readOnly = true)
    public List<ConversationSummary> inbox(String meUsername) {
        User me = userService.getEntity(meUsername);
        Set<Long> hidden = blockService.hiddenIds(me);
        List<Conversation> list = conversations.findForUser(me.getId(), PageRequest.of(0, INBOX_SIZE)).stream()
                .filter(c -> { User o = c.otherThan(me); return !hidden.contains(o.getId()) && !o.isSuspended(); })
                .toList();
        if (list.isEmpty()) return List.of();
        Map<Long, Message> last = messages.latestIn(list.stream().map(Conversation::getId).toList()).stream()
                .collect(Collectors.toMap(m -> m.getConversation().getId(), Function.identity()));
        Map<Long, Long> unread = unreadMap(me);
        return list.stream().filter(c -> last.containsKey(c.getId())).map(c -> {
            Message m = last.get(c.getId());
            return new ConversationSummary(c.getId(), userService.author(c.otherThan(me)), preview(m.getContent()),
                    m.getSender().getId().equals(me.getId()), m.getCreatedAt(), unread.getOrDefault(c.getId(), 0L));
        }).toList();
    }

    /** Total unread messages (for the navigation badge), ignoring hidden conversations. */
    @Transactional(readOnly = true)
    public long unreadCount(String meUsername) {
        User me = userService.getEntity(meUsername);
        Map<Long, Long> unread = unreadMap(me);
        if (unread.isEmpty()) return 0;
        Set<Long> hidden = blockService.hiddenIds(me);
        return conversations.findAllById(unread.keySet()).stream()
                .filter(c -> { User o = c.otherThan(me); return !hidden.contains(o.getId()) && !o.isSuspended(); })
                .mapToLong(c -> unread.get(c.getId())).sum();
    }

    private Map<Long, Long> unreadMap(User me) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] r : messages.unreadByConversation(me.getId())) map.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        return map;
    }

    private static String preview(String s) {
        String one = s.replaceAll("\\s+", " ").strip();
        return one.length() > 120 ? one.substring(0, 120) + "…" : one;
    }

    private MessageResponse toResponse(Message m, User me) {
        boolean mine = m.getSender().getId().equals(me.getId());
        return new MessageResponse(m.getId(), m.getSender().getId(), mine, m.getContent(), m.getCreatedAt());
    }
}
