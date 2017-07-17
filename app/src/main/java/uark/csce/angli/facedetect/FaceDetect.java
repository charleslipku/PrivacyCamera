package uark.csce.angli.facedetect;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.FaceDetector;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


public class FaceDetect extends Activity {
    private static final int SELECT_PICTURE_FROM_GALLERY=1;
    private static final int TAKE_PHOTO=2;
    private static final String CONFIRM_BLUR_FACE="Yes";
    private static final String DISMISS_NOTIFICATION="No";
    private static final String TAG="PrivacyCamera";

    Button buttonSelectPhoto;
    Button buttonTakePhoto;
    Button buttonBlurFace;
    private ImageView imageView;
    String currentPhotoPath;
    public int imageWidth;
    public int imageHeight;
    public int MAX_FACES_DETECTED=1;
    public FaceDetector faceDetector;
    public FaceDetector.Face[] detectedFaces;
    float eyeDistance;
    int numberOfDetectedFaces;
    Bitmap faceBitmap;
   public InputStream inputStream;
    Paint painRect = new Paint();
    Canvas canvas=new Canvas();
    ContentValues contentValues;
   public Uri imageUri;
    public LocationManager locationManager;
    TextView textView;
    String provider;
   public  NotificationManager notificationManager;
    int notificationRef=1;
    String groupOwnerAddress;
    IntentFilter intentFilter=new IntentFilter();
    WifiP2pManager wifiP2pManager;
    WifiP2pManager.Channel channel;
    List peersList=new ArrayList();
    LocationListener locationListener;
    Location location;
    double longtitude;
    double  latitude;
    double cameraHorizontalViewAngle;
    float heading;
    SensorManager sensorManager;
    GeomagneticField geomagneticField;
    boolean isInCamera;
    float gpsAccuracy;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_detect);
        buttonSelectPhoto=(Button)findViewById(R.id.buttonSelectPhoto);
        buttonTakePhoto=(Button)findViewById(R.id.buttonTakePhoto);
        buttonBlurFace=(Button)findViewById(R.id.buttonBlurFace);
        imageView=(ImageView)findViewById(R.id.imageView);
        textView=(TextView)findViewById(R.id.gpsCoordinates);

        cameraHorizontalViewAngle=60.0;

        //Initiate wifidirect framework
        initializeWifiDirect();

        //Add intent for listening wifi p2p states
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        //Get current location information
        longtitude=0.0;
        latitude=0.0;
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);

        locationListener=new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (location != null) {
                    longtitude=location.getLongitude();
                    latitude=location.getLatitude();
                }
                else {
                    return;
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0,0,locationListener);
            Log.e(TAG, "Get Location From GPS Provider");
            location=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(location!=null) {
                gpsAccuracy=location.getAccuracy();
                longtitude = location.getLongitude();
                latitude = location.getLatitude();
                textView.setText("Current GPS Coordinate:" + String.valueOf(longtitude) + "," + String.valueOf(latitude));
                textView.append("GPS Accuracy:"+String.valueOf(gpsAccuracy)+"meters.");
            }
            else {
                Log.e(TAG,"Get Location form Network");
                Criteria criteria= new Criteria();
                criteria.setBearingRequired(true);
                provider=locationManager.getBestProvider(criteria, false);
                location=locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                onLocationChanged(location);

            }
        }else {
            Log.e(TAG,"Get Location form Network");
            Criteria criteria= new Criteria();
            String  provider=locationManager.getBestProvider(criteria, false);
            location=locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            onLocationChanged(location);
        }

        //Get orientation from accelerometer and magnetometer
        sensorManager=(SensorManager)getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor=sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        final SensorEventListener sensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                heading=event.values[0];
                geomagneticField = new GeomagneticField((float)location.getLatitude(),(float)location.getLongitude(),(float)location.getAltitude(),System.currentTimeMillis());
                heading+=geomagneticField.getDeclination();
                //Log.e(TAG, "Heading:" + heading);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };
        sensorManager.registerListener(sensorEventListener,sensor,SensorManager.SENSOR_DELAY_UI);


        buttonSelectPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*Intent intent=new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent,SELECT_PICTURE_FROM_GALLERY);*/
                Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, SELECT_PICTURE_FROM_GALLERY);
                buttonBlurFace.setVisibility(View.VISIBLE);

            }
        });
        buttonTakePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                contentValues=new ContentValues();
                imageUri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,contentValues);
                Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                startActivityForResult(intent, TAKE_PHOTO);

            }
        });
        buttonBlurFace.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                blurFace();
                }
        });

        if (numberOfDetectedFaces>0){
            wifiP2pManager.discoverPeers(channel,actionListener);
            if (peersList.size()==0){
                Log.e(TAG, "No Peers Found");
                Toast.makeText(getApplicationContext(), "No peer found!", Toast.LENGTH_SHORT);
            }else if (peersList.size()>0)
            {
                for (int i=0; i < peersList.size();i++)
                {
                    Log.e(TAG, "Connecting to: " + peersList.get(i).toString());
                    connecTo((WifiP2pDevice) peersList.get(i));
                }
            }
        }
    }

    //Blur face
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void blurFace() {
        imageWidth = faceBitmap.getWidth();
        imageHeight = faceBitmap.getHeight();
        imageView.setImageBitmap(faceBitmap);

        detectedFaces = new FaceDetector.Face[MAX_FACES_DETECTED];
        faceDetector = new FaceDetector(imageWidth, imageHeight, MAX_FACES_DETECTED);
        numberOfDetectedFaces = faceDetector.findFaces(faceBitmap, detectedFaces);
        Paint ditherPaint = new Paint();
        ditherPaint.setDither(true);
        canvas.setBitmap(faceBitmap);
        canvas.drawBitmap(faceBitmap, 0, 0, ditherPaint);

        if (numberOfDetectedFaces > 0 && numberOfDetectedFaces < 2) {
            for (int i = 0; i < numberOfDetectedFaces; i++) {
                FaceDetector.Face face = detectedFaces[i];
                gaussianBlur(face);
            }
        }else {
            blurMiniFace(detectedFaces,numberOfDetectedFaces);

        }
    }
    //Guassian Blur
    public void gaussianBlur (FaceDetector.Face face){
        imageWidth = faceBitmap.getWidth();
        imageHeight = faceBitmap.getHeight();
        imageView.setImageBitmap(faceBitmap);

                PointF myPointF = new PointF();
                face.getMidPoint(myPointF);
                eyeDistance = face.eyesDistance();
                float scale = 1.2f;
                int radius = 25;

                int w = (int) (2*eyeDistance*scale);
                int h = (int) (2*eyeDistance*scale);

                int[] pix = new int[w * h];
                Log.e("pix", w + " " + h + " " + pix.length);
                faceBitmap.getPixels(pix, 0, w, (int) (myPointF.x - eyeDistance * scale), (int) (myPointF.y - eyeDistance * scale), w, h);
                int wm = w - 1;
                int hm = h - 1;
                int wh = w * h;
                int div = radius + radius + 1;

                int r[] = new int[wh];
                int g[] = new int[wh];
                int b[] = new int[wh];
                int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
                int vmin[] = new int[Math.max(w, h)];

                int divsum = (div + 1) >> 1;
                divsum *= divsum;
                int dv[] = new int[256 * divsum];
                for (i = 0; i < 256 * divsum; i++) {
                    dv[i] = (i / divsum);
                }

                yw = yi = 0;

                int[][] stack = new int[div][3];
                int stackpointer;
                int stackstart;
                int[] sir;
                int rbs;
                int r1 = radius + 1;
                int routsum, goutsum, boutsum;
                int rinsum, ginsum, binsum;

                for (y = 0; y < h; y++) {
                    rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
                    for (i = -radius; i <= radius; i++) {
                        p = pix[yi + Math.min(wm, Math.max(i, 0))];
                        sir = stack[i + radius];
                        sir[0] = (p & 0xff0000) >> 16;
                        sir[1] = (p & 0x00ff00) >> 8;
                        sir[2] = (p & 0x0000ff);
                        rbs = r1 - Math.abs(i);
                        rsum += sir[0] * rbs;
                        gsum += sir[1] * rbs;
                        bsum += sir[2] * rbs;
                        if (i > 0) {
                            rinsum += sir[0];
                            ginsum += sir[1];
                            binsum += sir[2];
                        } else {
                            routsum += sir[0];
                            goutsum += sir[1];
                            boutsum += sir[2];
                        }
                    }
                    stackpointer = radius;

                    for (x = 0; x < w; x++) {

                        r[yi] = dv[rsum];
                        g[yi] = dv[gsum];
                        b[yi] = dv[bsum];

                        rsum -= routsum;
                        gsum -= goutsum;
                        bsum -= boutsum;

                        stackstart = stackpointer - radius + div;
                        sir = stack[stackstart % div];

                        routsum -= sir[0];
                        goutsum -= sir[1];
                        boutsum -= sir[2];

                        if (y == 0) {
                            vmin[x] = Math.min(x + radius + 1, wm);
                        }
                        p = pix[yw + vmin[x]];

                        sir[0] = (p & 0xff0000) >> 16;
                        sir[1] = (p & 0x00ff00) >> 8;
                        sir[2] = (p & 0x0000ff);

                        rinsum += sir[0];
                        ginsum += sir[1];
                        binsum += sir[2];

                        rsum += rinsum;
                        gsum += ginsum;
                        bsum += binsum;

                        stackpointer = (stackpointer + 1) % div;
                        sir = stack[(stackpointer) % div];

                        routsum += sir[0];
                        goutsum += sir[1];
                        boutsum += sir[2];

                        rinsum -= sir[0];
                        ginsum -= sir[1];
                        binsum -= sir[2];

                        yi++;
                    }
                    yw += w;
                }

                for (x = 0; x < w; x++) {
                    rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
                    yp = -radius * w;
                    for (i = -radius; i <= radius; i++) {
                        yi = Math.max(0, yp) + x;

                        sir = stack[i + radius];

                        sir[0] = r[yi];
                        sir[1] = g[yi];
                        sir[2] = b[yi];

                        rbs = r1 - Math.abs(i);

                        rsum += r[yi] * rbs;
                        gsum += g[yi] * rbs;
                        bsum += b[yi] * rbs;

                        if (i > 0) {
                            rinsum += sir[0];
                            ginsum += sir[1];
                            binsum += sir[2];
                        } else {
                            routsum += sir[0];
                            goutsum += sir[1];
                            boutsum += sir[2];
                        }

                        if (i < hm) {
                            yp += w;
                        }
                    }
                    yi = x;
                    stackpointer = radius;
                    for (y = 0; y < h; y++) {
                        // Preserve alpha channel: ( 0xff000000 & pix[yi] )
                        pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

                        rsum -= routsum;
                        gsum -= goutsum;
                        bsum -= boutsum;

                        stackstart = stackpointer - radius + div;
                        sir = stack[stackstart % div];

                        routsum -= sir[0];
                        goutsum -= sir[1];
                        boutsum -= sir[2];

                        if (x == 0) {
                            vmin[y] = Math.min(y + r1, hm) * w;
                        }
                        p = x + vmin[y];

                        sir[0] = r[p];
                        sir[1] = g[p];
                        sir[2] = b[p];

                        rinsum += sir[0];
                        ginsum += sir[1];
                        binsum += sir[2];

                        rsum += rinsum;
                        gsum += ginsum;
                        bsum += binsum;

                        stackpointer = (stackpointer + 1) % div;
                        sir = stack[stackpointer];

                        routsum += sir[0];
                        goutsum += sir[1];
                        boutsum += sir[2];

                        rinsum -= sir[0];
                        ginsum -= sir[1];
                        binsum -= sir[2];

                        yi += w;
                    }
                }
                Log.e("pix", w + " " + h + " " + pix.length);
                faceBitmap.setPixels(pix, 0, w,(int) (myPointF.x - eyeDistance * scale), (int) (myPointF.y - eyeDistance * scale), w, h);



    }
    //Blur Mini Face
    public void blurMiniFace (FaceDetector.Face [] faces,int numberOfDetectedFaces){
        FaceDetector.Face miniFace = faces[0];
        for (int i=0;i<numberOfDetectedFaces;i++){
            if (miniFace.eyesDistance()<=faces[i].eyesDistance()){
                miniFace = faces[0];
            }else {
                miniFace = faces[i];
            }
        }
        gaussianBlur(miniFace);
    }
    //Show notification
    private void showNotification(){

        Intent confirmBlurFace=new Intent();
        Intent dismissNotification=new Intent();
        confirmBlurFace.setAction(CONFIRM_BLUR_FACE);
        dismissNotification.setAction(DISMISS_NOTIFICATION);

        Resources resource=getResources();
        Bitmap largeIcon=BitmapFactory.decodeResource(resource,R.drawable.notification);
        notificationManager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder notificationBulider = new NotificationCompat.Builder(FaceDetect.this);
        notificationBulider.setSmallIcon(R.drawable.notification);
        notificationBulider.setLargeIcon(largeIcon);
        notificationBulider.setTicker("privacy warning");
        notificationBulider.setContentTitle("Privacy Warning");
        notificationBulider.setStyle(new NotificationCompat.BigTextStyle().bigText("I am sorry that you may be captured in my photo, would you like to blur your face?"));
        //notificationBulider.setContentText("I am sorry that you may be captured in my photo, would you like to blur your face?");
        notificationBulider.setWhen(System.currentTimeMillis());
        notificationBulider.setDefaults(Notification.DEFAULT_SOUND);
        notificationBulider.setVibrate(new long[]{1000, 1000, 1000, 1000, 1000});
        notificationBulider.setLights(Color.RED, 0, 1);
        notificationBulider.setAutoCancel(true);
        Notification notification=notificationBulider.getNotification();
        notificationManager.notify(notificationRef, notification);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // TODO Auto-generated method stub
        super.onSaveInstanceState(outState);
        if (imageUri == null) {
            return;
        }
        outState.putString("photoUri", imageUri.toString());
    }
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onRestoreInstanceState(savedInstanceState);
        imageUri = Uri.parse(savedInstanceState.getString("photoUri"));
    }

    private void dealImage(Uri uri) {
        String[] filePathColumns = { MediaStore.Images.Media.DATA };
        Cursor c = this.getContentResolver().query(uri,
                filePathColumns, null, null, null);
        c.moveToFirst();
        String picturePath = c.getString(c.getColumnIndex(filePathColumns[0]));

        Bitmap tempBitmap = loadImage(picturePath);
        faceBitmap = tempBitmap.copy(Bitmap.Config.RGB_565, true);
        detectFaces();
    }

    /**
     * In case of OutOfMemory Exception
     * @param imgPath
     * @return
     */
    private Bitmap loadImage(String imgPath) {
        BitmapFactory.Options options;
        try {
            options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            Bitmap bitmap = BitmapFactory.decodeFile(imgPath, options);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
       if(requestCode==SELECT_PICTURE_FROM_GALLERY&&resultCode==Activity.RESULT_OK) {
            if (faceBitmap!=null){
                faceBitmap.recycle();
            }
           try {
               inputStream=getContentResolver().openInputStream(data.getData());
               Bitmap temBitmap= BitmapFactory.decodeStream(inputStream);
               faceBitmap=temBitmap.copy(Bitmap.Config.RGB_565,true);
               imageView.setImageBitmap(faceBitmap);
               detectFaces();
           } catch (FileNotFoundException e) {
               e.printStackTrace();
           }
        }
        else if (requestCode==TAKE_PHOTO && resultCode==Activity.RESULT_OK){
           if (faceBitmap!=null){
               faceBitmap.recycle();
           }
           dealImage(imageUri);

       }

    }
    public void detectFaces(){
        imageWidth=faceBitmap.getWidth();
        imageHeight=faceBitmap.getHeight();
        imageView.setImageBitmap(faceBitmap);

        detectedFaces=new FaceDetector.Face[MAX_FACES_DETECTED];
        faceDetector=new FaceDetector(imageWidth,imageHeight,MAX_FACES_DETECTED);
        numberOfDetectedFaces=faceDetector.findFaces(faceBitmap,detectedFaces);
        Log.e(TAG,"Number of face:"+String.valueOf(numberOfDetectedFaces));
        Paint ditherPaint=new Paint();
        ditherPaint.setDither(true);

        canvas.setBitmap(faceBitmap);
        canvas.drawBitmap(faceBitmap, 0, 0, ditherPaint);

        if(numberOfDetectedFaces>0){
                for (int i = 0; i < numberOfDetectedFaces; i++) {
                    if (detectedFaces[i].confidence() > 0.15f) {
                        FaceDetector.Face face = detectedFaces[i];
                        PointF myPointF = new PointF();
                        face.getMidPoint(myPointF);
                        eyeDistance = face.eyesDistance();

                        int pixel = faceBitmap.getPixel((int) (myPointF.x), (int) (myPointF.y));
                        int redColor = Color.red(pixel);
                        int greenColor = Color.green(pixel);
                        int blueColor = Color.blue(pixel);
                        int alphaValue = Color.alpha(pixel);
                        //painRect.setARGB(alphaValue, redColor, greenColor, blueColor);
                        painRect.setColor(Color.WHITE);
                        painRect.setStyle(Paint.Style.STROKE);
                        painRect.setStrokeWidth(10);
                        double scale = 1.2;
                        canvas.drawRect((int) (myPointF.x - eyeDistance * scale), (int) (myPointF.y - eyeDistance * scale), (int) (myPointF.x + eyeDistance * scale), (int) (myPointF.y + eyeDistance * scale), painRect);
                    }
                }

        }

        if (numberOfDetectedFaces>0 && isInCamera==true){
            Toast.makeText(getApplicationContext(),"Client is in the camera! Start to blur face!",Toast.LENGTH_SHORT).show();
            blurFace();
        }else if (numberOfDetectedFaces>0 && isInCamera==false){
            Toast.makeText(getApplicationContext(),"Client is outside the camera!",Toast.LENGTH_SHORT).show();
        }else {
            Toast.makeText(getApplicationContext(),"No face detected!",Toast.LENGTH_SHORT).show();
            return;
        }
    }

    public void  onLocationChanged(Location location) {
        if (location != null) {
            longtitude = location.getLongitude();
            latitude = location.getLatitude();
            gpsAccuracy=location.getAccuracy();
            textView.setText("Current GPS Coordinate:" + String.valueOf(longtitude) + "," + String.valueOf(latitude));
            textView.append("GPS Accuracy:" + String.valueOf(gpsAccuracy) + "meters.");
        }
    }

    //Initialize WifiDirect
    public void initializeWifiDirect()
    {
        Log.e(TAG, "Initializing Wifi Direct");
        wifiP2pManager=(WifiP2pManager)getSystemService(Context.WIFI_P2P_SERVICE);
        channel=wifiP2pManager.initialize(this, getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected()
            {
                Log.e(TAG, "onChannelDisconnected()");
                initializeWifiDirect();
            }
        });
    }

    BroadcastReceiver broadcastReceiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();
            if(action.equals(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)){
                Log.e(TAG, "WIFI_P2P_STATE_CHANGED_ACTION Received");
                int state=intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, WifiP2pManager.WIFI_P2P_STATE_DISABLED);
                if(state==WifiP2pManager.WIFI_P2P_STATE_ENABLED){

                }else {
                    Intent temIntent=new Intent(Settings.ACTION_WIFI_SETTINGS);
                    startActivity(temIntent);
                }

            }else if (action.equals(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)){
                Log.e(TAG, "WIFI_P2P_PEERS_CHANGED_ACTION Received");
                if (wifiP2pManager!=null){
                    wifiP2pManager.requestPeers(channel,peerListListener);
                }
                Toast.makeText(getApplicationContext(),"Peers have changed",Toast.LENGTH_SHORT);

            }else if (action.equals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)){
                Log.e(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION Received");
                NetworkInfo networkInfo=(NetworkInfo)intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if (networkInfo.isConnected()){
                    wifiP2pManager.requestConnectionInfo(channel,connectionInfoListener);
                }


            }
            else if (action.equals(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)){

            }
        }
    };

    //Listen for peerlist changes
    WifiP2pManager.PeerListListener peerListListener=new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peers)
        {
            Log.e(TAG, "OnPeersAvailable: " + peers.getDeviceList().size());
            peersList.clear();
            peersList.addAll(peers.getDeviceList());
        }
    };

    //Listen for connection state
    WifiP2pManager.ConnectionInfoListener connectionInfoListener=new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            groupOwnerAddress = info.groupOwnerAddress.getHostAddress();
            Log.e(TAG, "OnConnectionInfoAvailable: Group Owner Address: " + groupOwnerAddress);
            if (info.groupFormed)
            {
                Log.e(TAG, "Group Formed!");
                if (info.isGroupOwner)
                    initServerSocket();
                else
                    initClientSocket(groupOwnerAddress);
            }

        }
    };

    private void initServerSocket(){

        String message= "hello"+"\n";
        Log.e(TAG, "Initiating server socket. Message: " + message);
        new ServerSocket(this).execute(message);
    }

    private void initClientSocket(final String hostAddress){
        Log.e(TAG, "Initiating client socket: Host Address: " + hostAddress);
        ClientSocket clientSocket =new ClientSocket(this);
        clientSocket.execute(hostAddress);
    }

    public void connecTo(WifiP2pDevice device){
        WifiP2pConfig wifiP2pConfig=new WifiP2pConfig();
        wifiP2pConfig.deviceAddress=device.deviceAddress;
        wifiP2pConfig.groupOwnerIntent=15;
        wifiP2pManager.connect(channel, wifiP2pConfig, actionListener);
    }

    private WifiP2pManager.ActionListener actionListener=new WifiP2pManager.ActionListener() {
        @Override
        public void onSuccess() {
            Log.e(TAG, "ActionListener.onSuccess");
        }

        @Override
        public void onFailure(int reason) {
            String errorMessage = "WiFi Direct Failed: ";
            switch (reason){
                case WifiP2pManager.BUSY :
                    errorMessage += "Framework busy."; break;
                case WifiP2pManager.ERROR :
                    errorMessage += "Internal error."; break;
                case WifiP2pManager.P2P_UNSUPPORTED :
                    errorMessage += "Unsupported."; break;
                default:
                    errorMessage += "Unknown error."; break;
            }
            Log.e(TAG, "ActionListener.onFailure: Message: " + errorMessage);
            Toast.makeText(getApplicationContext(),errorMessage,Toast.LENGTH_SHORT).show();
        }


    };

    public float normalizeDegree(float value) {
        if (value >= 0.0f && value <= 180.0f) {
            return value;
        } else {
            return 360+value;
        }
    }

    public float getRelativeAngle(float angle){
        if(Math.abs(angle)<=180){
            return Math.abs(angle);
        }else {
            return 360-Math.abs(angle);
        }

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_face_detect, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(broadcastReceiver, intentFilter);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
    }


    /**
     * Created by ANG LI on 6/4/2015.
     */
    private class ClientSocket extends AsyncTask<String,Void,Void>
    {
        private Activity mContext;
        public String message;


        public ClientSocket(Activity context)
        {
            mContext = context;
        }


        @Override
        protected Void doInBackground(String... params) {
            int port=4139;
            int timeOut=10000;

            InetSocketAddress socketAddress=new InetSocketAddress(params[0], port);
            Socket messageClient=new Socket();
            try {
                messageClient.bind(null);
                messageClient.connect(socketAddress);
                mContext.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mContext, "Connecting to server", Toast.LENGTH_SHORT).show();
                    }
                });
                Scanner in=new Scanner(messageClient.getInputStream());
                PrintStream out=new PrintStream(messageClient.getOutputStream());
                if (in.hasNext())
                {
                    mContext.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mContext, "Receiving  Message " , Toast.LENGTH_SHORT).show();
                        }
                    });
                    message=in.next();
                    Log.e(TAG,message);
                    if (message.equalsIgnoreCase("hello")){
                        showNotification();
                        out.print(longtitude+","+latitude+"\n");
                        out.flush();
                        // out.close();
                        Log.e(TAG, longtitude+","+latitude);
                    }

                    mContext.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mContext, "Message Received: " + message, Toast.LENGTH_SHORT).show();

                        }
                    });
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {

        }
    }

    /**
     * Created by ANG LI on 6/4/2015.
     */
    private class ServerSocket extends AsyncTask <String,Void,Void>
    {
        private Activity mContext;
        java.net.ServerSocket serverSocket;
        float temLon=0;
        float temLat=0;
        String response;
        float bearing=0;

        public ServerSocket(Activity context){
            mContext = context;
        }

        @Override
        protected Void doInBackground(String... params) {
            try {
                serverSocket=new java.net.ServerSocket(4139);
                while (true){
                    Socket messageServer=serverSocket.accept();
                    PrintStream out=new PrintStream(messageServer.getOutputStream());
                    InputStream inputStream=messageServer.getInputStream();
                    Scanner in=new Scanner(inputStream);
                    final String message = params[0];
                    out.print(message);
                    out.flush();
                    //out.close();
                    mContext.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mContext, "Message Flushed: " + message, Toast.LENGTH_SHORT).show();
                        }
                    });
                    mContext.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mContext, "Message Sent: " + message, Toast.LENGTH_SHORT).show();
                        }
                    });

                    if (inputStream.available()>0){
                        Log.e(TAG,"Empty Inputstream");
                    }
                    if(in.hasNext()) {
                        if (in.hasNextDouble()) {
                            temLon = (float)(in.nextDouble());
                            temLat = (float)(in.nextDouble());
                            Log.e(TAG, String.valueOf(temLon));
                            Log.e(TAG, String.valueOf(temLat));
                        }else {
                            response=in.next();
                            Log.e(TAG,response);
                            String []recMessage=response.split(",");
                            temLon=Float.parseFloat(recMessage[0]);
                            temLat=Float.parseFloat(recMessage[1]);
                            Log.e(TAG, String.valueOf(temLon));
                            Log.e(TAG, String.valueOf(temLat));
                            mContext.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mContext, "Client GPS:" + temLon + "," + temLat, Toast.LENGTH_SHORT).show();
                                }
                            });
                            Location temLocation=new Location("TempLocation");
                            temLocation.setLongitude(temLon);
                            temLocation.setLatitude(temLat);
                            bearing=location.bearingTo(temLocation);
                            Log.e(TAG, "Bearing:" + location.bearingTo(temLocation));
                            mContext.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mContext, "Bearing:" + bearing, Toast.LENGTH_SHORT).show();
                                }
                            });
                            float [] results=new float[3];
                            Location.distanceBetween(location.getLatitude(), location.getLongitude(), temLat, temLon, results);
                            final float distance=results[0];
                            mContext.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mContext, "Heading:"+heading, Toast.LENGTH_SHORT).show();
                                }
                            });

                            float normalBearing = normalizeDegree(location.bearingTo(temLocation));
                            final float relativeAngle=getRelativeAngle(normalBearing-heading);
                            Log.e(TAG, String.valueOf(relativeAngle));
                            mContext.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mContext, "Relative Angle:"+relativeAngle, Toast.LENGTH_SHORT).show();
                                }
                            });

                            if (relativeAngle<=30.0){
                                isInCamera=true;
                               /* runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        blurFace();
                                    }
                                });*/
                                mContext.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(mContext, "Client is in camera!" + "isInCamera:" + String.valueOf(isInCamera), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                            else {
                                isInCamera=false;
                                Log.e(TAG,"inInCamera:"+String.valueOf(isInCamera));
                                mContext.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(mContext, "Client is not camera!" + "isInCamera:" + String.valueOf(isInCamera), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    }else {
                        Log.e(TAG,"NO FEEDBACK");
                    }

                }

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }


    }

}
