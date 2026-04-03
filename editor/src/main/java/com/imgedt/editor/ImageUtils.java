package com.imgedt.editor;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.InputStream;

/**
 * Shared image loading utilities.
 */
public final class ImageUtils {

    private ImageUtils() {}

    /**
     * Decode a bitmap from a content URI and apply EXIF orientation correction.
     * Returns null on failure.
     */
    public static Bitmap decodeBitmapWithOrientation(ContentResolver resolver, Uri uri) {
        // 1. Read EXIF orientation
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try {
            InputStream exifStream = resolver.openInputStream(uri);
            if (exifStream != null) {
                ExifInterface exif = new ExifInterface(exifStream);
                orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
                exifStream.close();
            }
        } catch (Exception ignored) {}

        // 2. Decode bitmap
        Bitmap bitmap;
        try {
            InputStream bitmapStream = resolver.openInputStream(uri);
            if (bitmapStream == null) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bitmap = BitmapFactory.decodeStream(bitmapStream, null, opts);
            bitmapStream.close();
        } catch (Exception e) {
            return null;
        }
        if (bitmap == null) return null;

        // 3. Apply rotation/flip based on EXIF
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270);
                matrix.postScale(-1, 1);
                break;
            default:
                return bitmap;
        }

        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }
}
