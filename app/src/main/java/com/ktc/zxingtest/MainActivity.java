package com.ktc.zxingtest;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private Button readButton;
    private static final int REQUEST_EXTERNAL_STORAGE = 200;
    private ProgressDialog mProgress;
    private Bitmap scanBitmap;
    private TextView message;
    private ExecutorService executorService = Executors.newFixedThreadPool(5);
    private boolean isStop = true;
    private MainHandler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        addListener();
    }

    private void initView() {
        readButton = findViewById(R.id.read);
        message = findViewById(R.id.message);
    }

    private void addListener() {
        handler = new MainHandler(this);
        readButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                verifyStoragePermissions();
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, 100);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK) {
            handleAlbumPic(data);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {

            }
        }).start();
    }

    /*
      这里只能用从相册获取图片，从其他路径获取到的都没法解析路径 !!!
     */
    public String getRealPath(Uri uri) {
        String result;
        Cursor cursor = getContentResolver().query(uri,
                new String[]{MediaStore.Images.ImageColumns.DATA},//
                null, null, null);
        if (cursor == null) {
            result = uri.getPath();
        } else {
            cursor.moveToFirst();
            int index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(index);
            cursor.close();
        }
        return result;
    }

    private void verifyStoragePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) { // 申请权限
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_EXTERNAL_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_EXTERNAL_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("dingyl", "授权成功");
                } else {
                    Toast.makeText(this, "授权失败，请在设置中打开文件读写权限", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isStop = true;
    }

    private void handleAlbumPic(Intent data) { //获取选中图片的路径
        final Uri uri = data.getData();
        mProgress = new ProgressDialog(MainActivity.this);
        mProgress.setMessage("正在扫描...");
        mProgress.setCancelable(false);
        mProgress.show();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Result result = scanningImage(uri);
                mProgress.dismiss();
                if (result != null) {
                    /*Intent resultIntent = new Intent();
                    Bundle bundle = new Bundle();
                    bundle.putString(INTENT_EXTRA_KEY_QR_SCAN, result.getText());
                    resultIntent.putExtras(bundle);
                    MainActivity.this.setResult(RESULT_OK, resultIntent);
                    finish();*/
                    message.setText(result.getText());
                    executorService.execute(saveMessage);
                    executorService.execute(sendMessage);
                } else {
                    Toast.makeText(MainActivity.this, "识别失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public Result scanningImage(Uri uri) {
        if (uri == null) {
            return null;
        }
        String path = "";
        try{
            path = getRealPath(uri);
        }catch (Exception e){
            e.printStackTrace();
        }
        if (path == null || path.equals("")){
            Toast.makeText(MainActivity.this,getString(R.string.path_error_tips),Toast.LENGTH_SHORT).show();
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; // 先获取原大小
        scanBitmap = BitmapFactory.decodeFile(path, options);
        options.inJustDecodeBounds = false;
        int sampleSize = (int) (options.outHeight / (float) 200);
        if (sampleSize <= 0) sampleSize = 1;
        options.inSampleSize = sampleSize;
        scanBitmap = BitmapFactory.decodeFile(path, options);
        Hashtable<DecodeHintType, String> hints = new Hashtable<>();
        hints.put(DecodeHintType.CHARACTER_SET, "UTF8"); //设置二维码内容的编码
        // scanBitmap = BitmapUtil.decodeUri(this, uri, 500, 500);
        int data[] = new int[scanBitmap.getWidth() * scanBitmap.getHeight()];
        scanBitmap.getPixels(data, 0, scanBitmap.getWidth(), 0, 0
                , scanBitmap.getWidth(), scanBitmap.getHeight());//这句必须要加，否则扫描失败 ！！！
        RGBLuminanceSource source = new RGBLuminanceSource(scanBitmap.getWidth(), scanBitmap.getHeight(), data);
        BinaryBitmap bitmap1 = new BinaryBitmap(new HybridBinarizer(source));
        QRCodeReader reader = new QRCodeReader();
        try {
            return reader.decode(bitmap1, hints);
        } catch (NotFoundException | ChecksumException | FormatException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Runnable saveMessage = new Runnable() {
        @Override
        public void run() {
            String pathName = getExternalCacheDir().getAbsolutePath()
                    + "/cache.txt";
            File file = new File(pathName);
            try {
                FileOutputStream outputStream = new FileOutputStream(file, true);
                outputStream.write(message.getText().toString().getBytes());
                outputStream.write("\n".getBytes());
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    private Runnable sendMessage = new Runnable() {
        @Override
        public void run() {
            try{
                /*final BufferedReader br = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));*/
                Socket socket = new Socket("192.168.47.80",2001);
                socket.setSoTimeout(0);
                OutputStream outputStream = socket.getOutputStream();
                final BufferedReader br = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                String text = message.getText().toString();
                outputStream.write(text.getBytes());
                outputStream.flush();
                socket.shutdownOutput(); //通知服务端output已经结束，不加的话无法获取到服务端回传数据!!!
                isStop = false;
                Message msg = handler.obtainMessage();
                msg.what = 1;
                Bundle bundle = new Bundle();
                bundle.putString("result",br.readLine());
                msg.setData(bundle);
                handler.sendMessage(msg);
                outputStream.close();
                br.close();
                socket.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    };

    private class ReceiveThread extends Thread{
        Socket socket;
        ReceiveThread(Socket socket){
            this.socket = socket;
        }
        @Override
        public void run() {
            try{
                while (!isStop){
                    Log.d("dingyl","1111");
                    InputStream inputStream = socket.getInputStream();
                    byte[] buffer = new byte[1024];
                    int length;
                    StringBuilder stringBuilder = new StringBuilder();
                    Log.d("dingyl","2222");
                    while ((length = inputStream.read(buffer))!= -1){
                        stringBuilder.append(new String(buffer,0,length));
                    }
                    Log.d("dingyl","4444");
                    Log.d("dingyl","socket result : " + stringBuilder.toString());
                    inputStream.close();
                }
            }catch (IOException e){
                Log.d("dingyl","exception : " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    static class MainHandler extends Handler{
        WeakReference<MainActivity> reference;
        MainActivity activity;

        MainHandler(MainActivity activity){
            reference = new WeakReference<>(activity);
            this.activity = reference.get();
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Toast.makeText(activity,msg.getData().getString("result"),Toast.LENGTH_SHORT).show();
        }
    }
}
