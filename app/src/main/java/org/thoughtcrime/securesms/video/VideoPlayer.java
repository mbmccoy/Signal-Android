/*
 * Copyright (C) 2017 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.video;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.video.exo.AttachmentDataSourceFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class VideoPlayer extends FrameLayout {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(VideoPlayer.class);

  private final PlayerView        exoView;
  private final PlayerControlView exoControls;

  private SimpleExoPlayer                     exoPlayer;
  private Window                              window;
  private PlayerStateCallback                 playerStateCallback;
  private PlayerPositionDiscontinuityCallback playerPositionDiscontinuityCallback;
  private PlayerCallback                      playerCallback;
  private boolean                             clipped;
  private long                                clippedStartUs;

  public VideoPlayer(Context context) {
    this(context, null);
  }

  public VideoPlayer(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public VideoPlayer(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    inflate(context, R.layout.video_player, this);

    this.exoView     = findViewById(R.id.video_view);
    this.exoControls = new PlayerControlView(getContext());
    this.exoControls.setShowTimeoutMs(-1);
  }

  private MediaItem mediaItem;

  public void setVideoSource(@NonNull VideoSlide videoSource, boolean autoplay) {
    Context context = getContext();

    if (exoPlayer == null) {
      DefaultDataSourceFactory    defaultDataSourceFactory    = new DefaultDataSourceFactory(context, "GenericUserAgent", null);
      AttachmentDataSourceFactory attachmentDataSourceFactory = new AttachmentDataSourceFactory(context, defaultDataSourceFactory, null);
      MediaSourceFactory          mediaSourceFactory          = new DefaultMediaSourceFactory(attachmentDataSourceFactory);

      exoPlayer = new SimpleExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build();
      exoPlayer.addListener(new ExoPlayerListener(this, window, playerStateCallback, playerPositionDiscontinuityCallback));
      exoPlayer.addListener(new Player.Listener() {
        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
          onPlaybackStateChanged(playWhenReady, exoPlayer.getPlaybackState());
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
          onPlaybackStateChanged(exoPlayer.getPlayWhenReady(), playbackState);
        }

        private void onPlaybackStateChanged(boolean playWhenReady, int playbackState) {
          if (playerCallback != null) {
            switch (playbackState) {
              case Player.STATE_READY:
                if (playWhenReady) playerCallback.onPlaying();
                break;
              case Player.STATE_ENDED:
                playerCallback.onStopped();
                break;
            }
          }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
          playerCallback.onError();
        }
      });
      exoView.setPlayer(exoPlayer);
      exoControls.setPlayer(exoPlayer);
    }

    mediaItem = MediaItem.fromUri(Objects.requireNonNull(videoSource.getUri()));
    exoPlayer.setMediaItem(mediaItem);
    exoPlayer.prepare();
    exoPlayer.setPlayWhenReady(autoplay);
  }

  public boolean isInitialized() {
    return exoPlayer != null;
  }

  public void setResizeMode(@AspectRatioFrameLayout.ResizeMode int resizeMode) {
    exoView.setResizeMode(resizeMode);
  }

  public void pause() {
    if (this.exoPlayer != null) {
      this.exoPlayer.setPlayWhenReady(false);
    }
  }

  public void hideControls() {
    if (this.exoView != null) {
      this.exoView.hideController();
    }
  }

  public @Nullable View getControlView() {
    return this.exoControls;
  }

  public void cleanup() {
    if (this.exoPlayer != null) {
      this.exoPlayer.release();
      this.exoPlayer = null;
    }
  }

  public void loopForever() {
    if (this.exoPlayer != null) {
      exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
    }
  }

  public long getDuration() {
    if (this.exoPlayer != null) {
      return this.exoPlayer.getDuration();
    }
    return 0L;
  }

  public long getPlaybackPosition() {
    if (this.exoPlayer != null) {
      return this.exoPlayer.getCurrentPosition();
    }
    return 0L;
  }

  public long getPlaybackPositionUs() {
    if (this.exoPlayer != null) {
      return TimeUnit.MILLISECONDS.toMicros(this.exoPlayer.getCurrentPosition()) + clippedStartUs;
    }
    return 0L;
  }

  public void setPlaybackPosition(long positionMs) {
    if (this.exoPlayer != null) {
      this.exoPlayer.seekTo(positionMs);
    }
  }

  public void clip(long fromUs, long toUs, boolean playWhenReady) {
    if (this.exoPlayer != null && mediaItem != null) {
      MediaItem clippedMediaItem = mediaItem.buildUpon()
                                            .setClipStartPositionMs(TimeUnit.MICROSECONDS.toMillis(fromUs))
                                            .setClipEndPositionMs(TimeUnit.MICROSECONDS.toMillis(toUs))
                                            .build();
      exoPlayer.setMediaItem(clippedMediaItem);
      exoPlayer.prepare();
      exoPlayer.setPlayWhenReady(playWhenReady);
      clipped        = true;
      clippedStartUs = fromUs;
    }
  }

  public void removeClip(boolean playWhenReady) {
    if (exoPlayer != null && mediaItem != null) {
      if (clipped) {
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        clipped        = false;
        clippedStartUs = 0;
      }
      exoPlayer.setPlayWhenReady(playWhenReady);
    }
  }

  public void setWindow(@Nullable Window window) {
    this.window = window;
  }

  public void setPlayerStateCallbacks(@Nullable PlayerStateCallback playerStateCallback) {
    this.playerStateCallback = playerStateCallback;
  }

  public void setPlayerCallback(PlayerCallback playerCallback) {
    this.playerCallback = playerCallback;
  }

  public void setPlayerPositionDiscontinuityCallback(@NonNull PlayerPositionDiscontinuityCallback playerPositionDiscontinuityCallback) {
    this.playerPositionDiscontinuityCallback = playerPositionDiscontinuityCallback;
  }

  /**
   * Resumes a paused video, or restarts if at end of video.
   */
  public void play() {
    if (exoPlayer != null) {
      exoPlayer.setPlayWhenReady(true);
      if (exoPlayer.getCurrentPosition() >= exoPlayer.getDuration()) {
        exoPlayer.seekTo(0);
      }
    }
  }

  private static class ExoPlayerListener implements Player.Listener {
    private final VideoPlayer                         videoPlayer;
    private final Window                              window;
    private final PlayerStateCallback                 playerStateCallback;
    private final PlayerPositionDiscontinuityCallback playerPositionDiscontinuityCallback;

    ExoPlayerListener(@NonNull VideoPlayer videoPlayer,
                      @Nullable Window window,
                      @Nullable PlayerStateCallback playerStateCallback,
                      @Nullable PlayerPositionDiscontinuityCallback playerPositionDiscontinuityCallback)
    {
      this.videoPlayer                         = videoPlayer;
      this.window                              = window;
      this.playerStateCallback                 = playerStateCallback;
      this.playerPositionDiscontinuityCallback = playerPositionDiscontinuityCallback;
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
      onPlaybackStateChanged(playWhenReady, videoPlayer.exoPlayer.getPlaybackState());
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
      onPlaybackStateChanged(videoPlayer.exoPlayer.getPlayWhenReady(), playbackState);
    }

    private void onPlaybackStateChanged(boolean playWhenReady, int playbackState) {
      switch (playbackState) {
        case Player.STATE_IDLE:
        case Player.STATE_BUFFERING:
        case Player.STATE_ENDED:
          if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
          }
          break;
        case Player.STATE_READY:
          if (window != null) {
            if (playWhenReady) {
              window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
              window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
          }
          notifyPlayerReady();
          break;
        default:
          break;
      }
    }

    @Override
    public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition,
                                        @NonNull Player.PositionInfo newPosition,
                                        int reason)
    {
      if (playerPositionDiscontinuityCallback != null) {
        playerPositionDiscontinuityCallback.onPositionDiscontinuity(videoPlayer, reason);
      }
    }

    private void notifyPlayerReady() {
      if (playerStateCallback != null) playerStateCallback.onPlayerReady();
    }
  }

  public interface PlayerStateCallback {
    void onPlayerReady();
  }

  public interface PlayerPositionDiscontinuityCallback {
    void onPositionDiscontinuity(@NonNull VideoPlayer player, int reason);
  }

  public interface PlayerCallback {

    void onPlaying();

    void onStopped();

    void onError();
  }
}
