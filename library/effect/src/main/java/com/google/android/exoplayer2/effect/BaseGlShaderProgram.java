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
package com.readwise.android.exoplayer2.effect;

import static com.readwise.android.exoplayer2.util.Assertions.checkState;

import androidx.annotation.CallSuper;
import com.readwise.android.exoplayer2.util.GlObjectsProvider;
import com.readwise.android.exoplayer2.util.GlTextureInfo;
import com.readwise.android.exoplayer2.util.GlUtil;
import com.readwise.android.exoplayer2.util.Size;
import com.readwise.android.exoplayer2.util.VideoFrameProcessingException;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;

/**
 * A base implementation of {@link GlShaderProgram}.
 *
 * <p>{@code BaseGlShaderProgram} manages an output texture pool, whose size is configurable on
 * construction. An implementation should manage a GLSL shader program for processing frames.
 * Override {@link #drawFrame} to customize drawing. Implementations generally copy input pixels
 * into an output frame, with changes to pixels specific to the implementation.
 *
 * <p>{@code BaseShaderProgram} implementations can produce any number of output frames per input
 * frame with the same presentation timestamp. {@link SingleFrameGlShaderProgram} can be used to
 * implement a {@link GlShaderProgram} that produces exactly one output frame per input frame.
 *
 * <p>All methods in this class must be called on the thread that owns the OpenGL context.
 */
public abstract class BaseGlShaderProgram implements GlShaderProgram {

  private final ArrayDeque<GlTextureInfo> freeOutputTextures;
  private final ArrayDeque<GlTextureInfo> inUseOutputTextures;
  private final int texturePoolCapacity;
  private final boolean useHdr;

  private GlObjectsProvider glObjectsProvider;
  protected InputListener inputListener;
  private OutputListener outputListener;
  private ErrorListener errorListener;
  private Executor errorListenerExecutor;
  private boolean frameProcessingStarted;

  /**
   * Creates a {@code BaseGlShaderProgram} instance.
   *
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @param texturePoolCapacity The capacity of the texture pool. For example, if implementing a
   *     texture cache, the size should be the number of textures to cache.
   */
  public BaseGlShaderProgram(boolean useHdr, int texturePoolCapacity) {
    freeOutputTextures = new ArrayDeque<>(texturePoolCapacity);
    inUseOutputTextures = new ArrayDeque<>(texturePoolCapacity);
    this.useHdr = useHdr;
    this.texturePoolCapacity = texturePoolCapacity;
    glObjectsProvider = GlObjectsProvider.DEFAULT;
    inputListener = new InputListener() {};
    outputListener = new OutputListener() {};
    errorListener = (frameProcessingException) -> {};
    errorListenerExecutor = MoreExecutors.directExecutor();
  }

  /**
   * Configures the instance based on the input dimensions.
   *
   * <p>This method must be called before {@linkplain #drawFrame(int,long) drawing} the first frame
   * and before drawing subsequent frames with different input dimensions.
   *
   * @param inputWidth The input width, in pixels.
   * @param inputHeight The input height, in pixels.
   * @return The output width and height of frames processed through {@link #drawFrame(int, long)}.
   * @throws VideoFrameProcessingException If an error occurs while configuring.
   */
  public abstract Size configure(int inputWidth, int inputHeight)
      throws VideoFrameProcessingException;

  /**
   * Draws one frame.
   *
   * <p>This method may only be called after the shader program has been {@link #configure(int, int)
   * configured}. The caller is responsible for focussing the correct render target before calling
   * this method.
   *
   * <p>A minimal implementation should tell OpenGL to use its shader program, bind the shader
   * program's vertex attributes and uniforms, and issue a drawing command.
   *
   * @param inputTexId Identifier of a 2D OpenGL texture containing the input frame.
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   * @throws VideoFrameProcessingException If an error occurs while processing or drawing the frame.
   */
  public abstract void drawFrame(int inputTexId, long presentationTimeUs)
      throws VideoFrameProcessingException;

  @Override
  public void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    int numberOfFreeFramesToNotify;
    if (getIteratorToAllTextures().hasNext()) {
      // The frame buffers have already been allocated.
      numberOfFreeFramesToNotify = freeOutputTextures.size();
    } else {
      // Defer frame buffer allocation to when queueing input frames.
      numberOfFreeFramesToNotify = texturePoolCapacity;
    }
    for (int i = 0; i < numberOfFreeFramesToNotify; i++) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  public void setOutputListener(OutputListener outputListener) {
    this.outputListener = outputListener;
  }

  @Override
  public void setErrorListener(Executor errorListenerExecutor, ErrorListener errorListener) {
    this.errorListenerExecutor = errorListenerExecutor;
    this.errorListener = errorListener;
  }

  @Override
  public void setGlObjectsProvider(GlObjectsProvider glObjectsProvider) {
    checkState(
        !frameProcessingStarted,
        "The GlObjectsProvider cannot be set after frame processing has started.");
    this.glObjectsProvider = glObjectsProvider;
  }

  @Override
  public void queueInputFrame(GlTextureInfo inputTexture, long presentationTimeUs) {
    try {
      configureAllOutputTextures(inputTexture.width, inputTexture.height);
      checkState(
          !freeOutputTextures.isEmpty(),
          "The GlShaderProgram does not currently accept input frames. Release prior output frames"
              + " first.");
      frameProcessingStarted = true;

      // Focus on the next free buffer.
      GlTextureInfo outputTexture = freeOutputTextures.remove();
      inUseOutputTextures.add(outputTexture);

      // Copy frame to fbo.
      GlUtil.focusFramebufferUsingCurrentContext(
          outputTexture.fboId, outputTexture.width, outputTexture.height);
      GlUtil.clearOutputFrame();
      drawFrame(inputTexture.texId, presentationTimeUs);
      inputListener.onInputFrameProcessed(inputTexture);
      outputListener.onOutputFrameAvailable(outputTexture, presentationTimeUs);
    } catch (VideoFrameProcessingException | GlUtil.GlException | NoSuchElementException e) {
      errorListenerExecutor.execute(
          () -> errorListener.onError(VideoFrameProcessingException.from(e)));
    }
  }

  @Override
  public void releaseOutputFrame(GlTextureInfo outputTexture) {
    frameProcessingStarted = true;
    checkState(inUseOutputTextures.contains(outputTexture));
    inUseOutputTextures.remove(outputTexture);
    freeOutputTextures.add(outputTexture);
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    frameProcessingStarted = true;
    outputListener.onCurrentOutputStreamEnded();
  }

  @Override
  @CallSuper
  public void flush() {
    frameProcessingStarted = true;
    freeOutputTextures.addAll(inUseOutputTextures);
    inUseOutputTextures.clear();
    inputListener.onFlush();
    for (int i = 0; i < freeOutputTextures.size(); i++) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  @CallSuper
  public void release() throws VideoFrameProcessingException {
    frameProcessingStarted = true;
    try {
      deleteAllOutputTextures();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  private void configureAllOutputTextures(int inputWidth, int inputHeight)
      throws GlUtil.GlException, VideoFrameProcessingException {
    Iterator<GlTextureInfo> allTextures = getIteratorToAllTextures();
    if (!allTextures.hasNext()) {
      createAllOutputTextures(inputWidth, inputHeight);
      return;
    }
    GlTextureInfo outputGlTextureInfo = allTextures.next();
    if (outputGlTextureInfo.width != inputWidth || outputGlTextureInfo.height != inputHeight) {
      deleteAllOutputTextures();
      createAllOutputTextures(inputWidth, inputHeight);
    }
  }

  private void createAllOutputTextures(int width, int height)
      throws GlUtil.GlException, VideoFrameProcessingException {
    checkState(freeOutputTextures.isEmpty());
    checkState(inUseOutputTextures.isEmpty());
    Size outputSize = configure(width, height);
    for (int i = 0; i < texturePoolCapacity; i++) {
      int outputTexId = GlUtil.createTexture(outputSize.getWidth(), outputSize.getHeight(), useHdr);
      GlTextureInfo outputTexture =
          glObjectsProvider.createBuffersForTexture(
              outputTexId, outputSize.getWidth(), outputSize.getHeight());
      freeOutputTextures.add(outputTexture);
    }
  }

  private void deleteAllOutputTextures() throws GlUtil.GlException {
    Iterator<GlTextureInfo> allTextures = getIteratorToAllTextures();
    while (allTextures.hasNext()) {
      GlTextureInfo textureInfo = allTextures.next();
      GlUtil.deleteTexture(textureInfo.texId);
      GlUtil.deleteFbo(textureInfo.fboId);
    }
    freeOutputTextures.clear();
    inUseOutputTextures.clear();
  }

  private Iterator<GlTextureInfo> getIteratorToAllTextures() {
    return Iterables.concat(freeOutputTextures, inUseOutputTextures).iterator();
  }
}
