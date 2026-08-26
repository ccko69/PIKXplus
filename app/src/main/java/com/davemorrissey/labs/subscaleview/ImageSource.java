package com.davemorrissey.labs.subscaleview;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable; // Import Drawable
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * Helper class used to set the source and additional attributes from a variety of sources. Supports
 * use of a bitmap, asset, resource, external file or any other URI.
 *
 * <p>When you are using a preview image, you must set the dimensions of the full size image on the
 * ImageSource object for the full size image using the {@link #dimensions(int, int)} method.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public final class ImageSource {

  static final String FILE_SCHEME = "file:///";
  static final String ASSET_SCHEME = "file:///android_asset/";

  private final Uri uri;
  private final Bitmap bitmap;
  private final Integer resource;
  private final Drawable drawable; // New field for Drawable
  private boolean tile;
  private int sWidth;
  private int sHeight;
  private Rect sRegion;
  private boolean cached;

  private ImageSource(Bitmap bitmap, boolean cached) {
    this.bitmap = bitmap;
    this.uri = null;
    this.resource = null;
    this.drawable = null; // Initialize drawable to null
    this.tile = false;
    this.sWidth = bitmap.getWidth();
    this.sHeight = bitmap.getHeight();
    this.cached = cached;
  }

  // Constructor for Drawable
  private ImageSource(@NonNull Drawable drawable) {
    this.bitmap = null;
    this.uri = null;
    this.resource = null;
    this.drawable = drawable; // Store the drawable
    this.tile = true; // Default to true, but will be managed by tilingDisabled()
    // We don't know the intrinsic dimensions of a Drawable without drawing it.
    // SubsamplingScaleImageView will determine this upon rendering or through explicit dimensions.
    // For now, we can set them to 0 or a placeholder, and rely on tilingDisabled()
    // or explicit dimensions if needed.
    this.sWidth = drawable.getIntrinsicWidth(); // Attempt to get intrinsic dimensions
    this.sHeight = drawable.getIntrinsicHeight();
    this.cached = false; // Assume not cached unless explicitly managed
  }

  private ImageSource(@NonNull Uri uri) {
    // #114 If file doesn't exist, attempt to url decode the URI and try again
    String uriString = uri.toString();
    if (uriString.startsWith(FILE_SCHEME)) {
      File uriFile = new File(uriString.substring(FILE_SCHEME.length() - 1));
      if (!uriFile.exists()) {
        try {
          uri = Uri.parse(URLDecoder.decode(uriString, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
          // Fallback to encoded URI. This exception is not expected.
        }
      }
    }
    this.bitmap = null;
    this.uri = uri;
    this.resource = null;
    this.drawable = null; // Initialize drawable to null
    this.tile = true;
  }

  private ImageSource(int resource) {
    this.bitmap = null;
    this.uri = null;
    this.resource = resource;
    this.drawable = null; // Initialize drawable to null
    this.tile = true;
  }

  /**
   * Create an instance from a resource. The correct resource for the device screen resolution will
   * be used.
   *
   * @param resId resource ID.
   * @return an {@link ImageSource} instance.
   */
  @NonNull
  public static ImageSource resource(int resId) {
    return new ImageSource(resId);
  }

  /**
   * Create an instance from an asset name.
   *
   * @param assetName asset name.
   * @return an {@link ImageSource} instance.
   */
  @NonNull
  public static ImageSource asset(@NonNull String assetName) {
    //noinspection ConstantConditions
    if (assetName == null) {
      throw new NullPointerException("Asset name must not be null");
    }
    return uri(ASSET_SCHEME + assetName);
  }

  /**
   * Create an instance from a URI. If the URI does not start with a scheme, it's assumed to be the
   * URI of a file.
   *
   * @param uri image URI.
   * @return an {@link ImageSource} instance.
   */
  @NonNull
  public static ImageSource uri(@NonNull String uri) {
    //noinspection ConstantConditions
    if (uri == null) {
      throw new NullPointerException("Uri must not be null");
    }
    if (!uri.contains("://")) {
      if (uri.startsWith("/")) {
        uri = uri.substring(1);
      }
      uri = FILE_SCHEME + uri;
    }
    return new ImageSource(Uri.parse(uri));
  }

  /**
   * Create an instance from a URI.
   *
   * @param uri image URI.
   * @return an {@link ImageSource} instance.
   */
  @NonNull
  public static ImageSource uri(@NonNull Uri uri) {
    //noinspection ConstantConditions
    if (uri == null) {
      throw new NullPointerException("Uri must not be null");
    }
    return new ImageSource(uri);
  }

  /**
   * Provide a loaded bitmap for display.
   *
   * @param bitmap bitmap to be displayed.
   * @return an {@link ImageSource} instance.
   */
  @NonNull
  public static ImageSource bitmap(@NonNull Bitmap bitmap) {
    //noinspection ConstantConditions
    if (bitmap == null) {
      throw new NullPointerException("Bitmap must not be null");
    }
    return new ImageSource(bitmap, false);
  }

  /**
   * Provide a loaded and cached bitmap for display. This bitmap will not be recycled when it is no
   * longer needed. Use this method if you loaded the bitmap with an image loader such as Picasso or
   * Volley.
   *
   * @param bitmap bitmap to be displayed.
   * @return an {@link ImageSource} instance.
   */
  @NonNull
  public static ImageSource cachedBitmap(@NonNull Bitmap bitmap) {
    //noinspection ConstantConditions
    if (bitmap == null) {
      throw new NullPointerException("Bitmap must not be null");
    }
    return new ImageSource(bitmap, true);
  }

  /**
   * Create an instance from a Drawable. This is useful for displaying animated drawables (like
   * GIFs) or other custom drawables where tiling is not desired.
   *
   * @param drawable The Drawable to display.
   * @return an {@link ImageSource} instance.
   */
  @NonNull
  public static ImageSource drawable(@NonNull Drawable drawable) {
    //noinspection ConstantConditions
    if (drawable == null) {
      throw new NullPointerException("Drawable must not be null");
    }
    return new ImageSource(drawable);
  }

  /**
   * Enable tiling of the image. This does not apply to preview images which are always loaded as a
   * single bitmap., and tiling cannot be disabled when displaying a region of the source image.
   *
   * @return this instance for chaining.
   */
  @NonNull
  public ImageSource tilingEnabled() {
    return tiling(true);
  }

  /**
   * Disable tiling of the image. This does not apply to preview images which are always loaded as a
   * single bitmap, and tiling cannot be disabled when displaying a region of the source image.
   *
   * @return this instance for chaining.
   */
  @NonNull
  public ImageSource tilingDisabled() {
    return tiling(false);
  }

  /**
   * Enable or disable tiling of the image. This does not apply to preview images which are always
   * loaded as a single bitmap, and tiling cannot be disabled when displaying a region of the source
   * image.
   *
   * @param tile whether tiling should be enabled.
   * @return this instance for chaining.
   */
  @NonNull
  public ImageSource tiling(boolean tile) {
    this.tile = tile;
    return this;
  }

  /**
   * Use a region of the source image. Region must be set independently for the full size image and
   * the preview if you are using one.
   *
   * @param sRegion the region of the source image to be displayed.
   * @return this instance for chaining.
   */
  @NonNull
  public ImageSource region(Rect sRegion) {
    this.sRegion = sRegion;
    setInvariants();
    return this;
  }

  /**
   * Declare the dimensions of the image. This is only required for a full size image, when you are
   * specifying a URI and also a preview image. When displaying a bitmap object, or not using a
   * preview, you do not need to declare the image dimensions. Note if the declared dimensions are
   * found to be incorrect, the view will reset.
   *
   * @param sWidth width of the source image.
   * @param sHeight height of the source image.
   * @return this instance for chaining.
   */
  @NonNull
  public ImageSource dimensions(int sWidth, int sHeight) {
    if (bitmap == null && drawable == null) { // Also check if it's not a drawable source
      this.sWidth = sWidth;
      this.sHeight = sHeight;
    }
    // If it's a drawable source, we prefer its intrinsic dimensions or what's set by
    // tilingDisabled/Enabled
    setInvariants();
    return this;
  }

  private void setInvariants() {
    if (this.sRegion != null) {
      this.tile = true;
      this.sWidth = this.sRegion.width();
      this.sHeight = this.sRegion.height();
    }
    // When using a drawable, tiling should generally be false and dimensions
    // should be derived from the drawable itself unless explicitly overridden.
    // The tiling(false) call in the drawable factory method handles this.
    // If dimensions are explicitly set after creating from a drawable, they will override
    // intrinsic.
  }

  protected final Uri getUri() {
    return uri;
  }

  protected final Bitmap getBitmap() {
    return bitmap;
  }

  protected final Integer getResource() {
    return resource;
  }

  protected final Drawable getDrawable() { // Getter for Drawable
    return drawable;
  }

  protected final boolean getTile() {
    return tile;
  }

  protected final int getSWidth() {
    return sWidth;
  }

  protected final int getSHeight() {
    return sHeight;
  }

  protected final Rect getSRegion() {
    return sRegion;
  }

  protected final boolean isCached() {
    return cached;
  }
}
