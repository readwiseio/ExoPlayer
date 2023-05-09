/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.readwise.android.exoplayer2.transformer;

import com.readwise.android.exoplayer2.MediaItem;
import com.readwise.android.exoplayer2.audio.AudioProcessor;
import com.readwise.android.exoplayer2.effect.DefaultVideoFrameProcessor;
import com.readwise.android.exoplayer2.util.Effect;
import com.readwise.android.exoplayer2.util.VideoFrameProcessor;
import com.google.common.collect.ImmutableList;
import java.util.List;

/** Effects to apply to a {@link MediaItem} or to a {@link Composition}. */
public final class Effects {

  /** An empty {@link Effects} instance. */
  public static final Effects EMPTY =
      new Effects(
          /* audioProcessors= */ ImmutableList.of(), /* videoEffects= */ ImmutableList.of());

  /**
   * The list of {@linkplain AudioProcessor audio processors} to apply to audio buffers. They are
   * applied in the order of the list, and buffers will only be modified by that {@link
   * AudioProcessor} if it {@link AudioProcessor#isActive()} based on the current configuration.
   */
  public final ImmutableList<AudioProcessor> audioProcessors;
  /**
   * The list of {@linkplain Effect video effects} to apply to each frame. They are applied in the
   * order of the list.
   */
  public final ImmutableList<Effect> videoEffects;
  /**
   * The {@link VideoFrameProcessor.Factory} for the {@link VideoFrameProcessor} to use when
   * applying the {@code videoEffects} to the video frames.
   */
  public final VideoFrameProcessor.Factory videoFrameProcessorFactory;

  /**
   * Creates an instance using a {@link DefaultVideoFrameProcessor.Factory}.
   *
   * <p>This is equivalent to calling {@link Effects#Effects(List, List,
   * VideoFrameProcessor.Factory)} with a {@link DefaultVideoFrameProcessor.Factory}.
   */
  public Effects(List<AudioProcessor> audioProcessors, List<Effect> videoEffects) {
    this(audioProcessors, videoEffects, new DefaultVideoFrameProcessor.Factory.Builder().build());
  }

  /**
   * Creates an instance.
   *
   * @param audioProcessors The {@link #audioProcessors}.
   * @param videoEffects The {@link #videoEffects}.
   * @param videoFrameProcessorFactory The {@link #videoFrameProcessorFactory}.
   */
  public Effects(
      List<AudioProcessor> audioProcessors,
      List<Effect> videoEffects,
      VideoFrameProcessor.Factory videoFrameProcessorFactory) {
    this.audioProcessors = ImmutableList.copyOf(audioProcessors);
    this.videoEffects = ImmutableList.copyOf(videoEffects);
    this.videoFrameProcessorFactory = videoFrameProcessorFactory;
  }
}
