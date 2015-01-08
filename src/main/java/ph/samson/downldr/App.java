/*
 * Copyright Edward Samson
 */
package ph.samson.downldr;

import com.tumblr.jumblr.types.Photo;
import com.tumblr.jumblr.types.PhotoPost;
import com.tumblr.jumblr.types.PhotoSize;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

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

        final ArrayList<Future<File>> downloads = new ArrayList<>();
        Observable.create(posts(consumerKey, consumerSecret, blogName))
                .ofType(PhotoPost.class)
                .flatMap(photos())
                .map(originalSize())
                .map(urls())
                .subscribe(new Action1<String>() {

                    private final ExecutorService executor
                    = Executors.newFixedThreadPool(10);

                    @Override
                    public void call(final String url) {
                        Future<File> download = executor.submit(new Callable<File>() {

                            @Override
                            public File call() throws Exception {
                                log.debug("downloading {}", url);
                                try (BufferedInputStream is = new BufferedInputStream(
                                        new URL(url).openStream())) {
                                            Path target = new File(url.substring(
                                                            url.lastIndexOf("/") + 1)).toPath();
                                            Files.copy(is, target);
                                            return target.toFile();
                                        } catch (IOException ex) {
                                            log.error("Download failure: " + url, ex);
                                            throw ex;
                                        }
                            }
                        });

                        downloads.add(download);
                    }
                });

        int failures = 0;
        for (Future<File> download : downloads) {
            try {
                File f = download.get(10, TimeUnit.MINUTES);
            } catch (Exception ex) {
                failures += 1;
            }
        }

        if (failures > 0) {
            log.error("{} failures encountered. Check logs.", failures);
        }

        log.info("Done.");
    }

    private static Func1<PhotoSize, String> urls() {
        return new Func1<PhotoSize, String>() {
            @Override
            public String call(PhotoSize t1) {
                return t1.getUrl();
            }
        };
    }

    private static Func1<Photo, PhotoSize> originalSize() {
        return new Func1<Photo, PhotoSize>() {
            @Override
            public PhotoSize call(Photo t1) {
                return t1.getOriginalSize();
            }
        };
    }

    private static Func1<PhotoPost, Observable<Photo>> photos() {
        return new Func1<PhotoPost, Observable<Photo>>() {
            @Override
            public Observable<Photo> call(PhotoPost t1) {
                return Observable.from(t1.getPhotos());
            }
        };
    }

    private static Posts posts(String consumerKey, String consumerSecret, String blogName) {
        return new Posts(consumerKey, consumerSecret, blogName);
    }

}
