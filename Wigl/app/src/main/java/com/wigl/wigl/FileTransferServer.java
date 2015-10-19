package com.wigl.wigl;

import android.app.Activity;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A simple server socket that accepts connection and writes some data on the stream.
 */
public class FileTransferServer extends AsyncTask<Void, Void, String> {
    public static final int PORT = 8988;
    private static final String TAG = "FileTransferServer";
    private final Fragment fragment;
    private final Activity activity;
    private final TextView statusText;

    public FileTransferServer(Fragment fragment, View statusText) {
        this.fragment = fragment;
        this.activity = fragment.getActivity();
        this.statusText = (TextView) statusText;
    }

    @Override
    protected void onPreExecute() {
        Log.d(TAG, "Opening a ServerSocket");
        statusText.setText("Opening a ServerSocket");
    }

    /**
     * Accept incoming captureTime file from Socket, save to local disk
     */
    @Override
    protected String doInBackground(Void... params) {
        ServerSocket serverSocket = null;
        Socket client = null;
        InputStream is = null;
        OutputStream os = null;
        String result = null;
        try {
            Log.d(TAG, "Opening server socket");
            serverSocket = new ServerSocket(PORT);
            client = serverSocket.accept();
            Log.d(TAG, "Client connection open");
            final File f = new File(activity.getFilesDir(), "wiglS-" + System.currentTimeMillis());

            is = client.getInputStream();
            os = new FileOutputStream(f);
            Utils.copyFile(is, os);
            Log.d(TAG, "File copied");
            result = f.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        } finally {
            Utils.close(serverSocket);
            Utils.close(client);
            Utils.close(is);
            Utils.close(os);
            return result;
        }
    }

    /*
     * Once a file has been asynchronously transferred, do this
     */
    @Override
    protected void onPostExecute(String result) {
        if (result != null) {
            Log.d(TAG, "File on disk: " + result);
            statusText.setText("File copied: " + result);

            // read the contents of the file for the Wigl capture timestamp
            // start CaptureFragment with capture at specified time
            Intent intent = Utils.createCaptureIntent(activity, getCaptureTime(result));
            fragment.startActivityForResult(intent, 0);
            Log.d(TAG, "Started CaptureActivity");

            // TODO: delete file
        }
    }

    private long getCaptureTime(String filename) {
        Uri uri = Uri.parse("file://" + filename);
        ContentResolver cr = activity.getContentResolver();
        InputStream is = null;
        ByteArrayOutputStream byteBuffer = null;
        long captureTime = 0;
        try {
            is = cr.openInputStream(uri);
            byteBuffer = new ByteArrayOutputStream();

            // this is storage overwritten on each iteration with bytes
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];

            // we need to know how may bytes were read to write them to the byteBuffer
            int len;
            while ((len = is.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }

            // and then we can return your byte array.
            byte[] bytes = byteBuffer.toByteArray();
            byteBuffer.close();
            String timestamp = new String(bytes);
            captureTime = Long.parseLong(timestamp);
            Log.d(TAG, "**** Timestamp: " + captureTime);
        } catch (IOException e) {
            Log.e(TAG, "Error processing timestamp" + e.getStackTrace());
        } finally {
            try {
                if (is != null)
                    is.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing InputStream" + e.getMessage());
            }

            try {
                if (byteBuffer != null)
                    byteBuffer.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing ByteArrayOutputStream" + e.getMessage());
            }
            return captureTime;
        }
    }
}