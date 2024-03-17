package com.akto;

import com.akto.dao.context.Context;
import com.akto.dto.DependencyNode;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.*;
import com.akto.util.HTTPHeadersExample;
import com.akto.util.JSONUtils;
import com.akto.util.runtime.RuntimeUtil;
import com.akto.util.store.HashSetStore;
import com.akto.util.store.Store;
import com.mongodb.BasicDBObject;

import java.util.*;

import static com.akto.util.HttpRequestResponseUtils.extractValuesFromPayload;

public class DependencyAnalyserHelper {

    Store valueStore; // this is to store all the values seen in response payload
    Store urlValueStore; // this is to store all the url$value seen in response payload
    Store urlParamValueStore; // this is to store all the url$param$value seen in response payload

    Map<String, Set<String>> urlsToResponseParam = new HashMap<>();

    private Map<Integer, DependencyNode> nodes = new HashMap<>();
    public Map<Integer, APICatalog> dbState;


    public DependencyAnalyserHelper(Map<Integer, APICatalog> dbState) {
        valueStore = new HashSetStore(10_000);
        urlValueStore= new HashSetStore(10_000);
        urlParamValueStore = new HashSetStore(10_000);
        this.dbState = dbState;
    }

    public Map<Integer, DependencyNode> getNodes() {
        return this.nodes;
    }

    public void setNode(Map<Integer, DependencyNode> nodes) {
        this.nodes = nodes;
    }


    private void processRequestParam(String requestParam, Set<Object> reqFlattenedValuesSet, String originalCombinedUrl, boolean isUrlParam, boolean isHeader) {
        for (Object val : reqFlattenedValuesSet) {
            if (filterValues(val) && valueSeen(val)) {
                processValueForUrls(requestParam, val, originalCombinedUrl, isUrlParam, isHeader);
            }
        }
    }

    private void processValueForUrls(String requestParam, Object val, String originalCombinedUrl, boolean isUrlParam, boolean isHeader) {
        for (String url : urlsToResponseParam.keySet()) {
            if (!url.equals(originalCombinedUrl) && urlValSeen(url, val)) {
                processUrlForParam(url, requestParam, val, originalCombinedUrl, isUrlParam, isHeader);
            }
        }
    }

    private void processUrlForParam(String url, String requestParam, Object val, String originalCombinedUrl, boolean isUrlParam, boolean isHeader) {
        for (String responseParam : urlsToResponseParam.get(url)) {
            if (urlParamValueSeen(url, responseParam, val)) {
                updateNodesMap(url, responseParam, originalCombinedUrl, requestParam, isUrlParam, isHeader);
            }
        }
    }


    public boolean filterValues(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean) return false;
        if (val instanceof String) return val.toString().length() > 4 && val.toString().length() <= 4096;
        if (val instanceof Integer) return ((int) val) > 50;
        return true;
    }

    public boolean valueSeen(Object val) {
        return valueStore.contains(val.toString());
    }

    public boolean urlValSeen(String url, Object val) {
        return urlValueStore.contains(url + "$" + val.toString());
    }

    public boolean urlParamValueSeen(String url, String param, Object val) {
        return urlParamValueStore.contains(url + "$" + param + "$" + val.toString());
    }

    public void updateNodesMap(String combinedUrlResp, String paramResp, String combinedUrlReq, String paramReq, boolean isUrlParam, boolean isHeader) {
        String[] combinedUrlRespSplit = combinedUrlResp.split("#");
        String apiCollectionIdResp = combinedUrlRespSplit[0];
        String urlResp = combinedUrlRespSplit[1];
        String methodResp = combinedUrlRespSplit[2];

        String[] combinedUrlReqSplit = combinedUrlReq.split("#");
        String apiCollectionIdReq = combinedUrlReqSplit[0];
        String urlReq = combinedUrlReqSplit[1];
        String methodReq = combinedUrlReqSplit[2];

        DependencyNode.ParamInfo paramInfo = new DependencyNode.ParamInfo(paramReq, paramResp, 1, isUrlParam, isHeader);

        List<DependencyNode.ParamInfo> paramInfos = new ArrayList<>();
        paramInfos.add(paramInfo);
        DependencyNode dependencyNode = new DependencyNode(
                apiCollectionIdResp, urlResp, methodResp, apiCollectionIdReq, urlReq, methodReq, paramInfos, Context.now()
        );

        DependencyNode n1 = nodes.get(dependencyNode.hashCode());
        if (n1 != null) {
            n1.updateOrCreateParamInfo(paramInfo);
        } else {
            n1 = dependencyNode;
        }

        nodes.put(dependencyNode.hashCode(), n1);
    }

    public URLStatic realUrl(int apiCollectionId, URLStatic urlStatic) {
        APICatalog apiCatalog = this.dbState.get(apiCollectionId);
        if (apiCatalog == null) return urlStatic;

        Map<URLStatic, RequestTemplate> strictURLToMethods = apiCatalog.getStrictURLToMethods();
        boolean strictUrlFound = strictURLToMethods != null && strictURLToMethods.containsKey(urlStatic);
        if (strictUrlFound) return urlStatic;

        Map<URLTemplate, RequestTemplate> templateURLToMethods = apiCatalog.getTemplateURLToMethods();
        if (templateURLToMethods == null) return urlStatic;
        for (URLTemplate urlTemplate: templateURLToMethods.keySet()) {
            boolean match = urlTemplate.match(urlStatic);
            if (match) {
                return new URLStatic(urlTemplate.getTemplateString(), urlStatic.getMethod());
            }
        }

        return urlStatic;
    }

    public void mergeNodes() {
        List<DependencyNode> toBeDeleted = new ArrayList<>();
        Map<Integer, DependencyNode> toBeAdded = new HashMap<>();

        for (DependencyNode dependencyNode: nodes.values()) {
            String urlResp = dependencyNode.getUrlResp();
            String apiCollectionIdResp = dependencyNode.getApiCollectionIdResp();
            String methodResp = dependencyNode.getMethodResp();
            String newUrlResp = urlResp;
            if (!APICatalog.isTemplateUrl(urlResp)) {
                newUrlResp = realUrl(Integer.parseInt(apiCollectionIdResp), new URLStatic(urlResp, URLMethods.Method.valueOf(methodResp))).getUrl();
            }

            String urlReq = dependencyNode.getUrlReq();
            String apiCollectionIdReq = dependencyNode.getApiCollectionIdReq();
            String methodReq = dependencyNode.getMethodReq();
            String newUrlReq = urlReq;
            if (!APICatalog.isTemplateUrl(urlReq)) {
                newUrlReq = realUrl(Integer.parseInt(apiCollectionIdReq), new URLStatic(urlReq, URLMethods.Method.valueOf(methodReq))).getUrl();
            }

            // we try to check if any kind of merging happened or not
            // if yes fill the respective update lists
            if (!newUrlReq.equals(urlReq) || !newUrlResp.equals(urlResp)) {
                DependencyNode copy = dependencyNode.copy();
                copy.setUrlReq(newUrlReq);
                copy.setUrlResp(newUrlResp);

                toBeDeleted.add(dependencyNode);

                DependencyNode toBeAddedNode = toBeAdded.get(copy.hashCode());
                if (toBeAddedNode == null) {
                    toBeAddedNode = copy;
                } else {
                    toBeAddedNode.merge(copy);
                }

                toBeAdded.put(copy.hashCode(), toBeAddedNode);
            }
        }

        for (DependencyNode toBeDeletedNode: toBeDeleted ) {
            nodes.remove(toBeDeletedNode.hashCode());
        }

        for (DependencyNode toBeAddedNode: toBeAdded.values())  {
            int hashCode = toBeAddedNode.hashCode();
            DependencyNode node = nodes.get(hashCode);
            if (node == null) {
                nodes.put(hashCode,toBeAddedNode );
            } else {
                node.merge(toBeAddedNode);
            }
        }

    }

    public void analyse(HttpResponseParams responseParams, int finalApiCollectionId) {
        responseParams.requestParams.setApiCollectionId(finalApiCollectionId);

        if (!HttpResponseParams.validHttpResponseCode(responseParams.statusCode)) return;

        HttpRequestParams requestParams = responseParams.getRequestParams();
        String urlWithParams = requestParams.getURL();

        int apiCollectionId = requestParams.getApiCollectionId();
        String method = requestParams.getMethod();

        // get actual url (without any query params)
        URLStatic urlStatic = RuntimeUtil.getBaseURL(requestParams.getURL(), method);
        String url = urlStatic.getUrl();

        if (url.endsWith(".js") || url.endsWith(".png") || url.endsWith(".css") || url.endsWith(".jpeg") ||
                url.endsWith(".svg") || url.endsWith(".webp") || url.endsWith(".woff2")) return;

        // find real url. Real url is the one that is present in db. For example /api/books/1 is actually api/books/INTEGER
        url = realUrl(apiCollectionId, urlStatic).getUrl();

        String combinedUrl = apiCollectionId + "#" + url + "#" + method;

        // different URL variables and corresponding examples. Use accordingly
        // urlWithParams : /api/books/2?user=User1
        // url: api/books/INTEGER


        // Store response params in store
        String respPayload = responseParams.getPayload();
        Map<String, Set<Object>> respFlattened = extractValuesFromPayload(respPayload);

        Set<String> paramSet = urlsToResponseParam.getOrDefault(combinedUrl, new HashSet<>());
        for (String param: respFlattened.keySet()) {
            paramSet.add(param);
            for (Object val: respFlattened.get(param) ) {
                if (!filterValues(val)) continue;
                valueStore.add(val.toString());
                urlValueStore.add(combinedUrl + "$" + val);
                urlParamValueStore.add(combinedUrl + "$" + param + "$" + val);
            }
        }

        Map<String, List<String>> responseHeaders = responseParams.getHeaders();
        for (String param: responseHeaders.keySet()) {
            List<String> values = responseHeaders.get(param);
            if (param.equalsIgnoreCase("set-cookie")) {
                Map<String,String> cookieMap = RuntimeUtil.parseCookie(values);
                for (String cookieKey: cookieMap.keySet()) {
                    String cookieVal = cookieMap.get(cookieKey);
                    if (!filterValues(cookieVal)) continue;
                    paramSet.add(cookieKey);
                    valueStore.add(cookieVal);
                    urlValueStore.add(combinedUrl + "$" + cookieVal);
                    urlParamValueStore.add(combinedUrl + "$" + cookieKey + "$" + cookieVal);
                }
            } else {
                for (String val: values) {
                    if (!filterValues(val) || param.startsWith(":") || HTTPHeadersExample.responseHeaders.contains(param)) continue;
                    paramSet.add(param);
                    valueStore.add(val);
                    urlValueStore.add(combinedUrl + "$" + val);
                    urlParamValueStore.add(combinedUrl + "$" + param + "$" + val);
                }
            }
        }

        urlsToResponseParam.put(combinedUrl, paramSet);

        // Store url in Set

        // Check if request params in store
        //      a. Check if same value seen before
        //      b. Loop over previous urls and find which url had the value
        //      c. Loop over previous urls and params and find which param matches

        // analyse request payload
        BasicDBObject reqPayload = RequestTemplate.parseRequestPayload(requestParams, urlWithParams); // using urlWithParams to extract any query parameters
        Map<String, Set<Object>> reqFlattened = JSONUtils.flatten(reqPayload);

        for (String requestParam: reqFlattened.keySet()) {
            processRequestParam(requestParam, reqFlattened.get(requestParam), combinedUrl, false, false);
        }

        if (APICatalog.isTemplateUrl(url)) {
            String ogUrl = urlStatic.getUrl();
            String[] ogUrlSplit = ogUrl.split("/");
            URLTemplate urlTemplate = RuntimeUtil.createUrlTemplate(url, URLMethods.Method.fromString(method));
            for (int i = 0; i < urlTemplate.getTypes().length; i++) {
                SingleTypeInfo.SuperType superType = urlTemplate.getTypes()[i];
                if (superType == null) continue;
                int idx = ogUrl.startsWith("http") ? i:i+1;
                String s = ogUrlSplit[idx]; // because ogUrl=/api/books/123 while template url=api/books/INTEGER
                Set<Object> val = new HashSet<>();
                val.add(s);
                processRequestParam(i+"", val, combinedUrl, true, false);
            }
        }

        Map<String, List<String>> requestHeaders = requestParams.getHeaders();
        for (String param: requestHeaders.keySet()) {
            if (param.startsWith(":") || HTTPHeadersExample.requestHeaders.contains(param)) continue;
            List<String> values = requestHeaders.get(param);

            if (param.equals("cookie")) {
                Map<String,String> cookieMap = RuntimeUtil.parseCookie(values);
                for (String cookieKey: cookieMap.keySet()) {
                    String cookieValue = cookieMap.get(cookieKey);
                    processRequestParam(cookieKey, new HashSet<>(Collections.singletonList(cookieValue)), combinedUrl, false, true);
                }
            } else {
                Set<Object> valuesSet = new HashSet<>();
                for (String v: values) {
                    String[] vArr = v.split(" ");
                    if (vArr.length == 2 && Arrays.asList("bearer", "basic").contains(vArr[0].toLowerCase())) {
                        valuesSet.add(vArr[1]);
                    } else {
                        valuesSet.add(v);
                    }
                }
                processRequestParam(param, valuesSet, combinedUrl, false, true);
            }

        }
    }

}
