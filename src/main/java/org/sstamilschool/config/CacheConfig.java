package org.sstamilschool.config;

import java.time.Duration;
import java.util.List;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String TEAM_MEMBERS = "teamMembers";
    public static final String CALENDAR_EVENTS = "calendarEvents";

    @Bean
    public CacheManager cacheManager(
            @Value("${app.cache.team-ttl-hours:24}") long teamTtlHours,
            @Value("${app.cache.calendar-ttl-hours:24}") long calendarTtlHours) {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                caffeineCache(TEAM_MEMBERS, teamTtlHours),
                caffeineCache(CALENDAR_EVENTS, calendarTtlHours)));
        return cacheManager;
    }

    private static CaffeineCache caffeineCache(String name, long ttlHours) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(ttlHours))
                .maximumSize(10)
                .build());
    }
}
