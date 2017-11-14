package tso.chat;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;

/**
 * Created by reax on 11.11.17.
 */
public interface ConnectionHelper {
    CloseableHttpResponse doGet(HttpGet httpGet);
    CloseableHttpResponse doPost(HttpPost httpPost);

}
