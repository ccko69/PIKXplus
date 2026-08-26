package com.helper;

import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.lang.ref.WeakReference;

/**
 * A self‑contained controller for managing an ImageView's matrix transformations (scale, pan,
 * rotation) with smooth animations, clamping, and configurable double‑tap behavior.
 *
 * <p>Usage:
 *
 * <pre>
 * ImageMatrixController controller = new ImageMatrixController(imageView);
 * controller.setMaxScale(4.0f, ScaleReference.VIEW); // max scale = 4x view size
 * controller.onDoubleTap(tapX, tapY, DoubleTapBehavior.CENTER_ON_POINT);
 * </pre>
 */
public class ImageMatrixController {

  // -------------------------------------------------------------------------
  // Public enums / constants
  // -------------------------------------------------------------------------

  /** Determines how a double‑tap is interpreted. */
  public enum DoubleTapBehavior {
    /** Zoom in/out keeping the tapped point fixed under the finger. */
    ZOOM_AT_POINT,
    /** Zoom in/out and move the tapped point to the center of the view. */
    CENTER_ON_POINT
  }

  /** Reference point for interpreting min/max scale values. */
  public enum ScaleReference {
    /** Scale is relative to the drawable's intrinsic dimensions. */
    DRAWABLE,
    /** Scale is relative to the view's dimensions (width/height). */
    VIEW
  }

  // -------------------------------------------------------------------------
  // Fields
  // -------------------------------------------------------------------------

  private final WeakReference<ImageView> imageViewRef;
  private final Matrix currentMatrix = new Matrix();
  private float currentScale = 1.0f; // uniform scale (assumes uniform scaling)

  // Initial fit scale (the scale that makes the drawable fit inside the view)
  private float fitScale = 1.0f;
  private float fillScale = 1.0f;

  // Scale limits
  private float minScale = 1.0f;
  private float minScaleTemp = 0.7f;
  private float maxScale = 20f;
  private float doubleTapExtraScale = 0f;
  private float currentRotation = 0f; // degrees

  private boolean horizontalMirror = false;
  private boolean verticalMirror = false;

  private ScaleReference minScaleRef = ScaleReference.VIEW;
  private ScaleReference maxScaleRef = ScaleReference.VIEW;

  private float gestureMinScale = -1f; // -1 means not active
  private ScaleReference gestureMinScaleRef = ScaleReference.VIEW;

  // Animation defaults
  private long defaultAnimDuration = 800L;
  private Interpolator defaultInterpolator = new AccelerateInterpolator();

  // Currently running animator (cancelled before starting a new one)
  @Nullable private ValueAnimator currentAnimator;

  // Optional listener for matrix changes
  @Nullable private OnMatrixChangeListener matrixChangeListener;

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  /**
   * Creates a controller for the given ImageView.
   *
   * <p>The ImageView's scale type will be set to MATRIX automatically. Call {@link
   * #resetToFit(boolean)} after the view has been laid out (or use a layout listener) to apply the
   * initial fit matrix.
   */
  public ImageMatrixController(@NonNull ImageView imageView) {
    this.imageViewRef = new WeakReference<>(imageView);
    imageView.setScaleType(ImageView.ScaleType.MATRIX);

    // Inside the ImageMatrixController constructor:
    imageView.addOnLayoutChangeListener(
        new View.OnLayoutChangeListener() {
          @Override
          public void onLayoutChange(
              View v,
              int left,
              int top,
              int right,
              int bottom,
              int oldLeft,
              int oldTop,
              int oldRight,
              int oldBottom) {
            int newW = right - left;
            int newH = bottom - top;
            int oldW = oldRight - oldLeft;
            int oldH = oldBottom - oldTop;

            // If the view actually changed size (e.g., rotation) and is fully laid out
            if (newW > 0 && newH > 0 && (newW != oldW || newH != oldH)) {
              // Recalculate the fit matrix with the NEW dimensions instantly
              Matrix targetMatrix = computeFitMatrix();
              clampMatrixForMatrix(targetMatrix);
              cancelRunningAnimation();
              applyMatrix(targetMatrix, fitScale);
            }
          }
        });
  }

  // -------------------------------------------------------------------------
  // Public API – Configuration
  // -------------------------------------------------------------------------

  /** Sets the minimum allowed scale. */
  public void setMinScale(float minScale, @NonNull ScaleReference reference) {
    this.minScale = minScale;
    this.minScaleRef = reference;
    // Optionally re-clamp current scale immediately
    clampAndApply();
  }

  /** Sets the maximum allowed scale. */
  public void setMaxScale(float maxScale, @NonNull ScaleReference reference) {
    this.maxScale = maxScale;
    this.maxScaleRef = reference;
    clampAndApply();
  }

  /** Sets default animation duration (used when not specified). */
  public void setDefaultAnimDuration(long durationMs) {
    this.defaultAnimDuration = durationMs;
  }

  /**
   * Temporarily overrides the minimum scale during a gesture. Call {@link #clearGestureMinScale()}
   * after the gesture ends.
   */
  public void setGestureMinScale(float minScale, @NonNull ScaleReference reference) {
    this.gestureMinScale = minScale;
    this.gestureMinScaleRef = reference;
  }

  public void clearGestureMinScale() {
    this.gestureMinScale = -1f;
  }

  /** Sets default interpolator for animations. */
  public void setDefaultInterpolator(@NonNull Interpolator interpolator) {
    this.defaultInterpolator = interpolator;
  }

  /** Registers a listener to be notified whenever the matrix changes. */
  public void setOnMatrixChangeListener(@Nullable OnMatrixChangeListener listener) {
    this.matrixChangeListener = listener;
  }

  public boolean isHorizontallyMirrored() {
    return horizontalMirror;
  }

  public boolean isVerticallyMirrored() {
    return verticalMirror;
  }

  // -------------------------------------------------------------------------
  // Public API – Actions
  // -------------------------------------------------------------------------

  /**
   * Resets the image to fit inside the view (centered).
   *
   * @param animate if true, animates to the fit matrix; otherwise applies instantly.
   */
  public void resetToFit(boolean animate) {
    resetToFit(animate, defaultAnimDuration, defaultInterpolator);
  }

  /** Resets to fit with custom animation parameters. */
  public void resetToFit(boolean animate, long duration, @Nullable Interpolator interpolator) {
    ImageView iv = imageViewRef.get();
    if (iv == null) return;

    Matrix targetMatrix = computeFitMatrix();
    float targetScale = fitScale;

    clampMatrixForMatrix(targetMatrix);

    if (animate) {
      animateTo(
          targetMatrix,
          targetScale,
          duration,
          interpolator != null ? interpolator : defaultInterpolator);
    } else {
      cancelRunningAnimation();
      applyMatrix(targetMatrix, targetScale);
    }
  }
  /**
   * Zooms to a specific scale around a given focus point (view coordinates). The scale is clamped
   * to [minScale, maxScale] after conversion.
   */
  public void zoomTo(float scale, float focusX, float focusY, boolean animate) {
    zoomTo(scale, focusX, focusY, animate, defaultAnimDuration, defaultInterpolator);
  }

  public void zoomTo(
      float scale,
      float focusX,
      float focusY,
      boolean animate,
      long duration,
      @Nullable Interpolator interpolator) {
    ImageView iv = imageViewRef.get();
    if (iv == null) return;

    scale = clampScale(scale);

    Matrix target = new Matrix(currentMatrix);
    target.postScale(scale / currentScale, scale / currentScale, focusX, focusY);

    // Ensure the resulting matrix respects bounds
    clampMatrixForMatrix(target);

    if (animate) {
      animateTo(target, scale, duration, interpolator != null ? interpolator : defaultInterpolator);
    } else {
      cancelRunningAnimation();
      applyMatrix(target, scale);
    }
  }

  /** Pans the image by the given deltas (in view pixels). Clamping is applied after the pan. */
  public void panBy(float dx, float dy) {
    ImageView iv = imageViewRef.get();
    if (iv == null) return;

    currentMatrix.postTranslate(dx, dy);
    clampMatrixForMatrix(currentMatrix);
    // Recalculate current scale from the matrix
    float[] vals = new float[9];
    currentMatrix.getValues(vals);
    currentScale = (float) Math.hypot(vals[Matrix.MSCALE_X], vals[Matrix.MSKEW_Y]);

    applyMatrix(currentMatrix, currentScale);
  }

  /**
   * Mirrors the image horizontally (flips left-right). Resets to fit-centered like rotation does.
   */
  public void mirrorHorizontal(boolean animate) {
    mirrorHorizontal(animate, defaultAnimDuration, defaultInterpolator);
  }

  public void mirrorHorizontal(
      boolean animate, long duration, @Nullable Interpolator interpolator) {
    ImageView iv = imageViewRef.get();
    if (iv == null) return;

    // Toggle mirror state
    horizontalMirror = !horizontalMirror;

    // Get fit-centered matrix with new mirror state
    Matrix targetMatrix = computeFitMatrix();
    float targetScale = fitScale;

    clampMatrixForMatrix(targetMatrix);

    if (animate) {
      animateTo(
          targetMatrix,
          targetScale,
          duration,
          interpolator != null ? interpolator : defaultInterpolator);
    } else {
      cancelRunningAnimation();
      applyMatrix(targetMatrix, targetScale);
    }
  }

  /** Mirrors the image vertically (flips top-bottom). Resets to fit-centered like rotation does. */
  public void mirrorVertical(boolean animate) {
    mirrorVertical(animate, defaultAnimDuration, defaultInterpolator);
  }

  public void mirrorVertical(boolean animate, long duration, @Nullable Interpolator interpolator) {
    ImageView iv = imageViewRef.get();
    if (iv == null) return;

    // Toggle mirror state
    verticalMirror = !verticalMirror;

    // Get fit-centered matrix with new mirror state
    Matrix targetMatrix = computeFitMatrix();
    float targetScale = fitScale;

    clampMatrixForMatrix(targetMatrix);

    if (animate) {
      animateTo(
          targetMatrix,
          targetScale,
          duration,
          interpolator != null ? interpolator : defaultInterpolator);
    } else {
      cancelRunningAnimation();
      applyMatrix(targetMatrix, targetScale);
    }
  }

  /**
   * Rotates the image by the given angle (in degrees) around its current center. Not animated by
   * default; call {@link #rotateBy(float, boolean)} for animation.
   */
  public void rotateBy(float degrees) {
    rotateBy(degrees, false);
  }

  public void rotateBy(float degrees, boolean animate) {
    rotateBy(degrees, animate, defaultAnimDuration, defaultInterpolator);
  }

  public void rotateBy(
      float degrees, boolean animate, long duration, @Nullable Interpolator interpolator) {
    ImageView iv = imageViewRef.get();
    if (iv == null) return;
    // Update cumulative rotation
    currentRotation = (currentRotation + degrees) % 360f;
    // Recompute fit matrix with new rotation
    Matrix targetMatrix = computeFitMatrix();
    RectF rect = getDisplayRect(targetMatrix);
    if (targetMatrix == null) return;
    float pivotX = rect.centerX();
    float pivotY = rect.centerY();

    targetMatrix.postRotate(degrees, pivotX, pivotY);
    clampMatrixForMatrix(targetMatrix);
    float targetScale = currentScale;

    if (animate) {
      animateTo(
          targetMatrix,
          targetScale,
          duration,
          interpolator != null ? interpolator : defaultInterpolator);
    } else {
      cancelRunningAnimation();
      applyMatrix(targetMatrix, targetScale);
    }
  }

  /**
   * Handles a double‑tap gesture at the given view coordinates.
   *
   * @param tapX X coordinate of the tap (relative to ImageView)
   * @param tapY Y coordinate of the tap
   * @param behavior desired zoom behavior
   */
  public void onDoubleTap(float tapX, float tapY, @NonNull DoubleTapBehavior behavior) {
    ImageView iv = imageViewRef.get();
    if (iv == null) return;

    float threshold = 0.01f;
    float targetScale;

    if (Math.abs(currentScale - fitScale) < threshold) {
      // Zoom in: use fillScale + extra, but clamped to maxScale
      float desired = fillScale + doubleTapExtraScale;
      targetScale = Math.max(fitScale, Math.min(desired, getEffectiveMaxScale()));

    } else {
      targetScale = fitScale;
    }

    Matrix targetMatrix = computeZoomMatrix(targetScale, tapX, tapY, behavior);
    clampMatrixForMatrix(targetMatrix);
    animateTo(targetMatrix, targetScale, defaultAnimDuration, defaultInterpolator);
  }

  /** Returns a copy of the current matrix. */
  @NonNull
  public Matrix getMatrix() {
    return new Matrix(currentMatrix);
  }

  /** Returns the current uniform scale. */
  public float getScale() {
    return currentScale;
  }

  /** Returns the fit scale (the scale that makes the drawable fit inside the view). */
  public float getFitScale() {
    return fitScale;
  }

  /** Returns the fill scale (the scale that makes the image cover the view). */
  public float getFillScale() {
    return fillScale;
  }

  /** Returns the current rotation angle in degrees (0-359). */
  public float getCurrentRotation() {
    return currentRotation;
  }

  /**
   * Call this from the host Activity/Fragment's onDestroy or when the view is detached to cancel
   * any running animations and prevent leaks.
   */
  public void release() {
    cancelRunningAnimation();
    imageViewRef.clear();
  }

  // -------------------------------------------------------------------------
  // Private helpers – Matrix computation
  // -------------------------------------------------------------------------
  /** Computes the matrix that fits the drawable inside the view (centered). */
  @NonNull
  private Matrix computeFitMatrix() {
    ImageView iv = imageViewRef.get();
    if (iv == null) return new Matrix();
    Drawable d = iv.getDrawable();
    if (d == null) return new Matrix();

    int dw = getDrawableWidth(d);
    int dh = getDrawableHeight(d);
    int vw = getViewContentWidth(iv);
    int vh = getViewContentHeight(iv);

    if (dw <= 0 || dh <= 0 || vw <= 0 || vh <= 0) return new Matrix();

    // Compute rotated bounding box
    Matrix rotMatrix = new Matrix();
    rotMatrix.setRotate(currentRotation, dw / 2f, dh / 2f);
    RectF rotatedBounds = new RectF(0, 0, dw, dh);
    rotMatrix.mapRect(rotatedBounds);

    // Compute fit scale
    float scale = Math.min(vw / rotatedBounds.width(), vh / rotatedBounds.height());
    fitScale = scale;
    fillScale = Math.max(vw / rotatedBounds.width(), vh / rotatedBounds.height());

    // Build matrix: rotate + mirror + scale + translate
    Matrix m = new Matrix();
    m.setRotate(currentRotation, dw / 2f, dh / 2f);

    // Apply mirroring around drawable center
    float hScale = horizontalMirror ? -1f : 1f;
    float vScale = verticalMirror ? -1f : 1f;
    m.postScale(vScale, hScale, dw / 2f, dh / 2f);

    m.postScale(scale, scale, dw / 2f, dh / 2f);

    // Translate to center of the view
    float dx = vw / 2f + iv.getPaddingLeft() - (dw / 2f) * scale;
    float dy = vh / 2f + iv.getPaddingTop() - (dh / 2f) * scale;
    m.postTranslate(dx, dy);

    return m;
  }

  /**
   * Computes a matrix that zooms to targetScale around the given focus point.
   *
   * @param targetScale desired scale (should already be clamped)
   * @param focusX focus X in view coordinates
   * @param focusY focus Y in view coordinates
   * @param behavior how the tap point should be treated
   */
  @NonNull
  private Matrix computeZoomMatrix(
      float targetScale, float focusX, float focusY, @NonNull DoubleTapBehavior behavior) {
    Matrix m = new Matrix(currentMatrix);
    float scaleFactor = targetScale / currentScale;

    // Zoom around the focus point
    m.postScale(scaleFactor, scaleFactor, focusX, focusY);

    if (behavior == DoubleTapBehavior.CENTER_ON_POINT) {
      // After zooming, the tapped point becomes center of the view.
      // The view center in current coordinate space is at (viewWidth/2, viewHeight/2).
      // We need to translate so that the mapped focus point lands at the center.
      ImageView iv = imageViewRef.get();
      if (iv != null) {
        float[] pts = new float[] {focusX, focusY};
        m.mapPoints(pts);
        // pts[0], pts[1] are where the focus point is now after zoom.
        float centerX = iv.getWidth() / 2f;
        float centerY = iv.getHeight() / 2f;
        m.postTranslate(centerX - pts[0], centerY - pts[1]);
      }
    }
    return m;
  }

  // -------------------------------------------------------------------------
  // Private helpers – Clamping & bounds
  // -------------------------------------------------------------------------

  /** Clamps the given matrix so the drawable stays within the view bounds. */
  private void clampMatrixForMatrix(@NonNull Matrix matrix) {
    ImageView iv = imageViewRef.get();
    if (iv == null) return;

    RectF rect = getDisplayRect(matrix);
    if (rect == null) return;

    int viewW = getViewContentWidth(iv);
    int viewH = getViewContentHeight(iv);
    if (viewW <= 0 || viewH <= 0) return;

    float deltaX = 0f, deltaY = 0f;

    // Horizontal clamp / center
    if (rect.width() <= viewW) {
      deltaX = (viewW - rect.width()) * 0.5f - rect.left;
    } else {
      if (rect.left > iv.getPaddingLeft()) {
        deltaX = iv.getPaddingLeft() - rect.left;
      } else if (rect.right < viewW + iv.getPaddingLeft()) {
        deltaX = viewW + iv.getPaddingLeft() - rect.right;
      }
    }

    // Vertical clamp / center
    if (rect.height() <= viewH) {
      deltaY = (viewH - rect.height()) * 0.5f - rect.top;
    } else {
      if (rect.top > iv.getPaddingTop()) {
        deltaY = iv.getPaddingTop() - rect.top;
      } else if (rect.bottom < viewH + iv.getPaddingTop()) {
        deltaY = viewH + iv.getPaddingTop() - rect.bottom;
      }
    }

    if (Math.abs(deltaX) > 0.5f || Math.abs(deltaY) > 0.5f) {
      matrix.postTranslate(deltaX, deltaY);
    }
  }

  /** Returns the bounding rectangle of the drawable after applying the given matrix. */
  @Nullable
  private RectF getDisplayRect(@NonNull Matrix matrix) {
    ImageView iv = imageViewRef.get();
    if (iv == null) return null;
    Drawable d = iv.getDrawable();
    if (d == null) return null;

    int drawableW = getDrawableWidth(d);
    int drawableH = getDrawableHeight(d);
    RectF rect = new RectF(0, 0, drawableW, drawableH);
    matrix.mapRect(rect);
    return rect;
  }

  // -------------------------------------------------------------------------
  // Private helpers – Scale clamping
  // -------------------------------------------------------------------------

  private float clampScale(float scale) {
    return Math.max(getEffectiveMinScale(), Math.min(scale, getEffectiveMaxScale()));
  }

  public float getEffectiveMinScale() {
    if (gestureMinScale > 0) {
      return convertScaleReference(gestureMinScale, gestureMinScaleRef);
    }
    return convertScaleReference(minScale, minScaleRef);
  }

  public float getEffectiveMaxScale() {
    return convertScaleReference(maxScale, maxScaleRef);
  }

  /** Converts a scale value from its reference type to an absolute scale. */
  private float convertScaleReference(float value, @NonNull ScaleReference ref) {
    ImageView iv = imageViewRef.get();
    if (iv == null) return value;

    Drawable d = iv.getDrawable();
    if (d == null) return value;

    int drawableW = getDrawableWidth(d);
    int drawableH = getDrawableHeight(d);
    int viewW = getViewContentWidth(iv);
    int viewH = getViewContentHeight(iv);

    if (drawableW <= 0 || drawableH <= 0 || viewW <= 0 || viewH <= 0) {
      return value;
    }

    switch (ref) {
      case DRAWABLE:
        // value is already relative to drawable intrinsic size
        return value;
      case VIEW:
        // Convert: scale = value * (viewMinDimension / drawableMinDimension)
        // Example: maxScale=4f VIEW means the image can be 4x the view's smaller dimension.
        float viewMinDim = Math.min(viewW, viewH);
        float drawableMinDim = Math.min(drawableW, drawableH);
        return value * (viewMinDim / drawableMinDim);
      default:
        return value;
    }
  }

  // -------------------------------------------------------------------------
  // Private helpers – Dimensions (account for padding & fallbacks)
  // -------------------------------------------------------------------------

  private int getViewContentWidth(@NonNull ImageView iv) {
    return iv.getWidth() - iv.getPaddingLeft() - iv.getPaddingRight();
  }

  private int getViewContentHeight(@NonNull ImageView iv) {
    return iv.getHeight() - iv.getPaddingTop() - iv.getPaddingBottom();
  }

  private int getDrawableWidth(@NonNull Drawable d) {
    int w = d.getIntrinsicWidth();
    if (w <= 0) w = d.getBounds().width();
    return w;
  }

  private int getDrawableHeight(@NonNull Drawable d) {
    int h = d.getIntrinsicHeight();
    if (h <= 0) h = d.getBounds().height();
    return h;
  }

  // -------------------------------------------------------------------------
  // Private helpers – Animation
  // -------------------------------------------------------------------------

  private void animateTo(
      @NonNull Matrix targetMatrix,
      float targetScale,
      long duration,
      @NonNull Interpolator interpolator) {
    cancelRunningAnimation();

    final Matrix startMatrix = new Matrix(currentMatrix);
    final float startScale = currentScale;

    ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setDuration(duration);
    animator.setInterpolator(interpolator);
    animator.addUpdateListener(
        animation -> {
          float progress = (float) animation.getAnimatedValue();

          // Interpolate matrix values (simple 9‑value lerp)
          float[] startVals = new float[9];
          float[] endVals = new float[9];
          float[] interpVals = new float[9];
          startMatrix.getValues(startVals);
          targetMatrix.getValues(endVals);
          for (int i = 0; i < 9; i++) {
            interpVals[i] = startVals[i] + progress * (endVals[i] - startVals[i]);
          }

          currentMatrix.setValues(interpVals);
          currentScale = startScale + progress * (targetScale - startScale);

          applyMatrix(currentMatrix, currentScale);
        });

    animator.addListener(
        new android.animation.AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(android.animation.Animator animation) {
            currentAnimator = null;
          }
        });

    currentAnimator = animator;
    animator.start();
  }

  private void cancelRunningAnimation() {
    if (currentAnimator != null) {
      currentAnimator.cancel();
      currentAnimator = null;
    }
  }

  // -------------------------------------------------------------------------
  // Private helpers – Apply matrix to ImageView
  // -------------------------------------------------------------------------

  private void applyMatrix(@NonNull Matrix matrix, float scale) {
    ImageView iv = imageViewRef.get();
    if (iv == null) return;

    iv.setImageMatrix(matrix);
    currentMatrix.set(matrix);
    currentScale = scale;

    if (matrixChangeListener != null) {
      matrixChangeListener.onMatrixChanged(currentMatrix, currentScale);
    }
  }

  /** Re-clamps the current matrix and applies it (useful after config changes). */
  private void clampAndApply() {
    clampMatrixForMatrix(currentMatrix);
    applyMatrix(currentMatrix, currentScale);
  }

  // -------------------------------------------------------------------------
  // Public callback interface
  // -------------------------------------------------------------------------

  public interface OnMatrixChangeListener {
    void onMatrixChanged(@NonNull Matrix matrix, float scale);
  }
}
