package io.fabric8.apiman;

public class AuthToken {
    private static ThreadLocal<String> tl = new ThreadLocal<String>();
        
    public static String get() {
        return (String) tl.get();
      }
      public static void set(String newValue) {
        tl.set(newValue);
      }
    
    
}
