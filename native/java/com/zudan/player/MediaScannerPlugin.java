package com.zudan.player;

import android.Manifest;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * MediaScannerPlugin
 * ---------------------------------------------------------------------
 * Requests device media permission ONCE, then queries the system
 * MediaStore for every audio + video file on the device and returns
 * them all in a single call. This is what lets the web UI auto-fill
 * the whole queue instead of the user picking files one by one.
 */
@CapacitorPlugin(
    name = "MediaScanner",
    permissions = {
        @Permission(
            alias = "media",
            strings = {
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO
            }
        ),
        @Permission(
            alias = "mediaLegacy",
            strings = { Manifest.permission.READ_EXTERNAL_STORAGE }
        )
    }
)
public class MediaScannerPlugin extends Plugin {

    private String permissionAliasForSdk() {
        return Build.VERSION.SDK_INT >= 33 ? "media" : "mediaLegacy";
    }

    @PluginMethod
    public void requestAndScan(PluginCall call) {
        String alias = permissionAliasForSdk();
        if (getPermissionState(alias) != com.getcapacitor.PermissionState.GRANTED) {
            requestPermissionForAlias(alias, call, "scanCallback");
        } else {
            scanAll(call);
        }
    }

    @PermissionCallback
    private void scanCallback(PluginCall call) {
        String alias = permissionAliasForSdk();
        if (getPermissionState(alias) == com.getcapacitor.PermissionState.GRANTED) {
            scanAll(call);
        } else {
            call.reject("Media permission was denied. The queue can't be auto-filled without it.");
        }
    }

    /** Queries MediaStore.Audio and MediaStore.Video and returns a combined JSArray. */
    private void scanAll(PluginCall call) {
        JSArray flat = new JSArray();
        appendCollection(
            flat,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            "audio",
            new String[] {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE
            }
        );
        appendCollection(
            flat,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            "video",
            new String[] {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.ARTIST,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE
            }
        );

        JSObject ret = new JSObject();
        ret.put("items", flat);
        ret.put("count", flat.length());
        call.resolve(ret);
    }

    /** Appends every row of one MediaStore collection (audio or video) straight into `flat`. */
    private void appendCollection(JSArray flat, Uri collectionUri, String kind, String[] projection) {
        Cursor cursor = getContext().getContentResolver().query(
            collectionUri, projection, null, null,
            MediaStore.MediaColumns.TITLE + " ASC"
        );
        if (cursor == null) return;

        int idCol = cursor.getColumnIndexOrThrow(projection[0]);
        int titleCol = cursor.getColumnIndexOrThrow(projection[1]);
        int artistCol = cursor.getColumnIndex(projection[2]);
        int durationCol = cursor.getColumnIndex(projection[3]);
        int sizeCol = cursor.getColumnIndex(projection[4]);
        int mimeCol = cursor.getColumnIndex(projection[5]);

        while (cursor.moveToNext()) {
            long id = cursor.getLong(idCol);
            Uri contentUri = Uri.withAppendedPath(collectionUri, String.valueOf(id));

            JSObject item = new JSObject();
            item.put("id", kind + "_" + id);
            item.put("kind", kind);
            item.put("title", cursor.getString(titleCol));
            item.put("artist", artistCol >= 0 ? cursor.getString(artistCol) : "");
            item.put("durationMs", durationCol >= 0 ? cursor.getLong(durationCol) : 0);
            item.put("size", sizeCol >= 0 ? cursor.getLong(sizeCol) : 0);
            item.put("mimeType", mimeCol >= 0 ? cursor.getString(mimeCol) : "");
            // content:// URI — playable directly as a <video>/<audio> src inside the WebView
            item.put("uri", contentUri.toString());
            flat.put(item);
        }
        cursor.close();
    }
    }
