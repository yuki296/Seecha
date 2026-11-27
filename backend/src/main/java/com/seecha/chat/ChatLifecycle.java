package com.seecha.chat;

import com.seecha.dao.TopicDao;
import com.seecha.match.MatchQueue;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@WebListener
public final class ChatLifecycle implements ServletContextListener {
    private final TopicDao topics = new TopicDao();
    private List<TopicStory> manualTopics = List.of();
    private ScheduledExecutorService housekeeping;
    private ScheduledExecutorService topicWorker;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        String publicOrigin = System.getenv("SEECHA_PUBLIC_ORIGIN");
        if (publicOrigin != null && !publicOrigin.isBlank()) {
            event.getServletContext().getSessionCookieConfig().setSecure(true);
        }
        housekeeping = Executors.newSingleThreadScheduledExecutor();
        housekeeping.scheduleWithFixedDelay(() -> MatchQueue.INSTANCE.tick(System.currentTimeMillis()),
                0, 5, TimeUnit.SECONDS);
        topicWorker = Executors.newSingleThreadScheduledExecutor();
        topicWorker.execute(() -> loadManualTopics(event));
        if (ChatProviders.generationEnabled()) {
            topicWorker.scheduleWithFixedDelay(() -> refreshTopics(event), 1, 30, TimeUnit.MINUTES);
        }
    }

    private void loadManualTopics(ServletContextEvent event) {
        try {
            manualTopics = topics.manualTopics();
            MatchQueue.INSTANCE.useManualTopics(manualTopics);
        } catch (SQLException exception) {
            event.getServletContext().log("Could not load manual chat topics: " + exception.getClass().getSimpleName());
        }
    }

    private void refreshTopics(ServletContextEvent event) {
        try {
            var topic = ChatProviders.generate();
            topics.save(topic.category(), topic.story());
            MatchQueue.INSTANCE.useTopics(topics.recent());
        } catch (IOException | SQLException | RuntimeException exception) {
            MatchQueue.INSTANCE.useManualTopics(manualTopics);
            event.getServletContext().log("Could not refresh chat topics: " + exception.getClass().getSimpleName());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        housekeeping.shutdownNow();
        topicWorker.shutdownNow();
    }
}
