/*
 * Copyright 2013 Alberto Panizzo <alberto@amarulasolutions.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 *
 */

package com.betterembedded.camerapreview;

import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

public class CameraPreviewActivity extends Activity {

	private static final String TAG = "CameraPreview";

	/**
	 * Keep track of the FrameLayout that will contain the preview
	 * surface to be able to set its size on surface updates.
	 */
	private FrameLayout mFramePreview;

	private CameraPreviewSurface mPreviewSurface;
	private SurfaceHolder mHolder;
	private Camera mCamera;
	private boolean surfaceCreated = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		if (!checkCameraHardware()) {
			Log.e(TAG, "No camera available on this device");
			return;
		}

		mFramePreview = (FrameLayout) findViewById(R.id.camera_preview);
		mPreviewSurface = new CameraPreviewSurface(this);
		mFramePreview.addView(mPreviewSurface);
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.w(TAG, "onPause");
		/**
		 * Turn OFF the preview on Pause since the Surface may
		 * be kept the same between an onPause/onResume cycle
		 */
		if (mCamera != null)
			stopCameraPreview();
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.w(TAG, "onResume");
		/**
		 * If the capture was OFF but the surface has been kept
		 * the same since the last onPause, restart the capture now
		 * Otherwise the capture will start when the surface
		 * will be created.
		 */
		if (mCamera == null && surfaceCreated)
			startCameraPreview(mHolder);
	}

	/** Check if this device has a camera */
	private boolean checkCameraHardware() {
		return getPackageManager()
					.hasSystemFeature(PackageManager.FEATURE_CAMERA);
	}

	/** Open the default camera (First Facing back camera) */
	private static Camera getCameraInstance(){
		Camera c = null;
		try {
			c = Camera.open();
		}
		catch (Exception e){
			Log.e(TAG, "Cannot open the default camera: "+e);
		}
		return c;
	}

	private void startCameraPreview(SurfaceHolder holder) {
		if (mCamera == null)
			mCamera = getCameraInstance();
		if (mCamera == null) {
			Log.e(TAG, "Camera not started");
			return;
		}

		// The Surface has been created, now tell the camera where to draw the preview.
		try {
			mCamera.setPreviewDisplay(holder);
			mCamera.startPreview();
		} catch (IOException e) {
			Log.d(TAG, "Error setting camera preview: " + e.getMessage());
		}
		Log.d(TAG, "Start Preview");

		if (holder != mHolder)
				mHolder = holder;
	}

	private void stopCameraPreview() {
		if (mCamera == null)
			return;
		try {
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		} catch (Exception e){
			// ignore: tried to stop a non-existent preview
		}
		Log.d(TAG, "Stop Preview");
	}

	/** A basic Camera preview class */
	public class CameraPreviewSurface extends SurfaceView
									  implements SurfaceHolder.Callback {

		@SuppressWarnings("deprecation")
		public CameraPreviewSurface(Context context) {
			super(context);

			// Install a SurfaceHolder.Callback so we get notified when the
			// underlying surface is created and destroyed.
			mHolder = getHolder();
			mHolder.addCallback(this);
			// deprecated setting, but required on Android versions prior to 3.0
			mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}

		public void surfaceCreated(SurfaceHolder holder) {
			Log.w(TAG, "Surface created");
			/**
			 * Create the camera instance now to bound its life 
			 * to the Surface lifecycle.
			 * The camera instance will be released when this surface
			 * will be destroyed.
			 */
			startCameraPreview(holder);
			surfaceCreated = true;
		}

		public void surfaceDestroyed(SurfaceHolder holder) {
			Log.w(TAG, "Surface destroyed");
			// stop preview before making changes
			stopCameraPreview();
			surfaceCreated = false;
		}

		public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
			// If your preview can change or rotate, take care of those events here.
			// Make sure to stop the preview before resizing or reformatting it.
			Log.w(TAG, "Surface changed");

			if (mHolder.getSurface() == null){
				// preview surface does not exist
				return;
			}

			// stop preview before making changes
			try {
				mCamera.stopPreview();
			} catch (Exception e){
				// ignore: tried to stop a non-existent preview
			}

			/**
			 * Adjust the FrameLayout size to expand as much as
			 * possible but keep the camera aspect ratio 
			 */
			Parameters params = mCamera.getParameters();
			Size s = params.getPreviewSize();
			float factor = (float)s.width / (float)s.height;
			int frameW = w;
			int frameH = h;
			if (s.width > w) {
				frameW = w;
				frameH = (int)((float) frameW /factor);
			} else if (s.height > h) {
				frameH = h;
				frameW = (int)((float) frameH * factor);
			}
			if (frameW != w || frameH != h) {
				LayoutParams lparams = mFramePreview.getLayoutParams();
				lparams.width = frameW;
				lparams.height = frameH;
				mFramePreview.setLayoutParams(lparams);
				Log.d(TAG, "Changing surface size: ("+w+"x"+h+")->"+"("+frameW+"x"+frameH+")");
				return;
			}

			// start preview with new settings
			try {
				mCamera.setPreviewDisplay(mHolder);
				mCamera.startPreview();

			} catch (Exception e){
				Log.d(TAG, "Error starting camera preview: " + e.getMessage());
			}
		}
	}
}