package org.exoplatform.services.pdfviewer;

import java.io.File;
import java.io.Serializable;
import org.exoplatform.services.cache.CacheListener;
import org.exoplatform.services.cache.CacheListenerContext;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * Created by exo on 1/28/16.
 */
public class PDFViewerListener implements CacheListener {

    private static final Log LOG = ExoLogger.getLogger(PDFViewerListener.class.getName());

    public PDFViewerListener() {
    }

    @Override
    public void onExpire(CacheListenerContext context, Serializable key, Object obj) throws Exception {
        String path = obj.toString();
        File file = new File(path);
        file.delete();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Delete Expired File : " + file);
        }
    }

    @Override
    public void onRemove(CacheListenerContext context, Serializable key, Object obj) throws Exception {
        String path = obj.toString();
        File file = new File(path);
        file.delete();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Delete Removed File : " + file);
        }
    }

    @Override
    public void onPut(CacheListenerContext context, Serializable key, Object obj) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Put object in the cache : " + obj);
        }
    }

    @Override
    public void onGet(CacheListenerContext context, Serializable key, Object obj) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Get object from the cache : " + obj);
        }
    }

    @Override
    public void onClearCache(CacheListenerContext context) throws Exception {
        String property = "java.io.tmpdir";
        File temp = new File(System.getProperty(property));
        File[] listOfFiles = temp.listFiles();
        for(int i = 0; i < listOfFiles.length; i++){
            if(listOfFiles[i].isFile()) {
                listOfFiles[i].delete();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Delete the file: " + listOfFiles[i].getAbsolutePath());
                }
            }
        }
    }
}
