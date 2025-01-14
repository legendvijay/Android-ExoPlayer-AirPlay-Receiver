package com.github.warren_bank.exoplayer_airplay_receiver.exoplayer2;

/*
 * based on:
 *   https://github.com/google/ExoPlayer/blob/r2.15.1/demos/main/src/main/java/com/google/android/exoplayer2/demo/DemoUtil.java
 */

import com.github.warren_bank.exoplayer_airplay_receiver.BuildConfig;
import com.github.warren_bank.exoplayer_airplay_receiver.R;
import com.github.warren_bank.exoplayer_airplay_receiver.utils.PreferencesMgr;

import android.content.Context;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.offline.ActionFileUpgradeUtil;
import com.google.android.exoplayer2.offline.DefaultDownloadIndex;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.ui.DownloadNotificationHelper;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Log;
import java.io.File;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.concurrent.Executors;

/** Utility methods for the demo app. */
public final class ExoPlayerUtils {

  public static final String DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel";

  private static final String TAG = "ExoPlayerUtils";
  private static final String DOWNLOAD_ACTION_FILE = "actions";
  private static final String DOWNLOAD_TRACKER_ACTION_FILE = "tracked_actions";
  private static final String DOWNLOAD_CONTENT_DIRECTORY = "downloads";

  private static String USER_AGENT;
  private static DefaultHttpDataSource.Factory httpDataSourceFactory;
  private static DataSource.Factory defaultDataSourceFactory;
  private static CacheDataSource.Factory cacheDataSourceFactory;
  private static DatabaseProvider databaseProvider;
  private static File downloadDirectory;
  private static Cache downloadCache;
  private static DownloadManager downloadManager;
  private static DownloadTracker downloadTracker;
  private static DownloadNotificationHelper downloadNotificationHelper;

  /** Returns whether extension renderers should be used. */
  public static boolean useExtensionRenderers() {
    return BuildConfig.USE_DECODER_EXTENSIONS;
  }

  public static int getExtensionRendererMode(boolean preferExtensionRenderer) {
    @DefaultRenderersFactory.ExtensionRendererMode
    int extensionRendererMode =
        useExtensionRenderers()
            ? (preferExtensionRenderer
                ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;

    return extensionRendererMode;
  }

  public static RenderersFactory buildRenderersFactory(Context context, boolean preferExtensionRenderer) {
    @DefaultRenderersFactory.ExtensionRendererMode
    int extensionRendererMode = getExtensionRendererMode(preferExtensionRenderer);

    return new DefaultRenderersFactory(context.getApplicationContext())
        .setExtensionRendererMode(extensionRendererMode);
  }

  public static synchronized void setUserAgent(String userAgent) {
    USER_AGENT = userAgent;

    if (httpDataSourceFactory != null) {
      httpDataSourceFactory.setUserAgent(USER_AGENT);
    }
  }

  public static synchronized DefaultHttpDataSource.Factory getHttpDataSourceFactory(Context context) {
    if (httpDataSourceFactory == null) {
      CookieManager cookieManager = new CookieManager();
      cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
      CookieHandler.setDefault(cookieManager);
      httpDataSourceFactory = new DefaultHttpDataSource.Factory();

      if (USER_AGENT != null) {
        httpDataSourceFactory.setUserAgent(USER_AGENT);
      }
    }
    return httpDataSourceFactory;
  }

  /** Returns a {@link DataSource.Factory}. */
  public static synchronized DataSource.Factory getDefaultDataSourceFactory(Context context) {
    if (defaultDataSourceFactory == null) {
      context = context.getApplicationContext();
      defaultDataSourceFactory = new DefaultDataSourceFactory(context, getHttpDataSourceFactory(context));
    }
    return defaultDataSourceFactory;
  }

  /** Returns a {@link CacheDataSource.Factory}. */
  public static synchronized CacheDataSource.Factory getCacheDataSourceFactory(Context context) {
    if (cacheDataSourceFactory == null) {
      context = context.getApplicationContext();
      cacheDataSourceFactory = buildReadOnlyCacheDataSource(getDefaultDataSourceFactory(context), getDownloadCache(context));
    }
    return cacheDataSourceFactory;
  }

  public static synchronized DownloadNotificationHelper getDownloadNotificationHelper(Context context) {
    if (downloadNotificationHelper == null) {
      downloadNotificationHelper = new DownloadNotificationHelper(context, DOWNLOAD_NOTIFICATION_CHANNEL_ID);
    }
    return downloadNotificationHelper;
  }

  public static synchronized DownloadManager getDownloadManager(Context context) {
    ensureDownloadManagerInitialized(context);
    return downloadManager;
  }

  public static synchronized DownloadTracker getDownloadTracker(Context context) {
    ensureDownloadManagerInitialized(context);
    return downloadTracker;
  }

  private static synchronized Cache getDownloadCache(Context context) {
    if (downloadCache == null) {
      File downloadContentDirectory = new File(getDownloadDirectory(context), DOWNLOAD_CONTENT_DIRECTORY);
      downloadCache = new SimpleCache(downloadContentDirectory, new NoOpCacheEvictor(), getDatabaseProvider(context));
    }
    return downloadCache;
  }

  private static synchronized void ensureDownloadManagerInitialized(Context context) {
    if (downloadManager == null) {
      DefaultDownloadIndex downloadIndex = new DefaultDownloadIndex(getDatabaseProvider(context));

      upgradeActionFile(context, DOWNLOAD_ACTION_FILE,         downloadIndex, /* addNewDownloadsAsCompleted= */ false);
      upgradeActionFile(context, DOWNLOAD_TRACKER_ACTION_FILE, downloadIndex, /* addNewDownloadsAsCompleted= */ true);

      int threadPoolSize = PreferencesMgr.get_max_parallel_downloads();

      downloadManager = new DownloadManager(
          context,
          getDatabaseProvider(context),
          getDownloadCache(context),
          getHttpDataSourceFactory(context),
          Executors.newFixedThreadPool(threadPoolSize)
      );

      downloadTracker = new DownloadTracker(context, getHttpDataSourceFactory(context), downloadManager);
    }
  }

  private static synchronized void upgradeActionFile(Context context, String fileName, DefaultDownloadIndex downloadIndex, boolean addNewDownloadsAsCompleted) {
    try {
      ActionFileUpgradeUtil.upgradeAndDelete(
          new File(getDownloadDirectory(context), fileName),
          /* downloadIdProvider= */ null,
          downloadIndex,
          /* deleteOnFailure= */ true,
          addNewDownloadsAsCompleted
      );
    }
    catch (IOException e) {
      Log.e(TAG, "Failed to upgrade action file: " + fileName, e);
    }
  }

  private static synchronized DatabaseProvider getDatabaseProvider(Context context) {
    if (databaseProvider == null) {
      databaseProvider = new ExoDatabaseProvider(context);
    }
    return databaseProvider;
  }

  private static synchronized File getDownloadDirectory(Context context) {
    if (downloadDirectory == null) {
      downloadDirectory = context.getExternalFilesDir(/* type= */ null);

      if (downloadDirectory == null) {
        downloadDirectory = context.getFilesDir();
      }
    }
    return downloadDirectory;
  }

  private static CacheDataSource.Factory buildReadOnlyCacheDataSource(DataSource.Factory upstreamFactory, Cache cache) {
    return new CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstreamFactory)
        .setCacheWriteDataSinkFactory(null)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
  }

}
