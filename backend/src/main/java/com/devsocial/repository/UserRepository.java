package com.devsocial.repository;

import com.devsocial.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByUsernameIgnoreCase(String username);
    List<User> findByUsernameIgnoreCase(String username);
    boolean existsByEmail(String email);

    /**
     * Server-side people search over username, display name and skills (case-insensitive substring).
     * Matches at the start of a username or name rank first. {@code q} and {@code prefix} are LIKE
     * patterns escaped with '!'; {@code hidden} must not be empty (pass a dummy id).
     */
    @Query("""
            select u from User u
            where u.suspended = false and u.id <> :viewerId and u.id not in :hidden
              and (lower(u.username) like :q escape '!'
                   or lower(coalesce(u.displayName, '')) like :q escape '!'
                   or lower(coalesce(u.skills, '')) like :q escape '!')
            order by case when lower(u.username) like :prefix escape '!' then 0
                          when lower(coalesce(u.displayName, '')) like :prefix escape '!' then 1
                          else 2 end,
                     u.createdAt desc
            """)
    Slice<User> search(@Param("q") String q, @Param("prefix") String prefix, @Param("viewerId") Long viewerId,
                       @Param("hidden") Collection<Long> hidden, Pageable pageable);

    /** Newest active people the viewer doesn't follow yet. */
    @Query("""
            select u from User u
            where u.suspended = false and u.id <> :viewerId and u.id not in :hidden
              and u.id not in (select f.following.id from Follow f where f.follower.id = :viewerId)
            order by u.createdAt desc
            """)
    List<User> suggestions(@Param("viewerId") Long viewerId, @Param("hidden") Collection<Long> hidden, Pageable pageable);
}
