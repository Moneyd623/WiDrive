package com.example.widrive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.apache.http.client.ClientProtocolException;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

public class WiDriveCamActivity extends Activity implements WiDriveCamView.CameraReadyCallback {
  private WiDriveCamView cameraView_;
  
  // main handler
  final Handler mHandler = new Handler();
  
  // connection variables
  public static final String EXTRAS_GROUP_OWNER_ADDRESS = "go_host";
  private String host;
  private int port = 8988;
  private static final int SOCKET_TIMEOUT = 5000;
  
  // stream variables
  ByteArrayOutputStream buffer;
  OutputStream stream = null;
  private byte[] frame = null;
  //private boolean streamOK;
  
  // image variables
  private int IMG_WIDTH;
  private int IMG_HEIGHT;
  private Rect area;
  private int imageFormat;
  
  //MJPEG variables
  private static String boundary = "WIDRIVE";
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      requestWindowFeature(Window.FEATURE_NO_TITLE);
      Window win = getWindow();
      win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);    
      win.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
      
      Intent mIntent = getIntent();
      host = mIntent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);      
      buffer = new ByteArrayOutputStream();
      //streamOK = false;
      
      setContentView(R.layout.wicamsurface);
      initCamera();
      new WriteStream().execute();
  }
  
  @Override
  public void onCameraReady() {
          IMG_WIDTH = cameraView_.Width();
          IMG_HEIGHT = cameraView_.Height();
          area = new Rect(0, 0, IMG_WIDTH, IMG_HEIGHT);
          cameraView_.StopPreview();
          cameraView_.setupCamera(IMG_WIDTH, IMG_HEIGHT, previewCb_);
          cameraView_.StartPreview();
  }
  
  @Override
  public void onPause(){  
      super.onPause();
      cameraView_.StopPreview(); 
      finish();
  }  

  private void initCamera() {
	  Log.d(WiDriveActivity.TAG,"initCamera");
      SurfaceView cameraSurface = (SurfaceView)findViewById(R.id.MySurface);
      cameraView_ = new WiDriveCamView(cameraSurface);        
      cameraView_.setCameraReadyCallback(this);
  }
  
  public class WriteStream extends AsyncTask<String, Void, Void> {

      protected Void doInBackground(String... url) {
    	  Log.d(WiDriveActivity.TAG,"ReadStream doInBackground");
          Socket socket = new Socket();

	      try {
	          Log.d(WiDriveActivity.TAG, "Opening client socket - ");
	          socket.bind(null);
	          socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);
	
	          Log.d(WiDriveActivity.TAG, "Client socket - " + socket.isConnected());
	          //streamOK = true;
	          stream = socket.getOutputStream();
	          
	      } catch (ClientProtocolException e) {
	          e.printStackTrace();
	          //streamOK = false;
	      } catch (IOException e) {
	          e.printStackTrace();
	          //streamOK = true;
	      }
	      //initCamera();
		return null;
      }
  }

  private PreviewCallback previewCb_ = new PreviewCallback() {
      
	  public void onPreviewFrame(byte[] data, Camera c) {
         
    	  frame = data;
    	  imageFormat = c.getParameters().getPreviewFormat();
    	  
          mHandler.post(new Runnable() {
              public void run() {
            	  if (stream != null){
            	    try
            	    {
            	       buffer.reset();
            	       synchronized(frame){
            	    	   new YuvImage(frame, imageFormat, IMG_WIDTH, IMG_HEIGHT, null).compressToJpeg(area, 100, buffer);
            	       }
            	        buffer.flush();

            	        // write the content header
            	        stream.write(("--" + boundary + "\r\n" + 
            	                      "Content-type: image/jpg\r\n" + 
            	                      "Content-Length: " + buffer.size() + 
            	                      "\r\n\r\n").getBytes());

            	        buffer.writeTo(stream);
            	        stream.write("\r\n\r\n".getBytes());
            	        stream.flush();
            	    }
            	    catch (IOException e)
            	    {
            	    	Log.d(WiDriveActivity.TAG, e.getMessage());
            	    }
            	  }
              }
          });
      }
  };
}