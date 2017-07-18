package com.noname.web.route;



import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.noname.web.annotation.*;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by zhuyichen on 2017/7/12.
 */
public class RouteFinder {
    private static Map<Pattern, Route> routeMap = new HashMap<>();

    public static Pattern pathCompiler(String path, Method method) {
        Parameter[] parameters = method.getParameters();
        for (Parameter parameter : parameters) {
            if (parameter.getAnnotations() == null) {
                continue;
            }
            Annotation annotation = parameter.getAnnotations()[0];
            if (annotation instanceof PathVariable) {
                if (parameter.getType() == String.class) {
                    path = path.replace("{" + parameter.getName()+"}","[0-9\\d\\D]*");
                }
                else if (parameter.getType() == Integer.class
                        || parameter.getType() == Long.class) {

                    path = path.replace("{" + parameter.getName()+"}","[0-9]*");
                }

            }
        }
        return Pattern.compile(path);
    }

    public static void addRouter(Pattern pattern, Route route) {
        routeMap.put(pattern,route);
    }


    public static Route findRoute(FullHttpRequest fullHttpRequest) {

        String path = fullHttpRequest.uri();
        HttpMethod method = fullHttpRequest.method();


        //如果有params，找到问号的位置
        int splitIndex = path.indexOf('?');
        //如果没有问号，则index为请求uri的长度
        if (splitIndex == -1) {
            splitIndex = path.length();
        }

        //遍历map
        for (Pattern pattern : routeMap.keySet()) {
            //如果找到uri匹配的
            if (pattern.matcher(path.substring(0, splitIndex)).matches()) {
                Route route = routeMap.get(pattern);
                //如果请求方式一样
                if (route.getHttpMethod().equals(method)) {

                    //设置@PathVariable
                    routePathVariableSetter(path, route);

                    //设置@PathParams
                    routePathParamsSetter(path, route);

                    //设置@RequestJson
                    if (fullHttpRequest.headers().get("content-Type").equals("application/json")) {
                        routeRequestJsonSetter(fullHttpRequest.content().copy().toString(CharsetUtil.UTF_8), route);
                    }

                    return route;
                }
            }
        }
        return null;
    }

    //处理@RequestJson参数
    public static void routeRequestJsonSetter(String json, Route route) {
        Method method = route.getMethod();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Annotation[] annotations = parameters[i].getAnnotations();
            for (Annotation annotation : annotations) {
                if (annotation instanceof RequestJson) {
                    Class<?> class1 = parameters[i].getType();
                    System.out.println(class1.getName());
                    Object object = JSON.parseObject(json, class1);
                    route.getParamters()[i] = object;
                }
            }
        }
    }


    //处理@PathVariable参数
    public static void routePathVariableSetter(String path, Route route) {
        Method method = route.getMethod();
        Annotation annotation = method.getAnnotations()[0];
        String uri = "";


        if (annotation instanceof GET) {
            uri = ((GET)annotation).value();
        }
        if (annotation instanceof POST) {
            uri = ((POST)annotation).value();
        }
        String[] requestPaths = path.split("/");
        String[] originPath = uri.split("/");

        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < requestPaths.length; i++) {
            if (!requestPaths[i].equals(originPath[i])) {
                for (int j = 0; j < parameters.length; j++) {
                    if (parameters[j].getName().equals(originPath[i].substring(1, originPath[i].length() - 1))) {
                        route.getParamters()[j] = requestPaths[i];
                    }
                }
            }
        }
    }

    //处理@RequestParam参数
    public static void routePathParamsSetter(String path, Route route) {
        QueryStringDecoder decoder = new QueryStringDecoder(path);
        Map<String, List<String>> map = decoder.parameters();
        Parameter[] parameters = route.getMethod().getParameters();

        for (int i = 0; i < parameters.length; i++) {
            Annotation[] annotations = parameters[i].getAnnotations();
            for (Annotation annotation : annotations) {
                if (annotation instanceof RequestParam) {
                    List<String> list = map.get(parameters[i].getName());
                    if (list == null || list.size() == 0) {
                        route.getParamters()[i] = null;
                    }
                    else {
                        route.getParamters()[i] = list.get(0);
                    }
                }
            }
        }
    }

}
