/*
 * Copyright 2022 The Android Open Source Project
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

import static com.readwise.android.exoplayer2.transformer.AndroidTestUtil.MP3_ASSET_URI_STRING;
import static com.readwise.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_URI_STRING;
import static com.readwise.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING;
import static com.readwise.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING;
import static com.readwise.android.exoplayer2.transformer.AndroidTestUtil.PNG_ASSET_URI_STRING;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.readwise.android.exoplayer2.C;
import com.readwise.android.exoplayer2.Format;
import com.readwise.android.exoplayer2.MediaItem;
import com.readwise.android.exoplayer2.audio.AudioProcessor;
import com.readwise.android.exoplayer2.audio.SonicAudioProcessor;
import com.readwise.android.exoplayer2.effect.Contrast;
import com.readwise.android.exoplayer2.effect.FrameCache;
import com.readwise.android.exoplayer2.effect.Presentation;
import com.readwise.android.exoplayer2.effect.RgbFilter;
import com.readwise.android.exoplayer2.effect.TimestampWrapper;
import com.readwise.android.exoplayer2.util.Effect;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * End-to-end instrumentation test for {@link Transformer} for cases that cannot be tested using
 * robolectric.
 */
@RunWith(AndroidJUnit4.class)
public class TransformerEndToEndTest {

  private final Context context = ApplicationProvider.getApplicationContext();

  @Test
  public void videoEditing_withImageInput_completesWithCorrectFrameCountAndDuration()
      throws Exception {
    String testId = "videoEditing_withImageInput_completesWithCorrectFrameCountAndDuration";
    Transformer transformer = new Transformer.Builder(context).build();
    ImmutableList<Effect> videoEffects = ImmutableList.of(Presentation.createForHeight(480));
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    int expectedFrameCount = 40;
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(PNG_ASSET_URI_STRING))
            .setDurationUs(C.MICROS_PER_SECOND)
            .setFrameRate(expectedFrameCount)
            .setEffects(effects)
            .build();
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.videoFrameCount).isEqualTo(expectedFrameCount);
    // Expected timestamp of the last frame.
    assertThat(result.exportResult.durationMs)
        .isEqualTo((C.MILLIS_PER_SECOND / expectedFrameCount) * (expectedFrameCount - 1));
  }

  @Test
  public void videoTranscoding_withImageInput_completesWithCorrectFrameCountAndDuration()
      throws Exception {
    String testId = "videoTranscoding_withImageInput_completesWithCorrectFrameCountAndDuration";
    Transformer transformer = new Transformer.Builder(context).build();
    int expectedFrameCount = 40;
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(PNG_ASSET_URI_STRING))
            .setDurationUs(C.MICROS_PER_SECOND)
            .setFrameRate(expectedFrameCount)
            .build();
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.videoFrameCount).isEqualTo(expectedFrameCount);
    // Expected timestamp of the last frame.
    assertThat(result.exportResult.durationMs)
        .isEqualTo((C.MILLIS_PER_SECOND / expectedFrameCount) * (expectedFrameCount - 1));
  }

  @Test
  public void videoEditing_completesWithConsistentFrameCount() throws Exception {
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING));
    ImmutableList<Effect> videoEffects = ImmutableList.of(Presentation.createForHeight(480));
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();
    // Result of the following command:
    // ffprobe -count_frames -select_streams v:0 -show_entries stream=nb_read_frames sample.mp4
    int expectedFrameCount = 30;

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(/* testId= */ "videoEditing_completesWithConsistentFrameCount", editedMediaItem);

    assertThat(result.exportResult.videoFrameCount).isEqualTo(expectedFrameCount);
  }

  @Test
  public void videoEditing_effectsOverTime_completesWithConsistentFrameCount() throws Exception {
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING));
    ImmutableList<Effect> videoEffects =
        ImmutableList.of(
            new TimestampWrapper(
                new Contrast(.5f),
                /* startTimeUs= */ 0,
                /* endTimeUs= */ Math.round(.1f * C.MICROS_PER_SECOND)),
            new TimestampWrapper(
                new FrameCache(/* capacity= */ 5),
                /* startTimeUs= */ Math.round(.2f * C.MICROS_PER_SECOND),
                /* endTimeUs= */ Math.round(.3f * C.MICROS_PER_SECOND)));
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();
    // Result of the following command:
    // ffprobe -count_frames -select_streams v:0 -show_entries stream=nb_read_frames sample.mp4
    int expectedFrameCount = 30;

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(
                /* testId= */ "videoEditing_effectsOverTime_completesWithConsistentFrameCount",
                editedMediaItem);

    assertThat(result.exportResult.videoFrameCount).isEqualTo(expectedFrameCount);
  }

  @Test
  public void videoOnly_completesWithConsistentDuration() throws Exception {
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING));
    ImmutableList<Effect> videoEffects = ImmutableList.of(Presentation.createForHeight(480));
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).setEffects(effects).build();
    long expectedDurationMs = 967;

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(/* testId= */ "videoOnly_completesWithConsistentDuration", editedMediaItem);

    assertThat(result.exportResult.durationMs).isEqualTo(expectedDurationMs);
  }

  @Test
  public void clippedMedia_completesWithClippedDuration() throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    long clippingStartMs = 10_000;
    long clippingEndMs = 11_000;
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S_URI_STRING))
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clippingStartMs)
                    .setEndPositionMs(clippingEndMs)
                    .build())
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(/* testId= */ "clippedMedia_completesWithClippedDuration", mediaItem);

    assertThat(result.exportResult.durationMs).isAtMost(clippingEndMs - clippingStartMs);
  }

  @Test
  public void videoEncoderFormatUnsupported_completesWithError() {
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(new VideoUnsupportedEncoderFactory(context))
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING)))
            .setRemoveAudio(true)
            .build();

    ExportException exception =
        assertThrows(
            ExportException.class,
            () ->
                new TransformerAndroidTestRunner.Builder(context, transformer)
                    .build()
                    .run(
                        /* testId= */ "videoEncoderFormatUnsupported_completesWithError",
                        editedMediaItem));

    assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(exception.errorCode).isEqualTo(ExportException.ERROR_CODE_ENCODER_INIT_FAILED);
    assertThat(exception).hasMessageThat().contains("video");
  }

  @Test
  public void audioVideoTranscodedFromDifferentSequences_producesExpectedResult() throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    String testId = "audioVideoTranscodedFromDifferentSequences_producesExpectedResult";
    ImmutableList<AudioProcessor> audioProcessors = ImmutableList.of(new SonicAudioProcessor());
    ImmutableList<Effect> videoEffects = ImmutableList.of(RgbFilter.createGrayscaleFilter());
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_URI_STRING));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(new Effects(audioProcessors, videoEffects))
            .build();
    ExportTestResult expectedResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(new Effects(audioProcessors, /* videoEffects= */ ImmutableList.of()))
            .setRemoveVideo(true)
            .build();
    EditedMediaItemSequence audioSequence =
        new EditedMediaItemSequence(ImmutableList.of(audioEditedMediaItem));
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects))
            .setRemoveAudio(true)
            .build();
    EditedMediaItemSequence videoSequence =
        new EditedMediaItemSequence(ImmutableList.of(videoEditedMediaItem));
    Composition composition =
        new Composition.Builder(ImmutableList.of(audioSequence, videoSequence)).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition);

    assertThat(result.exportResult.channelCount)
        .isEqualTo(expectedResult.exportResult.channelCount);
    assertThat(result.exportResult.videoFrameCount)
        .isEqualTo(expectedResult.exportResult.videoFrameCount);
    assertThat(result.exportResult.durationMs).isEqualTo(expectedResult.exportResult.durationMs);
  }

  @Test
  public void loopingTranscodedAudio_producesExpectedResult() throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    String testId = "loopingTranscodedAudio_producesExpectedResult";
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP3_ASSET_URI_STRING)).build();
    EditedMediaItemSequence audioSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(audioEditedMediaItem, audioEditedMediaItem), /* isLooping= */ true);
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_URI_STRING))
            .setRemoveAudio(true)
            .build();
    EditedMediaItemSequence videoSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(videoEditedMediaItem, videoEditedMediaItem, videoEditedMediaItem));
    Composition composition =
        new Composition.Builder(ImmutableList.of(audioSequence, videoSequence))
            .setTransmuxVideo(true)
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition);

    assertThat(result.exportResult.processedInputs).hasSize(6);
    assertThat(result.exportResult.channelCount).isEqualTo(1);
    assertThat(result.exportResult.videoFrameCount).isEqualTo(90);
    assertThat(result.exportResult.durationMs).isEqualTo(2980);
  }

  @Test
  public void loopingTranscodedVideo_producesExpectedResult() throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    String testId = "loopingTranscodedVideo_producesExpectedResult";
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP3_ASSET_URI_STRING)).build();
    EditedMediaItemSequence audioSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(audioEditedMediaItem, audioEditedMediaItem, audioEditedMediaItem));
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET_URI_STRING))
            .setRemoveAudio(true)
            .build();
    EditedMediaItemSequence videoSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(videoEditedMediaItem, videoEditedMediaItem), /* isLooping= */ true);
    Composition composition =
        new Composition.Builder(ImmutableList.of(audioSequence, videoSequence)).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition);

    assertThat(result.exportResult.processedInputs).hasSize(7);
    assertThat(result.exportResult.channelCount).isEqualTo(1);
    assertThat(result.exportResult.videoFrameCount).isEqualTo(92);
    assertThat(result.exportResult.durationMs).isEqualTo(3105);
  }

  @Test
  public void loopingImage_producesExpectedResult() throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    String testId = "loopingImage_producesExpectedResult";
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP3_ASSET_URI_STRING)).build();
    EditedMediaItemSequence audioSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(audioEditedMediaItem, audioEditedMediaItem, audioEditedMediaItem));
    EditedMediaItem imageEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(PNG_ASSET_URI_STRING))
            .setDurationUs(1_000_000)
            .setFrameRate(30)
            .build();
    EditedMediaItemSequence imageSequence =
        new EditedMediaItemSequence(
            ImmutableList.of(imageEditedMediaItem, imageEditedMediaItem), /* isLooping= */ true);
    Composition composition =
        new Composition.Builder(ImmutableList.of(audioSequence, imageSequence)).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition);

    assertThat(result.exportResult.processedInputs).hasSize(7);
    assertThat(result.exportResult.channelCount).isEqualTo(1);
    assertThat(result.exportResult.videoFrameCount).isEqualTo(94);
    assertThat(result.exportResult.durationMs).isEqualTo(3100);
  }

  private static final class VideoUnsupportedEncoderFactory implements Codec.EncoderFactory {

    private final Codec.EncoderFactory encoderFactory;

    public VideoUnsupportedEncoderFactory(Context context) {
      encoderFactory = new DefaultEncoderFactory.Builder(context).build();
    }

    @Override
    public Codec createForAudioEncoding(Format format) throws ExportException {
      return encoderFactory.createForAudioEncoding(format);
    }

    @Override
    public Codec createForVideoEncoding(Format format) throws ExportException {
      throw ExportException.createForCodec(
          new IllegalArgumentException(),
          ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
          /* isVideo= */ true,
          /* isDecoder= */ false,
          format);
    }

    @Override
    public boolean audioNeedsEncoding() {
      return false;
    }

    @Override
    public boolean videoNeedsEncoding() {
      return true;
    }
  }
}
