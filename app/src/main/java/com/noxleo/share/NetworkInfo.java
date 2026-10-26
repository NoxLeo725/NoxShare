package com.noxleo.share;

import android.content.Context;
import android.net.*;
import org.json.*;
import java.net.*;
import java.util.*;

public final class NetworkInfo {
    public static JSONObject read(Context context){
        JSONObject result=new JSONObject();JSONArray addresses=new JSONArray();
        try{
            result.put("connected",false);result.put("kind","未连接局域网");result.put("ip","—");result.put("gateway","—");result.put("prefix","—");result.put("interface","—");result.put("port",8787);
            ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            for(Network network:cm.getAllNetworks()){
                NetworkCapabilities cap=cm.getNetworkCapabilities(network);if(cap==null)continue;
                boolean wifi=cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),ethernet=cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);if(!wifi&&!ethernet)continue;
                LinkProperties lp=cm.getLinkProperties(network);if(lp==null)continue;
                String gateway="—";for(RouteInfo route:lp.getRoutes())if(route.isDefaultRoute()&&route.getGateway() instanceof Inet4Address&&!route.getGateway().isAnyLocalAddress()){gateway=route.getGateway().getHostAddress();break;}
                for(LinkAddress a:lp.getLinkAddresses())if(a.getAddress() instanceof Inet4Address&&!a.getAddress().isLoopbackAddress()){
                    JSONObject item=new JSONObject();item.put("ip",a.getAddress().getHostAddress());item.put("prefix",a.getPrefixLength());item.put("gateway",gateway);item.put("kind",wifi?"Wi-Fi":"有线网络");item.put("interface",lp.getInterfaceName());addresses.put(item);
                }
            }
            if(addresses.length()==0){Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();while(all.hasMoreElements()){
                NetworkInterface n=all.nextElement();String name=n.getName();if(!n.isUp()||n.isLoopback()||!(name.startsWith("wlan")||name.startsWith("ap")||name.startsWith("swlan")||name.startsWith("eth")))continue;
                for(InterfaceAddress a:n.getInterfaceAddresses())if(a.getAddress() instanceof Inet4Address&&a.getAddress().isSiteLocalAddress()){
                    JSONObject item=new JSONObject();item.put("ip",a.getAddress().getHostAddress());item.put("prefix",a.getNetworkPrefixLength());item.put("gateway","—");item.put("kind","本地网络 / 热点");item.put("interface",name);addresses.put(item);
                }
            }}
            if(addresses.length()>0){JSONObject first=addresses.getJSONObject(0);result.put("connected",true);for(String key:new String[]{"ip","prefix","gateway","kind","interface"})result.put(key,first.get(key));}
            result.put("addresses",addresses);
        }catch(Exception ignored){}
        return result;
    }
}
