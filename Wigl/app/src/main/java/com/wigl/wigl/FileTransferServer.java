package com.wigl.wigl;

import android.app.Activity;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.Context;
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
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A simple server socket that accepts connection and writes some data on
 * the stream.
 */
public class FileTransferServer extends AsyncTask<Void, Void, String> {
    private static final String TAG = "FileTransferServer";

    public static final int PORT = 8988;

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
        statusText.setText("Opening a server socket");
    }

    /**
     * Accept incoming image file from Socket, save to local disk
     */
    @Override
    protected String doInBackground(Void... params) {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            Log.d(TAG, "Server: Socket opened");
            Socket client = serverSocket.accept();
            Log.d(TAG, "Server: connection done");
            final File f = new File(activity.getFilesDir(), "wiglS-" + System.currentTimeMillis());

            Log.d(TAG, "server: copying files " + f.toString());
            InputStream inputstream = client.getInputStream();
            Utils.copyFile(inputstream, new FileOutputStream(f));
            serverSocket.close();
            return f.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }

    /*
     * Once a file has been asynchronously transferred, do this
     */
    @Override
    protected void onPostExecute(String result) {
        if (result != null) {
            // read the contents of the file for the Wigl capture timestamp
            // start CaptureFragment with capture at specified time

            Log.d(TAG, "File copied: " + result);
            statusText.setText("File copied: " + result);

            Intent intent = Utils.createCaptureIntent(activity, getCaptureTime(result));
            fragment.startActivityForResult(intent, 0);
        }
    }

    private long getCaptureTime(String filename) {
        Uri uri = Uri.parse("file://" + filename);
        ContentResolver cr = activity.getContentResolver();
        InputStream is = null;
        ByteArrayOutputStream byteBuffer = null;
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
            long captureTime = Long.parseLong(timestamp);
            Log.d(TAG, "**** timestamp: " + captureTime);
            return captureTime;
        } catch (IOException e) {
            throw new RuntimeException(e);
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
        }
    }
}