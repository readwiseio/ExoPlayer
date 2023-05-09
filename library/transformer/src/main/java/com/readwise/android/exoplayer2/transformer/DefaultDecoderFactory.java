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

import static com.readwise.android.exoplayer2.util.Assertions.checkNotNull;
import static com.readwise.android.exoplayer2.util.MediaFormatUtil.createMediaFormatFromFormat;
import static com.readwise.android.exoplayer2.util.Util.SDK_INT;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.readwise.android.exoplayer2.Format;
import com.readwise.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.readwise.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.readwise.android.exoplayer2.util.Log;
import com.readwise.android.exoplayer2.util.MediaFormatUtil;
import com.readwise.android.exoplayer2.util.MimeTypes;
import com.readwise.android.exoplayer2.util.Util;
import com.readwise.android.exoplayer2.video.ColorInfo;
import java.util.List;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** A default implementation of {@link Codec.DecoderFactory}. */
/* package */ final class DefaultDecoderFactory implements Codec.DecoderFactory {

  private static final String TAG = "DefaultDecoderFactory";

  private final Context context;

  private final boolean decoderSupportsKeyAllowFrameDrop;

  public DefaultDecoderFactory(Context context) {
    this.context = context;

    decoderSupportsKeyAllowFrameDrop =
        SDK_INT >= 29
            && context.getApplicationContext().getApplicationInfo().targetSdkVersion >= 29;
  }

  @Override
  public Codec createForAudioDecoding(Format format) throws ExportException {
    checkNotNull(format.sampleMimeType);
    MediaFormat mediaFormat = createMediaFormatFromFormat(format);

    String mediaCodecName;
    try {
      mediaCodecName = getMediaCodecNameForDecoding(format);
    } catch (MediaCodecUtil.DecoderQueryException e) {
      Log.e(TAG, "Error querying decoders", e);
      throw createExportException(format, /* reason= */ "Querying codecs failed.");
    }

    return new DefaultCodec(
        context,
        format,
        mediaFormat,
        mediaCodecName,
        /* isDecoder= */ true,
        /* outputSurface= */ null);
  }

  @SuppressLint("InlinedApi")
  @Override
  public Codec createForVideoDecoding(
      Format format, Surface outputSurface, boolean requestSdrToneMapping) throws ExportException {
    checkNotNull(format.sampleMimeType);

    if (ColorInfo.isTransferHdr(format.colorInfo)) {
      if (requestSdrToneMapping && (SDK_INT < 31 || deviceNeedsNoToneMappingWorkaround())) {
        throw createExportException(
            format, /* reason= */ "Tone-mapping HDR is not supported on this device.");
      }
      if (SDK_INT < 29) {
        // TODO(b/266837571, b/267171669): Remove API version restriction after fixing linked bugs.
        throw createExportException(
            format, /* reason= */ "Decoding HDR is not supported on this device.");
      }
    }

    MediaFormat mediaFormat = createMediaFormatFromFormat(format);
    if (decoderSupportsKeyAllowFrameDrop) {
      // This key ensures no frame dropping when the decoder's output surface is full. This allows
      // transformer to decode as many frames as possible in one render cycle.
      mediaFormat.setInteger(MediaFormat.KEY_ALLOW_FRAME_DROP, 0);
    }
    if (SDK_INT >= 31 && requestSdrToneMapping) {
      mediaFormat.setInteger(
          MediaFormat.KEY_COLOR_TRANSFER_REQUEST, MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
    }

    String mediaCodecName;
    try {
      mediaCodecName = getMediaCodecNameForDecoding(format);
    } catch (MediaCodecUtil.DecoderQueryException e) {
      Log.e(TAG, "Error querying decoders", e);
      throw createExportException(format, /* reason= */ "Querying codecs failed");
    }

    @Nullable
    Pair<Integer, Integer> codecProfileAndLevel = MediaCodecUtil.getCodecProfileAndLevel(format);
    if (codecProfileAndLevel != null) {
      MediaFormatUtil.maybeSetInteger(
          mediaFormat, MediaFormat.KEY_PROFILE, codecProfileAndLevel.first);
      MediaFormatUtil.maybeSetInteger(
          mediaFormat, MediaFormat.KEY_LEVEL, codecProfileAndLevel.second);
    }

    return new DefaultCodec(
        context, format, mediaFormat, mediaCodecName, /* isDecoder= */ true, outputSurface);
  }

  private static boolean deviceNeedsNoToneMappingWorkaround() {
    // Some Pixel 6 builds report support for tone mapping but the feature doesn't work
    // (see http://b/249297370#comment8).
    return Util.MANUFACTURER.equals("Google") && Build.ID.startsWith("TP1A");
  }

  @RequiresNonNull("#1.sampleMimeType")
  private static ExportException createExportException(Format format, String reason) {
    return ExportException.createForCodec(
        new IllegalArgumentException(reason),
        ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        MimeTypes.isVideo(format.sampleMimeType),
        /* isDecoder= */ true,
        format);
  }

  private static String getMediaCodecNameForDecoding(Format format)
      throws MediaCodecUtil.DecoderQueryException, ExportException {
    List<MediaCodecInfo> decoderInfos =
        MediaCodecUtil.getDecoderInfosSortedByFormatSupport(
            MediaCodecUtil.getDecoderInfos(
                checkNotNull(format.sampleMimeType), /* secure= */ false, /* tunneling= */ false),
            format);
    if (decoderInfos.isEmpty()) {
      throw createExportException(format, /* reason= */ "No decoders for format");
    }
    return decoderInfos.get(0).name;
  }
}
