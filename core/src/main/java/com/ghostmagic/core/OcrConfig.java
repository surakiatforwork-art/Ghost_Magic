package com.ghostmagic.core;

import java.util.Map;
import java.util.regex.Pattern;

/** Shared, platform-independent OCR configuration. Region coordinates are percentages. */
public record OcrConfig(String source,String imageUri,String outputVariable,String script,Region region,int rotation) {
    public record Region(int x,int y,int width,int height) {}
    private static final Pattern TEMPLATE=Pattern.compile("\\$\\{([^{}]+)\\}");
    public static OcrConfig parse(Map<String,String> config) throws ActionException {
        validate(config,false);
        return new OcrConfig(config.get("source"),config.getOrDefault("imageUri",""),config.get("outputVariable"),config.get("script"),parseRegion(config.get("region")),Integer.parseInt(config.get("rotation")));
    }
    public static void validate(Map<String,String> c,boolean templates) throws ActionException {
        String source=value(c,"source"), output=value(c,"outputVariable"),script=value(c,"script"),region=value(c,"region"),rotation=value(c,"rotation");
        if(!dynamic(source,templates)&&!source.equals("image")&&!source.equals("screen"))fail("source must be image or screen");
        if(source.equals("image"))value(c,"imageUri");
        if(!dynamic(output,templates)&&(!output.matches("[A-Za-z_][A-Za-z0-9_.]*")||output.equals("error.code")||output.equals("error.message")))fail("outputVariable must be a variable name outside reserved error fields");
        if(!dynamic(script,templates)&&!script.equals("latin"))fail("script must be latin");
        if(!dynamic(region,templates))parseRegion(region);
        if(!dynamic(rotation,templates)&&!rotation.matches("0|90|180|270"))fail("rotation must be 0, 90, 180 or 270");
    }
    private static boolean dynamic(String value,boolean templates){return templates&&TEMPLATE.matcher(value).find();}
    private static String value(Map<String,String> c,String key)throws ActionException {String value=c.get(key);if(value==null||value.trim().isEmpty())fail("Missing "+key);return value;}
    private static Region parseRegion(String value)throws ActionException {
        if(value.equals("full"))return new Region(0,0,100,100);
        String[] parts=value.split(",",-1);
        try {
            if(parts.length!=4)throw new NumberFormatException();
            int x=Integer.parseInt(parts[0].trim()),y=Integer.parseInt(parts[1].trim()),w=Integer.parseInt(parts[2].trim()),h=Integer.parseInt(parts[3].trim());
            if(x<0||y<0||x>100||y>100||w<=0||h<=0||w>100||h>100||x+w>100||y+h>100)throw new NumberFormatException();
            return new Region(x,y,w,h);
        }catch(NumberFormatException ex){fail("region must be full or x,y,width,height percentages inside 0..100");return null;}
    }
    private static void fail(String message)throws ActionException{throw new ActionException(ActionException.INVALID_CONFIG,message);}
}
