package com.messq.ops;
import java.util.*;
public final class Ui {
 private Ui(){}
 public record Field(String key,String label,String type,boolean required,List<String> options,String value){}
 public record Action(String operation,String label,Map<String,String> params,List<Field> fields){}
 public record Card(String id,String title,String subtitle,String tag,List<Action> actions){}
 public record Metric(String label,String value){}
 public record Page(String title,String description,List<Metric> metrics,List<Card> cards,List<Action> actions,String artwork){}
 public record Module(String key,String title){}
 public record Workspace(String messName,String role,List<Module> modules,boolean mockPayments){}
 public record Command(String requestId,Map<String,String> values){}
 public record Result(String message,String qr,String aiText) {public Result(String message){this(message,null,null);}}
 public static Field field(String key,String label,String type,boolean required,String value,String...options){return new Field(key,label,type,required,List.of(options),value);}
 public static Field field(String key,String label){return field(key,label,"text",true,"");}
 public static Action action(String op,String label,Map<String,String> params,Field...fields){return new Action(op,label,params,List.of(fields));}
 public static Action action(String op,String label,Field...fields){return action(op,label,Map.of(),fields);}
 public static Card card(Object id,String title,String sub,String tag,Action... actions){return new Card(id.toString(),title,sub,tag,List.of(actions));}
 public static Page page(String title,String desc,List<Card> cards,Action... actions){return new Page(title,desc,List.of(),cards,List.of(actions),"");}
}
