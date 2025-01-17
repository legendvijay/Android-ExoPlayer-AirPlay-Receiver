package com.github.warren_bank.exoplayer_airplay_receiver.exoplayer2.customizations;

import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;

import java.io.IOException;

public class MyLoadErrorHandlingPolicy extends DefaultLoadErrorHandlingPolicy {
  private static final String TAG = "LoadErrorHandlingPolicy";

  private static final int  DEFAULT_RETRY_COUNT            = 3;
  private static final long DEFAULT_OFFLINE_RETRY_DELAY_MS = 6000l;

  private boolean isOffline;
  private int     retryCount;
  private long    offlineRetryDelayMs;

  public MyLoadErrorHandlingPolicy() {
    this(DEFAULT_RETRY_COUNT, DEFAULT_OFFLINE_RETRY_DELAY_MS);
  }

  public MyLoadErrorHandlingPolicy(int retryCount) {
    this(retryCount, DEFAULT_OFFLINE_RETRY_DELAY_MS);
  }

  public MyLoadErrorHandlingPolicy(long offlineRetryDelayMs) {
    this(DEFAULT_RETRY_COUNT, offlineRetryDelayMs);
  }

  public MyLoadErrorHandlingPolicy(int retryCount, long offlineRetryDelayMs) {
    super();
    this.isOffline           = false;
    this.retryCount          = retryCount;
    this.offlineRetryDelayMs = offlineRetryDelayMs;
  }

  // ===========================================================================
  // https://github.com/google/ExoPlayer/blob/r2.15.1/library/core/src/main/java/com/google/android/exoplayer2/upstream/DefaultLoadErrorHandlingPolicy.java#L108
  // https://github.com/google/ExoPlayer/blob/r2.15.1/library/core/src/main/java/com/google/android/exoplayer2/upstream/LoadErrorHandlingPolicy.java#L70
  // https://github.com/google/ExoPlayer/blob/r2.15.1/library/core/src/main/java/com/google/android/exoplayer2/source/LoadEventInfo.java
  // https://github.com/google/ExoPlayer/blob/r2.15.1/library/core/src/main/java/com/google/android/exoplayer2/source/MediaLoadData.java
  // ===========================================================================

  @Override
  public long getRetryDelayMsFor(LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo) {
    int         dataType       = loadErrorInfo.mediaLoadData.dataType;
    long        loadDurationMs = loadErrorInfo.loadEventInfo.loadDurationMs;
    IOException exception      = loadErrorInfo.exception;
    int         errorCount     = loadErrorInfo.errorCount;

    Log.e(TAG, "getRetryDelayMsFor [" + errorCount + "@" + loadDurationMs + "]", exception);

    isOffline = (exception instanceof HttpDataSourceException);

    return (isOffline)
      ? offlineRetryDelayMs
      : super.getRetryDelayMsFor(loadErrorInfo)
    ;
  }

  @Override
  public int getMinimumLoadableRetryCount(int dataType) {
    if (isOffline)
      return Integer.MAX_VALUE;

    if (dataType == C.DATA_TYPE_MEDIA_PROGRESSIVE_LIVE)
      return Integer.MAX_VALUE;

    return retryCount;
  }
}
