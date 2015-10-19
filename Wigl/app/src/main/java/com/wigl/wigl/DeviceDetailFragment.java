/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.wigl.wigl;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.wigl.wigl.DeviceListFragment.DeviceActionListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A fragment that manages a particular peer and allows interaction with device i.e. setting up
 * network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {
    public static final String IP_SERVER = "192.168.49.1";
    private static final String TAG = "DeviceDetailFragment";
    private static final long CAPTURE_DELAY = 5000;
    ProgressDialog progressDialog = null;
    private View mView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    private long captureTime;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.device_detail, container);

        mView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = ProgressDialog.show(getActivity(), "WiFi P2P magic",
                        "Connecting to :" + device.deviceAddress, true, true
                );

                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                ((DeviceActionListener) getActivity()).connect(config);
            }
        });

        mView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        resetViews();
                        ((DeviceActionListener) getActivity()).disconnect();
                    }
                });

        /**
         * This will become the "capture Wigl" button.  Here, we must send a file to slave devices that will contain:
         *
         * <ul>
         * <li>capture time (unix timestamp)</li>
         * <li>exposure settings</li>
         * </ul>
         *
         */
        mView.findViewById(R.id.btn_start_wigl).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        Uri uri = createAndSaveWiglCaptureFile();

                        TextView statusText = (TextView) mView.findViewById(R.id.status_text);
                        statusText.setText("Sending: " + uri);
                        Log.d(TAG, "Intent: " + uri);

                        // Transfer file to group owner i.e peer using FileTransferClient.
                        Intent clientFileTransfer = createFileTransferClientIntent(uri);
                        getActivity().startService(clientFileTransfer);

                        // For now assume the FileTransferClient is successful.  Maybe in the future have it send back a Result via LocalBroadcastManager
                        Intent capture = Utils.createCaptureIntent(getActivity(), captureTime);
                        startActivityForResult(capture, 0);
                    }
                });

        return mView;
    }

    private Uri createAndSaveWiglCaptureFile() {
        String filename = "wiglM-" + System.currentTimeMillis();
        File file = new File(getActivity().getFilesDir(), filename);
        try {
            FileOutputStream os = new FileOutputStream(file);
            os.write(getCaptureTimestampAsBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Uri.fromFile(file);
    }

    private byte[] getCaptureTimestampAsBytes() {
        captureTime = System.currentTimeMillis() + CAPTURE_DELAY;
        Log.d(TAG, "**** captureTime: " + captureTime);
        return String.format("%d", captureTime).getBytes();
    }

    @NonNull
    private Intent createFileTransferClientIntent(Uri uri) {
        Intent intent = new Intent(getActivity(), FileTransferClient.class);
        intent.setAction(FileTransferClient.ACTION_SEND_FILE);
        intent.putExtra(FileTransferClient.EXTRAS_FILE_PATH, uri.toString());
        intent.putExtra(FileTransferClient.EXTRAS_HOST, getServerIp());
        intent.putExtra(FileTransferClient.EXTRAS_PORT, FileTransferServer.PORT);
        return intent;
    }

    private String getServerIp() {
        String local = Utils.getLocalIPAddress();
        // Trick to find the ip in the file /proc/net/arp
        String other_mac_fixed = new String(device.deviceAddress).replace("99", "19");
        String other = Utils.getIpFromMac(other_mac_fixed);
        return local.equals(IP_SERVER) ? other : IP_SERVER;
    }

    /**
     * Executes after startActivityForResult(CaptureActivity.ACTION_CAPTURE)
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Uri pictureFile = data.getData();
        Log.i(TAG, "We can see you, " + pictureFile);
    }

    /**
     * This gets executed on both devices after one of them initiates "connect".  In the future,
     * bring up the camera preview activity instead (and possibly the Server)
     */
    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;

        // UI information
        TextView view = (TextView) mView.findViewById(R.id.group_owner);
        view.setText(String.format(getString(R.string.group_owner_text), this.info.isGroupOwner));

        view = (TextView) mView.findViewById(R.id.group_ip);
        view.setText(String.format(getString(R.string.group_ip_text), this.info.groupOwnerAddress.getHostAddress()));

        // UI visibility
        mView.setVisibility(View.VISIBLE); // brings into view the DeviceDetail panel
        mView.findViewById(R.id.btn_start_wigl).setVisibility(this.info.isGroupOwner ? View.GONE : View.VISIBLE);
        mView.findViewById(R.id.btn_connect).setVisibility(View.GONE);

        if (this.info.isGroupOwner) {
            new FileTransferServer(this, mView.findViewById(R.id.status_text)).execute();
            Log.d(TAG, "FileTransferServer started on background thread");
        }
    }

    /**
     * Updates the UI with device data
     */
    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        ((TextView) mView.findViewById(R.id.device_address)).setText(device.deviceAddress);
        ((TextView) mView.findViewById(R.id.device_info)).setText(device.toString());
        getView().setVisibility(View.VISIBLE);
    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        ((TextView) mView.findViewById(R.id.device_address)).setText(R.string.empty);
        ((TextView) mView.findViewById(R.id.device_info)).setText(R.string.empty);
        ((TextView) mView.findViewById(R.id.group_owner)).setText(R.string.empty);
        ((TextView) mView.findViewById(R.id.group_ip)).setText(R.string.empty);
        ((TextView) mView.findViewById(R.id.status_text)).setText(R.string.empty);
        mView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        mView.findViewById(R.id.btn_start_wigl).setVisibility(View.GONE);
        getView().setVisibility(View.GONE);
    }
}