// Copyright 2011 Google Inc. All Rights Reserved.

package com.wigl.wigl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * A service that process each file transfer request i.e Intent by opening a
 * socket connection with the WiFi Direct Group Owner and writing the file
 */
public class FileTransferClient extends IntentService {
    private static final String TAG = "FileTransferClient";

    private static final int SOCKET_TIMEOUT = 5000;
    public static final String ACTION_SEND_FILE = "com.wigl.wigl.SEND_FILE";
    public static final String EXTRAS_FILE_PATH = "file_url";
    public static final String EXTRAS_HOST = "go_host";
    public static final String EXTRAS_PORT = "go_port";

    public FileTransferClient(String name) {
        super(name);
    }

    public FileTransferClient() {
        super("FileTransferClient");
    }

    /*
     * After identifying a file, this is going to send that local file to WiFi P2P client
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        Context context = getApplicationContext();
        if (intent.getAction().equals(ACTION_SEND_FILE)) {
            String fileUri = intent.getExtras().getString(EXTRAS_FILE_PATH);
            String host = intent.getExtras().getString(EXTRAS_HOST);
            int port = intent.getExtras().getInt(EXTRAS_PORT);

            Socket socket = new Socket();
            OutputStream os = null;
            InputStream is = null;
            try {
                Log.d(TAG, "Opening client socket");
                socket.bind(null);
                socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);
                Log.d(TAG, "Client connected: " + socket.isConnected());

                os = socket.getOutputStream();
                ContentResolver cr = context.getContentResolver();
                is = cr.openInputStream(Uri.parse(fileUri));
                Utils.copyFile(is, os);
                Log.d(TAG, "Client data written");
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            } finally {
                if (socket != null) {
                    if (socket.isConnected()) {
                        Utils.close(socket);
                    }
                }

                Utils.close(is);
                Utils.close(os);
            }

        }
    }
}