package com.pixierge.api.background;

public interface BackgroundJobHandler {

    String jobType();

    void handle(BackgroundJobRecord job) throws Exception;
}
