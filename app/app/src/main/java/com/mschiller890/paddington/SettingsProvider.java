package com.mschiller890.paddington;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Exposes the module's settings to other processes (the hook runs inside
 * System UI, which is a different UID and cannot read the module's private
 * SharedPreferences files directly).
 *
 * <p>The provider runs in the module's own process, reads its own prefs and
 * returns them over a simple {@code content://} URI.
 */
public class SettingsProvider extends ContentProvider {

    public static final String AUTHORITY = "com.mschiller890.paddington.settings";
    public static final Uri CONTENT_URI =
            Uri.parse("content://" + AUTHORITY + "/" + Hook.Settings.KEY_PADDING_DP);

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        int paddingDp = getContext().getSharedPreferences(
                        Hook.Settings.PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(Hook.Settings.KEY_PADDING_DP, Hook.Settings.DEFAULT_PADDING_DP);
        MatrixCursor cursor = new MatrixCursor(new String[]{Hook.Settings.KEY_PADDING_DP});
        cursor.addRow(new Object[]{paddingDp});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd." + AUTHORITY;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
