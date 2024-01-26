package com.example.pobieranie;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class DownloadService extends IntentService {
    private static final String ACTION_DOWNLOAD = "ACTION_DOWNLOAD";
    private static final String PARAMETER1 = "PARAMETER1";
    private NotificationManager notificationManager;
    private LocalBroadcastManager broadcastManager;
    private int downloadedBytes = 0;
    private static final String CHANNEL_ID = "CHANNEL_ID";
    private static final int MESSAGE_ID = 1;

    public DownloadService() {
        super("DownloadService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        this.notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        prepareNotificationChannel();
        broadcastManager = LocalBroadcastManager.getInstance(this);
        startForeground(MESSAGE_ID, createNotification(0));
        if (intent != null) {
            final String address = intent.getStringExtra(PARAMETER1);
            executeTask(address);
        } else {
            Log.e("intent_service", "unknown action");
        }
        Log.d("intent_service", "service performed the task");
    }

    private void executeTask(String adres) {
        FileOutputStream streamToFile = null;
        HttpsURLConnection conn = null;
        try {
            URL url = new URL(adres);
            conn = (HttpsURLConnection) url.openConnection();
            File workingFile = new File(url.getFile());
            File outputFile = new File(getBaseContext().getFilesDir().getPath() + File.separator + workingFile.getName());
            if (outputFile.exists()) {
                outputFile.delete();
            }
            DataInputStream reader = new DataInputStream(conn.getInputStream());
            streamToFile = new FileOutputStream(outputFile.getPath());
            int BLOCK_SIZE = 32767;
            byte cache[] = new byte[BLOCK_SIZE];
            int downloaded = reader.read(cache, 0, BLOCK_SIZE);
            while (downloaded != -1) {
                streamToFile.write(cache, 0, downloaded);
                this.downloadedBytes += downloaded;
                Log.d("DownloadService", "downloaded " + this.downloadedBytes + " bytes");
                downloaded = reader.read(cache, 0, BLOCK_SIZE);

                // Wysyłanie rozgłoszenia z informacjami o postępie
                ProgressInfo progressInfo = new ProgressInfo();
                progressInfo.mPobranychBajtow = this.downloadedBytes;
                progressInfo.mRozmiar = conn.getContentLength();
                progressInfo.mStatus = "IN_PROGRESS";

                int progressPercent = (int) ((double) this.downloadedBytes / conn.getContentLength() * 100);
                Notification notification = createNotification(progressPercent);
                notificationManager.notify(MESSAGE_ID, notification);

                sendNotification(progressInfo, this.downloadedBytes);
            }

            // Pobieranie zakończone sukcesem
            ProgressInfo progressInfo = new ProgressInfo();
            progressInfo.mPobranychBajtow = this.downloadedBytes;
            progressInfo.mRozmiar = conn.getContentLength();
            progressInfo.mStatus = "COMPLETED";
            sendNotification(progressInfo, this.downloadedBytes);
        } catch (Exception e) {
            e.printStackTrace();
            // Wystąpił błąd podczas pobierania
            ProgressInfo progressInfo = new ProgressInfo();
            progressInfo.mStatus = "ERROR";
            sendNotification(progressInfo, this.downloadedBytes);
        } finally {
            // Zamknięcie strumieni i połączenia
            try {
                if (streamToFile != null)
                    streamToFile.close();
                if (conn != null)
                    conn.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void prepareNotificationChannel() {
        this.notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW);
            this.notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(int progress) {
        Intent intentionNotifications = new Intent(this, MainActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(intentionNotifications);
        PendingIntent intentionAwaiting = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builderMessage = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.process))
                .setContentIntent(intentionAwaiting)
                .setSmallIcon(R.drawable.ic_cloud_download)
                .setProgress(100, progress, false) // Dodaj pasek postępu
                .setOngoing(true); // Ustaw powiadomienie jako trwałe (nieznika po kliknięciu)

        return builderMessage.build();
    }

    private void sendNotification(ProgressInfo progressInfo, int dwnloadedBytes) {
        Intent intent = new Intent();
        intent.setAction("com.example.pobieranie.ACTION_PROGRESS");
        intent.putExtra("com.example.pobieranie.EXTRA_PROGRESS_INFO", progressInfo);
        intent.putExtra("com.example.pobieranie.EXTRA_DOWNLOADED_BYTES", dwnloadedBytes);
        sendBroadcast(intent);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        // Aktualizacja powiadomienia
        int progressPercent = (int) ((double) progressInfo.mPobranychBajtow / progressInfo.mRozmiar * 100);
        Notification message = createNotification(progressPercent);
        notificationManager.notify(MESSAGE_ID, message);
    }

    public static void startDownloadFile(Context context, String address) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_DOWNLOAD);
        intent.putExtra(PARAMETER1, address);
        context.startService(intent);
    }
}
