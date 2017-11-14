package tso.chat;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * Created by reax on 10.11.17.
 */
class ConnectionHelperImpl implements ConnectionHelper {
    private CloseableHttpClient httpclient = HttpClients.createDefault();

    ConnectionHelperImpl() {

    }

    public CloseableHttpResponse doGet(HttpGet httpGet) {
        try {
            CloseableHttpResponse response = httpclient.execute(httpGet);
            return response;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public CloseableHttpResponse doPost(HttpPost httpPost) {
        try {
            CloseableHttpResponse response = httpclient.execute(httpPost);
            return response;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
