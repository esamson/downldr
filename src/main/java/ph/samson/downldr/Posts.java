/*
 * Copyright Edward Samson
 */
package ph.samson.downldr;

import com.tumblr.jumblr.JumblrClient;
import com.tumblr.jumblr.types.Post;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.observables.AbstractOnSubscribe;

public class Posts extends AbstractOnSubscribe<Post, Void> {

    private static final Logger log
            = LoggerFactory.getLogger(Posts.class);
    private static final int LIMIT = 20;

    private final String blogName;
    private final JumblrClient client;
    private final ArrayList<Post> posts;
    private int offset = 0;
    private boolean postsComplete = false;

    public Posts(String consumerKey, String consumerSecret, String blogName) {
        this.blogName = blogName;

        client = new JumblrClient(consumerKey, consumerSecret);
        posts = new ArrayList<>();
    }

    @Override
    protected void next(SubscriptionState<Post, Void> state) {
        final long index = state.calls();
        log.debug("next {}", index);

        if (index < offset) {
            state.onNext(posts.get((int) index));
        } else if (index == offset) {
            if (postsComplete) {
                state.onCompleted();
            } else {
                HashMap<String, Object> options = new HashMap<>();
                options.put("type", "photo");
                options.put("limit", LIMIT);
                options.put("offset", offset);

                log.debug("getting {} posts from index {}", LIMIT, offset);
                List<Post> blogPosts = client.blogPosts(blogName, options);
                posts.addAll(blogPosts);

                if (blogPosts.size() < LIMIT) {
                    postsComplete = true;
                    log.debug("all posts downloaded");
                }

                offset += blogPosts.size();
                state.onNext(posts.get((int) index));
            }
        } else {
            state.onCompleted();
        }
    }

}
