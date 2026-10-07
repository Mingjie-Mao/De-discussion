package com.example.myapplication;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.widget.ImageView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RemoteImageLoaderTest {
    @Test public void rebindingSharesPendingDownloadAndKeepsDisplayedImageWithoutCrossScreenCache() throws Exception {
        Bitmap source=Bitmap.createBitmap(3200, 40, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.BLUE);
        ByteArrayOutputStream encoded=new ByteArrayOutputStream();
        source.compress(Bitmap.CompressFormat.PNG, 100, encoded); source.recycle();
        byte[] bytes=encoded.toByteArray();
        AtomicInteger reads=new AtomicInteger();
        CountDownLatch started=new CountDownLatch(1), release=new CountDownLatch(1);
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try (ServerSocket server=new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))) {
            worker.submit(() -> {
                try {
                    for(int i=0;i<2;i++) try(Socket socket=server.accept()) {
                        BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                        String line; while((line=input.readLine())!=null && !line.isEmpty()) {}
                        reads.incrementAndGet(); started.countDown(); release.await(5,TimeUnit.SECONDS);
                        OutputStream output=socket.getOutputStream();
                        output.write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nCache-Control: no-store\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        output.write(bytes); output.flush();
                    }
                } catch(Exception e) { throw new RuntimeException(e); }
            });
            Uri uri=Uri.parse("http://127.0.0.1:"+server.getLocalPort()+"/image");
            ImageView[] views=new ImageView[3];
            main(() -> {
                for(int i=0;i<views.length;i++) views[i]=new ImageView(ApplicationProvider.getApplicationContext());
                for(int i=0;i<20;i++) RemoteImageLoader.display(views[0],uri);
                RemoteImageLoader.display(views[1],uri);
            });
            assertTrue(started.await(3,TimeUnit.SECONDS)); assertEquals(1,reads.get());
            release.countDown(); awaitImage(views[0]); awaitImage(views[1]);
            main(() -> {
                Bitmap decoded=((BitmapDrawable)views[0].getDrawable()).getBitmap();
                assertTrue(decoded.getWidth()<=1600);
                for(int i=0;i<20;i++) RemoteImageLoader.display(views[0],uri);
                assertSame(decoded,((BitmapDrawable)views[0].getDrawable()).getBitmap());
                assertEquals(1,reads.get());
                // A new screen must fetch again so revoked public media is not cached.
                RemoteImageLoader.display(views[2],uri);
            });
            awaitImage(views[2]); assertEquals(2,reads.get());
        } finally { release.countDown(); worker.shutdownNow(); }
    }

    private static void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private static void awaitImage(ImageView view) throws Exception {
        for(int i=0;i<100;i++) {
            boolean[] loaded={false}; main(() -> loaded[0]=view.getDrawable()!=null);
            if(loaded[0]) return;
            Thread.sleep(30);
        }
        fail("Image did not load");
    }
}
