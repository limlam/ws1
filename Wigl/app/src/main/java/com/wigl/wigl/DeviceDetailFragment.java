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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

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

/**
 * A fragment that manages a particular peer and allows interaction with device i.e. setting up
 * network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {
    private static final long DELAY = 5000;

    public static final String IP_SERVER = "192.168.49.1";

    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    ProgressDialog progressDialog = null;

    private long captureTime;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContentView = inflater.inflate(R.layout.device_detail, null);

        mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to :" + device.deviceAddress, true, true
                        //                        new DialogInterface.OnCancelListener() {
                        //
                        //                            @Override
                        //                            public void onCancel(DialogInterface dialog) {
                        //                                ((DeviceActionListener) getActivity()).cancelDisconnect();
                        //                            }
                        //                        }
                );
                ((DeviceActionListener) getActivity()).connect(config);

            }
        });

        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
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
        mContentView.findViewById(R.id.btn_start_wigl).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        Uri uri = createAndSaveWiglCaptureFile();

                        TextView statusText = (TextView) mContentView.findViewById(R.id.status_text);
                        statusText.setText("Sending: " + uri);
                        Log.d(WiFiDirectActivity.TAG, "Intent----------- " + uri);

                        // Transfer file to group owner i.e peer using FileTransferClient.
                        Intent clientFileTransfer = createFileTransferClientIntent(uri);
                        getActivity().startService(clientFileTransfer);

                        // For now assume the FileTransferClient is successful.  Maybe in the future have it send back a Result via LocalBroadcastManager
                        Intent capture = Utils.createCaptureIntent(getActivity(), captureTime);
                        startActivityForResult(capture, 0);
                    }
                });

        return mContentView;
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
        captureTime = System.currentTimeMillis() + DELAY;
        Log.d(WiFiDirectActivity.TAG, "**** captureTime: " + captureTime);
        return String.format("%d", captureTime).getBytes();
    }

    @NonNull
    private Intent createFileTransferClientIntent(Uri uri) {
        Intent intent = new Intent(getActivity(), FileTransferClient.class);
        intent.setAction(FileTransferClient.ACTION_SEND_FILE);
        intent.putExtra(FileTransferClient.EXTRAS_FILE_PATH, uri.toString());
        intent.putExtra(FileTransferClient.EXTRAS_ADDRESS, getHostIp());
        intent.putExtra(FileTransferClient.EXTRAS_PORT, FileTransferServer.PORT);
        return intent;
    }

    private String getHostIp() {
        String local = Utils.getLocalIPAddress();
        // Trick to find the ip in the file /proc/net/arp
        String client_mac_fixed = new String(device.deviceAddress).replace("99", "19");
        String client = Utils.getIPFromMac(client_mac_fixed);
        return local.equals(IP_SERVER) ? client : IP_SERVER;
    }

    /**
     * Executes after startActivityForResult(CaptureActivity.ACTION_CAPTURE)
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Uri pictureFile = data.getData();
        Log.i(WiFiDirectActivity.TAG, "We can see you, " + pictureFile);
    }

    /**
     * This gets executed on the device that did not initiate connect
     */
    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;
        this.getView().setVisibility(View.VISIBLE); // brings into view the DeviceDetail panel
        // in the future, bring up the camera preview activity instead

        // The owner IP is now known.
        TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(getResources().getString(R.string.group_owner_text)
                + ((info.isGroupOwner) ? getResources().getString(R.string.yes)
                : getResources().getString(R.string.no)));
        if (info.isGroupOwner) {
            view = (TextView) mContentView.findViewById(R.id.group_ip);
            view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());
        }

        mContentView.findViewById(R.id.btn_start_wigl).setVisibility(View.VISIBLE);

        new FileTransferServer(this, mContentView.findViewById(R.id.status_text)).execute();

        // hide the connect button
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
    }

    /**
     * Updates the UI with device data
     *
     * @param device the device to be displayed
     */
    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(device.deviceAddress);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(device.toString());
    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.status_text);
        view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_start_wigl).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
    }
}