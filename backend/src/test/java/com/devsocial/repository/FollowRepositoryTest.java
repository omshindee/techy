package com.devsocial.repository;

import com.devsocial.model.Follow;
import com.devsocial.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class FollowRepositoryTest {

    @Autowired UserRepository users;
    @Autowired FollowRepository follows;

    private User user(String name) {
        User u = new User();
        u.setUsername(name);
        u.setEmail(name + "@example.com");
        u.setPassword("hash");
        return users.save(u);
    }

    @Test
    void followCountsAndLookups() {
        User a = user("a_repo");
        User b = user("b_repo");
        follows.save(new Follow(a, b));

        assertTrue(follows.existsByFollowerIdAndFollowingId(a.getId(), b.getId()));
        assertFalse(follows.existsByFollowerIdAndFollowingId(b.getId(), a.getId()));
        assertEquals(1, follows.countByFollowingId(b.getId()));
        assertEquals(1, follows.countByFollowerId(a.getId()));
        assertTrue(follows.findFollowingIds(a.getId()).contains(b.getId()));
    }
}
