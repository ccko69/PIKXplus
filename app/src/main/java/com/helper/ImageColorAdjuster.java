package com.helper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import java.lang.ref.WeakReference;

/**
 * Color adjustment controller for ImageView. Maintains state so individual adjustments don't
 * overwrite each other.
 *
 * <p>Usage: ImageColorAdjuster adjuster = new ImageColorAdjuster(imageView);
 * adjuster.setBrightness(0.2f); // Adjust brightness adjuster.setContrast(1.3f); // Contrast is
 * preserved adjuster.reset(); // Clear all
 */
public class ImageColorAdjuster {

  /*
      how to use:

      // In Activity/Fragment:
    ImageColorAdjuster colorAdjuster = new ImageColorAdjuster(imageView);

    // User moves brightness slider:
    brightnessSlider.setOnSeekBarChangeListener(new SeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            float value = (progress - 50) / 50f;  // -1.0 to 1.0
            colorAdjuster.setBrightness(value);
        }
    });

    // Auto-enhance button:
    autoEnhanceBtn.setOnClickListener(v -> colorAdjuster.autoEnhance());

    // Reset button:
    resetBtn.setOnClickListener(v -> colorAdjuster.reset());

    // Optional: Track changes
    colorAdjuster.setOnAdjustmentChangeListener((b, c, s) -> {
        Log.d("ColorAdjuster", "Brightness: " + b + ", Contrast: " + c);
    });
  */

  private final WeakReference<ImageView> imageViewRef;
  private final WeakReference<SubsamplingScaleImageView> ssivRef;

  private float brightness = 0f; // -1..1
  private float contrast = 1f; // 0..2
  private float saturation = 1f; // 0..2

  private OnAdjustmentChangeListener listener;

  @Nullable private ValueAnimator currentAnimator; // NEW: holds running animation
  // Constructor for ImageView
  public ImageColorAdjuster(@NonNull ImageView imageView) {
    this.imageViewRef = new WeakReference<>(imageView);
    this.ssivRef = new WeakReference<>(null);
  }
  // Constructor for SubsamplingScaleImageView
  public ImageColorAdjuster(@NonNull SubsamplingScaleImageView ssiv) {
    this.ssivRef = new WeakReference<>(ssiv);
    this.imageViewRef = new WeakReference<>(null);
  }
  // ----- Individual Adjustments -------
  public void setBrightness(float brightness) {
    cancelAnimation();
    this.brightness = clamp(brightness, -1f, 1f);
    applyAdjustments();
    notifyListener();
  }
  public void setContrast(float contrast) {
    cancelAnimation();
    this.contrast = clamp(contrast, 0f, 2f);
    applyAdjustments();
    notifyListener();
  }
  public void setSaturation(float saturation) {
    cancelAnimation();
    this.saturation = clamp(saturation, 0f, 2f);
    applyAdjustments();
    notifyListener();
  }
  public void adjustBrightness(float delta) {
    setBrightness(brightness + delta);
  }
  public void adjustContrast(float delta) {
    setContrast(contrast + delta);
  }
  public void adjustSaturation(float delta) {
    setSaturation(saturation + delta);
  }
  public void autoEnhance() { // NEW: Animated autoEnhance with crossfade
    autoEnhance(350); // default 350ms
  }

  public void autoEnhance(long durationMs) {
    cancelAnimation();

    final float startBrightness = this.brightness;
    final float startContrast = this.contrast;
    final float startSaturation = this.saturation;

    final float targetBrightness = 0.03f;
    final float targetContrast = 1.25f;
    final float targetSaturation = 1.20f;

    currentAnimator = ValueAnimator.ofFloat(0f, 1f);
    currentAnimator.setDuration(durationMs);
    currentAnimator.setInterpolator(new DecelerateInterpolator(1.5f));

    currentAnimator.addUpdateListener(
        anim -> {
          float t = anim.getAnimatedFraction(); // 0.0 → 1.0

          this.brightness = startBrightness + (targetBrightness - startBrightness) * t;
          this.contrast = startContrast + (targetContrast - startContrast) * t;
          this.saturation = startSaturation + (targetSaturation - startSaturation) * t;

          applyAdjustments();
        });

    currentAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            currentAnimator = null;
            notifyListener();
          }
        });

    currentAnimator.start();
  }
  
  public void reset() { // NEW: Animated reset
    reset(250);
  }

  public void reset(long durationMs) {
    cancelAnimation();

    if (!hasAdjustments()) {
      // nothing to animate
      applyAdjustments();
      notifyListener();
      return;
    }

    final float startBrightness = this.brightness;
    final float startContrast = this.contrast;
    final float startSaturation = this.saturation;

    currentAnimator = ValueAnimator.ofFloat(0f, 1f);
    currentAnimator.setDuration(durationMs);
    currentAnimator.setInterpolator(new DecelerateInterpolator(1.5f));

    currentAnimator.addUpdateListener(
        anim -> {
          float t = anim.getAnimatedFraction();

          this.brightness = startBrightness + (0f - startBrightness) * t;
          this.contrast = startContrast + (1f - startContrast) * t;
          this.saturation = startSaturation + (1f - startSaturation) * t;

          applyAdjustments();
        });

    currentAnimator.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            // hard-clear filter to avoid float drift
            ImageView iv = imageViewRef.get();
            if (iv != null) {
              iv.setColorFilter(null);
            } else {
              SubsamplingScaleImageView ssiv = ssivRef.get();
              if (ssiv != null) ssiv.setColorFilter(null);
            }
            currentAnimator = null;
            notifyListener();
          }
        });

    currentAnimator.start();
  }
  // --- NEW: cancel running animation before slider changes ---
  private void cancelAnimation() {
    if (currentAnimator != null) {
      currentAnimator.cancel();
      currentAnimator = null;
    }
  }
  public float getBrightness() {
    return brightness;
  }
  public float getContrast() {
    return contrast;
  }
  public float getSaturation() {
    return saturation;
  }
  public boolean hasAdjustments() {
    return brightness != 0f || contrast != 1f || saturation != 1f;
  }
  public void setOnAdjustmentChangeListener(OnAdjustmentChangeListener listener) {
    this.listener = listener;
  }
  private void notifyListener() {
    if (listener != null) listener.onAdjustmentChanged(brightness, contrast, saturation);
  }
  public interface OnAdjustmentChangeListener {
    void onAdjustmentChanged(float brightness, float contrast, float saturation);
  }
  // ----- Private Implementation ----------------------------
  private void applyAdjustments() {
    ImageView iv = imageViewRef.get();
    SubsamplingScaleImageView ssiv = ssivRef.get();

    if (iv == null && ssiv == null) return;

    if (!hasAdjustments()) {
      if (iv != null) iv.setColorFilter(null);
      else if (ssiv != null) ssiv.setColorFilter(null);
      return;
    }

    ColorMatrix cm = new ColorMatrix();
    cm.setSaturation(saturation);

    float b = brightness * 255f;
    ColorMatrix brightnessMx =
        new ColorMatrix(
            new float[] {
              1, 0, 0, 0, b,
              0, 1, 0, 0, b,
              0, 0, 1, 0, b,
              0, 0, 0, 1, 0
            });
    cm.postConcat(brightnessMx);

    float scale = contrast;
    float translate = (1f - contrast) * 127.5f;
    ColorMatrix contrastMx =
        new ColorMatrix(
            new float[] {
              scale, 0, 0, 0, translate, 0, scale, 0, 0, translate, 0, 0, scale, 0, translate, 0, 0,
              0, 1, 0
            });
    cm.postConcat(contrastMx);

    ColorMatrixColorFilter filter = new ColorMatrixColorFilter(cm);
    if (iv != null) {
      iv.setColorFilter(filter);
    } else if (ssiv != null) {
      ssiv.setColorFilter(filter);
    }
  }

  private float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(value, max));
  }
  public void release() {
    cancelAnimation();
    imageViewRef.clear();
    ssivRef.clear();
    listener = null;
  }
}
