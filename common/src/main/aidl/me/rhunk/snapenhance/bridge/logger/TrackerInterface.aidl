package me.rhunk.snapenhance.bridge.logger;

interface TrackerInterface {
    String getTrackedEvents(String eventType); // returns serialized TrackerEventsResult
}