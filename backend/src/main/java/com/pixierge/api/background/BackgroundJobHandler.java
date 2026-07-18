package com.pixierge.api.background;

public interface BackgroundJobHandler {

    String jobType();

    void handle(BackgroundJobRecord job) throws Exception;

    default void afterComplete(BackgroundJobRecord job) throws Exception {
    }
}
