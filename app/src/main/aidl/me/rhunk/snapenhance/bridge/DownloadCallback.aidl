package me.rhunk.snapenhance.bridge;

oneway interface DownloadCallback {
    void onSuccess(String outputPath);
    void onProgress(String message);
    void onFailure(String message, @nullable String throwable);
}
