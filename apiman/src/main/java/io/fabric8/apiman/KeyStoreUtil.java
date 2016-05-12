package io.fabric8.apiman;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class KeyStoreUtil {

    final private static Log log = LogFactory.getLog(KeyStoreUtil.class);
            
    public static KeyManager[] getKeyManagers(Info pathInfo) throws Exception {
        
        File clientKeyStoreFile = new File(pathInfo.store);
        File clientKeyStorePasswordFile = new File(pathInfo.password);
        if (clientKeyStorePasswordFile.exists() && clientKeyStoreFile.exists()) {
            String clientKeyStorePassword = IOUtils.toString(clientKeyStorePasswordFile.toURI());
            if (clientKeyStorePassword!=null) clientKeyStorePassword = clientKeyStorePassword.trim();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = KeyStore.getInstance("JKS");
            
            FileInputStream clientFis = new FileInputStream(pathInfo.store);
            keyStore.load(clientFis, clientKeyStorePassword.toCharArray());
            clientFis.close();
            kmf.init(keyStore, clientKeyStorePassword.toCharArray());
            return kmf.getKeyManagers();
        } else {
            if (! clientKeyStoreFile.exists())
                log.info("No KeyManager: " + pathInfo.store    + " does not exist");
            if (! clientKeyStorePasswordFile.exists())
                log.info("No KeyManager: " + pathInfo.password + " does not exist");
            return null;
        }
    }
    
    public static TrustManager[] getTrustManagers(Info pathInfo) throws Exception {
        
        File trustStoreFile = new File(pathInfo.store);
        File trustStorePasswordFile = new File(pathInfo.password);
        if (trustStorePasswordFile.exists() && trustStoreFile.exists()) {
            String trustStorePassword = IOUtils.toString(trustStorePasswordFile.toURI());
            if (trustStorePassword!=null) trustStorePassword = trustStorePassword.trim();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore truststore = KeyStore.getInstance("JKS");
            
            FileInputStream fis = new FileInputStream(pathInfo.store);
            truststore.load(fis, trustStorePassword.toCharArray());
            fis.close();
            tmf.init(truststore);
            return tmf.getTrustManagers();
        } else {
            if (! trustStoreFile.exists())
                log.info("No TrustManager: " + pathInfo.store    + " does not exist");
            if (! trustStorePasswordFile.exists())
                log.info("No TrustManager: " + pathInfo.password + " does not exist");
            return null;
        }
    }
    
public class Info {
        
        String store;
        String password;
       
        public Info(String store, String password) {
            super();
            this.store = store;
            this.password = password;
        }

}
}
