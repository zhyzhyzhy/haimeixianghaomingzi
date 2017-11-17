package org.ink.web.http;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.ink.web.WebContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Response Builder
 * 建造者模式
 *
 * @author zhuyichen 2017-7-17
 */
public class Response {

    /**
     * the response body
     */
    private Object body;

    /**
     * the response http status
     * the default is not found
     */
    private HttpResponseStatus responseStatus = HttpResponseStatus.NOT_FOUND;

    /**
     * the headers of the response
     * except set-cookie header
     * because set-cookie can set many times, so i set it in {@code Set<Cookies>}
     */
    private Map<String, String> headers = new HashMap<>();

    /**
     * the cookies
     * lazy init
     */
    private Set<Cookie> cookies;

    public Response() {

    }

    public Response(Request request) {
        if (request.cookies() != null) {
            String sessionId = request.cookies().getOrDefault("sessionid", null);
            HttpSession session = null;
            //if sessionid is null or Manager not contains sessionid
            //then create one new http session
            if (sessionId == null || !SessionManager.containsSession(sessionId)) {
                sessionId = SessionManager.createSessionId();
                SessionManager.addSession(sessionId, request.channel());
                session = SessionManager.getSession(sessionId);
                addCookie("sessionid", sessionId);
            } else {
                session = SessionManager.getSession(sessionId);
                //if session hasExpired
                if (session.hasExpires()) {
                    sessionId = SessionManager.createSessionId();
                    SessionManager.addSession(sessionId, request.channel());
                    session = SessionManager.getSession(sessionId);
                    addCookie("sessionid", sessionId);
                }
            }
            //set to WebContext
            WebContext.setCurrentSession(session);
        }
    }

    /**
     * add one new cookie in response
     */
    public boolean addCookie(String name, String value) {
        if (cookies == null) {
            cookies = new HashSet<>();
        }
        return cookies.add(new Cookie(name, value));
    }

    public boolean addCookie(Cookie cookie) {
        return cookies.add(cookie);
    }

    /**
     * get all cookies
     */
    public Set<Cookie> cookies() {
        return cookies;
    }

    /**
     * @return all current headers in the response
     * except set-cookie
     */
    public Map<String, String> headers() {
        return headers;
    }

    /**
     * add one new header
     */
    public void header(String header, String value) {
        headers.putIfAbsent(header, value);
    }


    private Response(Object body, HttpResponseStatus responseStatus, Map<String, String> headers, Set<Cookie> cookies) {
        this.body = body;
        this.responseStatus = responseStatus;
        this.headers = headers;
        this.cookies = cookies;
    }


    /**
     * get and set response body
     */
    public Object body() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    /**
     * get and set response http status
     *
     * @return
     */
    public HttpResponseStatus responseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(HttpResponseStatus responseStatus) {
        this.responseStatus = responseStatus;
    }


    /**
     * the builder of the response
     */
    public static Builder ok() {
        return status(HttpResponseStatus.OK);
    }

    public static Builder badRequest() {
        return status(HttpResponseStatus.BAD_REQUEST);
    }


    public static Builder status(HttpResponseStatus status) {
        return new Builder(status);
    }

    public static class Builder {
        private HttpResponseStatus status = HttpResponseStatus.OK;
        private Object body;
        private Map<String, String> headers;
        private Set<Cookie> cookies;

        public Builder(HttpResponseStatus status) {
            this.status = status;
        }

        public Builder body(Object o) {
            this.body = o;
            return this;
        }

        public Response build() {
            Map<String, String> map = new HashMap<>();
            if (headers != null) {
                headers.keySet().forEach(s -> map.putIfAbsent(s, headers.get(s)));
            }
            return new Response(body, status, map, cookies);
        }

        public Builder header(String header, String value) {
            if (headers == null) {
                headers = new HashMap<>();
            }
            headers.putIfAbsent(header, value);
            return this;
        }

        public Builder cookie(String name, String value) {
            if (cookies == null) {
                cookies = new HashSet<>();
            }
            cookies.add(new Cookie(name, value));
            return this;
        }

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Response response = (Response) o;

        return new EqualsBuilder()
                .append(body, response.body)
                .append(responseStatus, response.responseStatus)
                .append(headers, response.headers)
                .append(cookies, response.cookies)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(body)
                .append(responseStatus)
                .append(headers)
                .append(cookies)
                .toHashCode();
    }

    public static Response mergeResponse(Response response1, Response response2) {
        //TODO
        response1.headers.forEach(response2::header);
        return response2;
    }


    /**
     * build response like 404 304 without response body
     */
    public static DefaultFullHttpResponse buildDefaultFullHttpResponse(HttpResponseStatus status) {

        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);

        Response currentResponse = WebContext.currentResponse();

        response.headers().add(HttpHeader.CONTENT_LENGTH, "0");
        if (currentResponse.cookies != null) {
            response.headers().add(HttpHeader.SET_COOKIE, currentResponse.cookies.stream()
                    .map(Cookie::toString)
                    .collect(Collectors.toList()));
        }
        for (String head : currentResponse.headers.keySet()) {
            response.headers().add(head, currentResponse.headers.get(head));
        }
        return response;
    }

    /**
     * convert response to DefaultFullHttpResponse
     */
    public DefaultFullHttpResponse buildDefaultFullHttpResponse() {
        DefaultFullHttpResponse fullHttpResponse = null;

        //set body
        if (this.body() == null) {
            fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, this.responseStatus());
        } else {
            fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, this.responseStatus(), Unpooled.copiedBuffer(JSON.toJSONString(body()).getBytes()));
        }

        //set headers
        for (String s : this.headers().keySet()) {
            fullHttpResponse.headers().add(s, headers.get(s));
        }

        //set content type if has not been done
        if (!fullHttpResponse.headers().contains("Content-type")) {
            if (body instanceof String) {
                fullHttpResponse.headers().set(HttpHeader.CONTENT_TYPE, "html/text");
            } else {
                fullHttpResponse.headers().set(HttpHeader.CONTENT_TYPE, "application/json;charset=utf-8");
            }
        }

        //set set-cookie header
        if (cookies() != null) {
            fullHttpResponse.headers().add(HttpHeader.SET_COOKIE, cookies().stream()
                    .map(Cookie::toString)
                    .collect(Collectors.toList()));
        }
        fullHttpResponse.headers().set(HttpHeader.CONNECTION, "keep-alive");
        fullHttpResponse.headers().add("Content-Length", fullHttpResponse.content().array().length);

        return fullHttpResponse;
    }

    public static DefaultFullHttpResponse buildDefaultFullHttpResponse0() {
        return WebContext.currentResponse().buildDefaultFullHttpResponse();
    }


}
