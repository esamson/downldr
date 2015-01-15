/*
 * Copyright Edward Samson
 */
package ph.samson.downldr;

import com.tumblr.jumblr.types.Photo;
import com.tumblr.jumblr.types.PhotoPost;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action1;
import static java.util.concurrent.TimeUnit.*;

public class App {

    private static final Logger log
            = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            String appName = System.getProperty("app.name");
            if (appName == null) {
                appName = "downldr";
            }
            System.err.printf("Usage: %s <blogName>%n", appName);
            System.exit(1);
        }

        String consumerKey = "FKl3mlwWIswWOTTB4CUC6qKVykjq5zdCg32g0UGRpLU0aUKJs5";
        String consumerSecret = "OL6ZXpDf9doA2KnNpZ1pyGh7Lw5E9wKSvfOhI2aQezVKF58JLR";
        String blogName = args[0];

        Downloader downloader = new Downloader();
        Observable.create(posts(consumerKey, consumerSecret, blogName))
                .ofType(PhotoPost.class)
                .subscribe(downloader);

        int failures = 0;
        for (Future<File> download : downloader.getDownloads()) {
            try {
                File f = download.get(10, MINUTES);
            } catch (Exception ex) {
                failures += 1;
                log.error("Download failure", ex);
            }
        }

        if (failures > 0) {
            log.error("{} failures encountered. Check logs.", failures);
        }

        downloader.stop();
        log.info("Done.");
    }

    private static Posts posts(String consumerKey, String consumerSecret, String blogName) {
        return new Posts(consumerKey, consumerSecret, blogName);
    }

    private static class Downloader implements Action1<PhotoPost> {

        private final ExecutorService executor
                = Executors.newFixedThreadPool(10);
        private final ArrayList<Future<File>> downloads = new ArrayList<>();
        private AtomicInteger total = new AtomicInteger();
        private AtomicInteger done = new AtomicInteger();

        public Downloader() {
        }

        @Override
        public void call(PhotoPost post) {
            final String prefix = new DateTime(post.getTimestamp() * 1000)
                    .toString("yyyy-MM-dd-HHmmss-");
            for (Photo photo : post.getPhotos()) {
                total.incrementAndGet();
                final String url = photo.getOriginalSize().getUrl();
                Future<File> download;
                download = executor.submit(new Callable<File>() {

                    @Override
                    public File call() throws Exception {
                        log.debug("downloading {}", url);
                        Path target = new File(prefix + url.substring(
                                url.lastIndexOf("/") + 1)).toPath();
                        long start = System.nanoTime();

                        try (BufferedInputStream is = new BufferedInputStream(
                                new URL(url).openStream())) {
                            Files.copy(is, target);
                        } catch (IOException ex) {
                            throw new IOException(
                                    "Failed downloading " + url, ex);
                        }

                        long finish = System.nanoTime();
                        int progress = done.incrementAndGet();
                        long millis = NANOSECONDS.toMillis(finish - start);

                        File targetFile = target.toFile();
                        long bytes = targetFile.length();
                        BigDecimal kBps = new BigDecimal(bytes).divide(
                                new BigDecimal(1024), 2, RoundingMode.UP)
                                .divide(new BigDecimal(millis).divide(
                                                new BigDecimal(1000), 2, RoundingMode.UP),
                                        2, RoundingMode.UP);
                        log.info("{}\n    {} bytes in {} ms ({} kB/s)    {}/{}",
                                target, bytes, millis, kBps, progress, total);

                        return targetFile;
                    }
                });

                downloads.add(download);
            }
        }

        public ArrayList<Future<File>> getDownloads() {
            return downloads;
        }

        public void stop() {
            executor.shutdown();
            try {
                executor.awaitTermination(1, MINUTES);
            } catch (InterruptedException ex) {
                log.error("Interrupted waiting for Downloader", ex);
            }
        }
    }
}
