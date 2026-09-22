import java.awt.Font;import java.awt.font.FontRenderContext;import java.awt.geom.*;import java.nio.file.*;
class BrandVectors {
 static Font font;static String path(String text,float size,float x,float y) {
 var shape=font.deriveFont(size).createGlyphVector(new FontRenderContext(null,true,true),text).getOutline(x,y);
 var it=shape.getPathIterator(null);StringBuilder b=new StringBuilder();double[] p=new double[6];
 while(!it.isDone()){int t=it.currentSegment(p);String op=switch(t){case 0->"M";case 1->"L";case 2->"Q";case 3->"C";default->"Z";};b.append(op);int n=switch(t){case 0,1->2;case 2->4;case 3->6;default->0;};for(int i=0;i<n;i++)b.append(String.format(java.util.Locale.ROOT,"%.3f",p[i])).append(i%2==0?",":" ");it.next();}return b.toString();}
 static String glyph(String t,float size,float x,float y,String color){return "<path android:fillColor=\""+color+"\" android:pathData=\""+path(t,size,x,y)+"\"/>";}
 public static void main(String[]args)throws Exception{
 font=Font.createFont(Font.TRUETYPE_FONT,new java.io.File("/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf"));
 var out=Path.of("app/src/main/res/drawable");Files.createDirectories(out);
 for(boolean dark:new boolean[]{false,true}){String key=dark?"dark":"light",ink=dark?"#F2F2F2":"#201F1D",bg=dark?"#201F1D":"#F2F2F2";
 String header="<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"108dp\" android:height=\"108dp\" android:viewportWidth=\"108\" android:viewportHeight=\"108\">";
 String body="<path android:fillColor=\""+bg+"\" android:pathData=\"M0,0H108V108H0Z\"/>";
 if(!dark)body+="<path android:fillColor=\"#00000000\" android:strokeColor=\"#201F1D\" android:strokeWidth=\"2.5\" android:pathData=\"M18,18H90V90H18Z\"/>";
 body+=glyph("p",55,33,71,ink)+glyph("©",20,63,44,"#C70070");Files.writeString(out.resolve("brand_icon_"+key+".xml"),header+body+"</vector>");
 String word="<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"190dp\" android:height=\"110dp\" android:viewportWidth=\"380\" android:viewportHeight=\"220\">"+glyph("p",180,10,170,ink)+glyph("ix",180,175,170,ink)+glyph("©",68,118,80,"#C70070")+"</vector>";
 Files.writeString(out.resolve("brand_wordmark_"+key+".xml"),word);
 }
 }
}
