package kornell.api.client;

import static kornell.core.util.StringUtils.isSome;
import static kornell.core.util.StringUtils.mkurl;

import java.util.Date;
import java.util.logging.Logger;

import com.github.gwtbootstrap.client.ui.constants.AlertType;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.Window;

import kornell.core.entity.AuthClientType;
import kornell.gui.client.util.ClientProperties;
import kornell.gui.client.util.view.KornellNotification;

public class RESTClient {

    Logger logger = Logger.getLogger(RESTClient.class.getName());

    private String apiURL = "/api";

    public String getApiUrl() {
        return apiURL;
    }

    protected ExceptionalRequestBuilder GET(String... path) {
        String url = mkurl(getApiUrl(), path);
        url = appendTimestampIfIE(url);
        ExceptionalRequestBuilder reqBuilder = new ExceptionalRequestBuilder(RequestBuilder.GET, url);
        setAuthenticationHeaders(reqBuilder);
        return reqBuilder;
    }

    protected ExceptionalRequestBuilder HEAD(String... path) {
        String url = mkurl(getApiUrl(), path);
        ExceptionalRequestBuilder reqBuilder = new ExceptionalRequestBuilder(RequestBuilder.HEAD, url);
        setAuthenticationHeaders(reqBuilder);
        return reqBuilder;
    }

    protected ExceptionalRequestBuilder PUT(String... path) {
        String url = mkurl(getApiUrl(), path);
        ExceptionalRequestBuilder reqBuilder = new ExceptionalRequestBuilder(RequestBuilder.PUT, url);
        setAuthenticationHeaders(reqBuilder);
        return reqBuilder;
    }

    protected ExceptionalRequestBuilder POST(String... path) {
        String url = mkurl(getApiUrl(), path);
        ExceptionalRequestBuilder reqBuilder = new ExceptionalRequestBuilder(RequestBuilder.POST, url);
        setAuthenticationHeaders(reqBuilder);
        return reqBuilder;
    }

    protected ExceptionalRequestBuilder POST_LOGIN(String username, String password, String institutionUUID,
            String... path) {
        String url = mkurl(getApiUrl(), path);
        ExceptionalRequestBuilder reqBuilder = new ExceptionalRequestBuilder(RequestBuilder.POST, url);
        reqBuilder.setHeader("Content-Type", "application/x-www-form-urlencoded");

        StringBuilder sb = new StringBuilder();
        sb.append("userkey=" + username + "&");
        sb.append("password=" + password + "&");
        sb.append("institutionUUID=" + institutionUUID + "&");
        sb.append("clientType=" + AuthClientType.web.toString());

        reqBuilder.setRequestData(sb.toString());
        return reqBuilder;
    }

    protected ExceptionalRequestBuilder DELETE(String... path) {
        String url = mkurl(getApiUrl(), path);
        ExceptionalRequestBuilder reqBuilder = new ExceptionalRequestBuilder(RequestBuilder.DELETE, url);
        setAuthenticationHeaders(reqBuilder);
        return reqBuilder;
    }

    protected void setAuthenticationHeaders(ExceptionalRequestBuilder reqBuilder) {
        String auth = ClientProperties.get(ClientProperties.X_KNL_TOKEN);
        if (isSome(auth)) {
            reqBuilder.setHeader(ClientProperties.X_KNL_TOKEN, auth);
        }
    }

    public void locationAssign(String... path) {
        if (Cookies.isCookieEnabled()) {
            ClientProperties.setCookie(ClientProperties.X_KNL_TOKEN, ClientProperties.get(ClientProperties.X_KNL_TOKEN),
                    new Date(new Date().getTime() + 5000));
            String url = appendTimestampIfIE(mkurl(getApiUrl(), path));
            Window.open(url, "_blank", "");
        } else {
            KornellNotification.show(
                    "Por motivos de segurança, é necessário que os cookies estejam ativados para esta operação. Entre em contato com o suporte caso tenha alguma dúvida.",
                    AlertType.ERROR, 10000);
        }
    }

    public String appendTimestampIfIE(String url) {
        if ((Window.Navigator.getUserAgent().toLowerCase().indexOf("trident/") != -1))
            return url + (url.indexOf("?") == -1 ? "?" : "&") + "t=" + (new Date()).getTime();
        return url;
    }
}