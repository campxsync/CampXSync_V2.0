package com.campx.iadm.billing.controller;
import com.campx.iadm.billing.exception.*;
import com.campx.iadm.billing.security.*;
import com.campx.iadm.billing.service.Iadm04DomainService;
import com.sun.net.httpserver.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class Iadm04Controller implements HttpHandler {
  private final Iadm04DomainService service;
  private final GatewayHmacVerifier hmac = new GatewayHmacVerifier();
  public Iadm04Controller(Iadm04DomainService service){ this.service=service; }
  public void handle(HttpExchange exchange) throws IOException {
    String method=exchange.getRequestMethod(); String path=exchange.getRequestURI().getPath();
    byte[] body=readAll(exchange.getRequestBody());
    try {
      if("OPTIONS".equalsIgnoreCase(method)){ exchange.sendResponseHeaders(204,-1); return; }
      GatewayHmacVerifier.VerificationResult auth=hmac.verify(exchange,method,path,body);
      if(!auth.isSuccess()){ send(exchange,auth.getStatus(), "{\"error\":\""+auth.getErrorCode()+"\"}"); return; }
      UserSecurityContext ctx = UserSecurityContext.fromHeaders(auth.getUserId(), auth.getTenantId()==null||auth.getTenantId().isEmpty()?"a0000000-0000-0000-0000-000000000001":auth.getTenantId());
      if("GET".equalsIgnoreCase(method) && path.endsWith("/health")){ send(exchange,200, "{\"status\":\"UP\",\"service\":\"IADM-04\"}"); return; }
      if("GET".equalsIgnoreCase(method) && path.contains("/entitlements/check")){
        String q = exchange.getRequestURI().getQuery();
        String key = null; String tenant = null;
        if (q != null) for (String p : q.split("&")) { String[] kv=p.split("=",2); if(kv.length==2){ if("key".equals(kv[0])) key=kv[1]; if("tenantId".equals(kv[0])) tenant=kv[1]; } }
        java.util.UUID tid = tenant==null||tenant.isEmpty()?null:java.util.UUID.fromString(tenant);
        java.util.Map<String,Object> r = service.checkEntitlement(ctx, tid, key);
        StringBuilder sb=new StringBuilder("{"); boolean first=true;
        for (java.util.Map.Entry<String,Object> e:r.entrySet()){ if(!first) sb.append(','); first=false; sb.append('"').append(e.getKey()).append("\":");
          Object v=e.getValue(); if(v==null) sb.append("null"); else if(v instanceof Number || v instanceof Boolean) sb.append(v); else sb.append('"').append(String.valueOf(v).replace("\"","\\\"")).append('"'); }
        sb.append('}');
        send(exchange,200, sb.toString()); return;
      }
      send(exchange,404, "{\"error\":\"IADM04_NOT_FOUND\"}");
    } catch(Exception e){ send(exchange,500, "{\"error\":\""+e.getMessage().replace("\"","'")+"\"}"); }
  }
  private static void send(HttpExchange ex,int st,String json) throws IOException {
    byte[] b=json.getBytes(StandardCharsets.UTF_8); ex.getResponseHeaders().set("Content-Type","application/json");
    ex.sendResponseHeaders(st,b.length); try(OutputStream os=ex.getResponseBody()){ os.write(b);} }
  private static byte[] readAll(InputStream in) throws IOException { ByteArrayOutputStream bos=new ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n; while((n=in.read(buf))>=0) bos.write(buf,0,n); return bos.toByteArray(); }
}