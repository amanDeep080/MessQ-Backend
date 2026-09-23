package com.messq.ops;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@Component
public class Store {
 public final JdbcTemplate db;
 public Store(JdbcTemplate db){this.db=db;}
 public List<Map<String,Object>> rows(String sql,Object... args){return db.queryForList(sql,args);}
 public Map<String,Object> one(String sql,Object... args){var rows=rows(sql,args);if(rows.isEmpty())throw error(404,"Record not found");return rows.get(0);}
 public long count(String sql,Object... args){Long n=db.queryForObject(sql,Long.class,args);return n==null?0:n;}
 public long insert(String table,Map<String,Object> values){
  // Table/column names are constants from the service, never client identifiers.
  var columns=new ArrayList<>(values.keySet());
  String sql="INSERT INTO "+table+" ("+String.join(",",columns)+") VALUES ("+String.join(",",Collections.nCopies(columns.size(),"?"))+")";
  var holder=new org.springframework.jdbc.support.GeneratedKeyHolder();
  db.update(connection->{var stmt=connection.prepareStatement(sql,new String[]{"id"});int i=1;for(String c:columns)stmt.setObject(i++,values.get(c));return stmt;},holder);
  return Objects.requireNonNull(holder.getKey()).longValue();
 }
 public static Map<String,Object> data(Object... pairs){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}
 public static long num(Map<String,Object> r,String key){Object value=r.get(key);return value==null?0:((Number)value).longValue();}
 public static String str(Map<String,Object> r,String key){return Objects.toString(r.get(key),"");}
 public static boolean bool(Map<String,Object> r,String key){return Boolean.TRUE.equals(r.get(key));}
 public static ResponseStatusException error(int status,String message){return new ResponseStatusException(HttpStatus.valueOf(status),message);}
 public static String text(Map<String,String> p,String key,int max){String v=p.getOrDefault(key,"").trim();if(v.isEmpty()||v.length()>max)throw error(400,key+" is required (max "+max+" characters)");return v;}
 public static String optional(Map<String,String> p,String key,int max){String v=p.getOrDefault(key,"").trim();if(v.length()>max)throw error(400,key+" is too long");return v;}
 public static long id(Map<String,String> p,String key){try{long v=Long.parseLong(p.getOrDefault(key,""));if(v<1)throw new NumberFormatException();return v;}catch(Exception e){throw error(400,"Invalid "+key);}}
 public static int integer(Map<String,String> p,String key,int min,int max){try{int v=Integer.parseInt(p.getOrDefault(key,""));if(v<min||v>max)throw new NumberFormatException();return v;}catch(Exception e){throw error(400,key+" must be between "+min+" and "+max);}}
 public static BigDecimal decimal(Map<String,String> p,String key,boolean positive){try{var d=new BigDecimal(p.getOrDefault(key,"")).setScale(3,java.math.RoundingMode.HALF_UP);if((positive?d.signum()<=0:d.signum()<0)||d.compareTo(new BigDecimal("1000000"))>0)throw new NumberFormatException();return d;}catch(Exception e){throw error(400,"Invalid "+key);}}
 public static String choice(Map<String,String> p,String key,String... allowed){String value=text(p,key,40).toUpperCase(Locale.ROOT);if(!List.of(allowed).contains(value))throw error(400,"Invalid "+key);return value;}
 public static LocalDate date(Map<String,String> p,String key){try{return LocalDate.parse(text(p,key,10));}catch(Exception e){throw error(400,"Use YYYY-MM-DD for "+key);}}
 public static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 public static OffsetDateTime time(Object value){if(value instanceof OffsetDateTime t)return t;if(value instanceof java.sql.Timestamp t)return t.toInstant().atOffset(ZoneOffset.UTC);return OffsetDateTime.parse(value.toString());}
}
