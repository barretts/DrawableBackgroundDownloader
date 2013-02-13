// Barrett Sonntag
// 01/2012
// Extended from the comment http://stackoverflow.com/a/7861011

// My code used ImageViewTouch as well, so needed to distinguish between it and a regular image
import it.sephiroth.android.library.imagezoom.ImageViewTouch;

// there is a ton of overflow in here for imports, need to clean this up
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.app.Activity;

public class DrawableBackgroudDownloader extends Activity {
	public int IMAGE_MAX_SIZE = 122;
	private static DrawableBackgroudDownloader instance = null;
	private ArrayList<Asset> assets = new ArrayList<Asset>(); 
	private ExecutorService mThreadPool;  
	private final Map<ImageView, String> mImageViews = Collections.synchronizedMap(new WeakHashMap<ImageView, String>()); 
	private File cacheDir;
	private int imageCount = 15;
	private HttpClient httpclient;
	
	public boolean isFullsize = false;

	final static int BUFFER_SIZE = 4096;
	final static int MAX_CACHE_SIZE = 1; 
	final int THREAD_POOL_SIZE = 4;
	
	private Timer timer;
	private final static long DELAY = 100;
	
	private SoftReference<Bitmap> bitmap;

	/**
	 * Constructor
	 */
    private DrawableBackgroudDownloader() {
    	mThreadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    	timer = new Timer(true);
		timer.schedule(new RunNextJob(), DELAY);
		HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setStaleCheckingEnabled(params, false);
        HttpConnectionParams.setSoTimeout(params, 5 * 1000);
        HttpConnectionParams.setSocketBufferSize(params, 8192);

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));

        // Create an HttpClient with the ThreadSafeClientConnManager.
        // This connection manager must be used if more than one thread will
        // be using the HttpClient.
        ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
        httpclient = new DefaultHttpClient(cm, params);
    }
    
    public static DrawableBackgroudDownloader getSingletonObject()
    {
      if (instance == null)
      {
          // it's ok, we can call this constructor
    	  //Log.d("DrawableBackgroudDownloader", "we need a new DBD!");
    	  instance = new DrawableBackgroudDownloader();
      }
      return instance;
    }


	/**
	 * Clears all instance data and stops running threads
	 */
	public void Reset() {
	    ExecutorService oldThreadPool = mThreadPool;
	    mThreadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
	    oldThreadPool.shutdownNow();

	    try {
		    mImageViews.clear();
		    timer.cancel();
		    timer = null;
	    } catch (Throwable t) {
	    	
	    }
	    //Log.d("DrawableBackgroundDownloader", "reset");
	}
	
	public void setFullsize(Boolean _setFullSize) {
		isFullsize = _setFullSize;
		assets.clear();
		if(isFullsize) {
			imageCount = 3;
		} else {
			imageCount = 15;
		}
	}

	public void loadDrawable(final String url, final ImageView imageView, final Drawable placeholder) {  
    	//Log.d("DrawableBackgroundDownloader", "loadDrawable");
	    mImageViews.put(imageView, url);  
	    cacheDir = imageView.getContext().getCacheDir();
	    queueJob(url, imageView, placeholder);
	} 

	private void runNextJob() {
		try {
			if(assets.size() > 0) {
				Asset asset = assets.get(0);
				runJob(asset.urlString, asset.targetImageView, asset.placeholderDrawable);
				assets.remove(0);
				
				if(assets.size() > 0) {
					timer.schedule(new RunNextJob(), DELAY);
				}
			}
		} catch (Throwable t) {
			
		}
	}
	
	private void runJob(final String url, final ImageView imageView, final Drawable placeholder) { 
		//final Boolean fullsizeStatus = isFullsize;
		/* Create handler in UI thread. */  
	    final Handler handler = new Handler() {  
	        @Override  
	        public void handleMessage(Message msg) {  
	            String tag = mImageViews.get(imageView);
	            if (tag != null && tag.equals(url)) {
                    if ((Bitmap)msg.obj != null) {
    	            	if(imageView.getClass().equals(ImageViewTouch.class)){
    	            		//Log.d("DrawableBackgroundDownloader", "i am a touch image");
    	            		if ((Bitmap)msg.obj != null) {
    	            			((ImageViewTouch)imageView).setImageBitmapReset((Bitmap)msg.obj, true);
    	            			imageView.setClickable(true);
    	            		} else {
    	            			//Log.d("DrawableBackgroundDownloader","getbitmap was null!");
    	            			((ImageViewTouch)imageView).setImageBitmapReset((Bitmap)msg.obj, true);
    	            		}
    	            	} else {
    	            		imageView.setImageBitmap((Bitmap)msg.obj);
    	            	}
            	        //Log.d("DrawableBackgroundDownloader", "drawable set " + url);
                    } else {  
    	            	if(imageView.getClass().equals(ImageViewTouch.class)){
    	            		//Log.d("DrawableBackgroundDownloader", "i am a touch image");
    	            		((ImageViewTouch)imageView).setImageBitmapReset(((BitmapDrawable)placeholder).getBitmap(), true);
    	            		imageView.setClickable(true);
    	            	} else {
    	            		imageView.setImageDrawable(placeholder);
    	            	}
                        //Log.d("DrawableBackgroundDownloader", "fail " + url);  
                    }
	            }  
	        }  
	    };
	    
	    //Log.d("DrawableBackgroundDownloader", "mThreadPool.submit: "+url);
	    mThreadPool.submit(new Runnable() {  
	        @Override  
	        public void run() {  
	            //Log.d("DrawableBackgroundDownloader", "mThreadPool run: "+url);

	            // if the view is not visible anymore, the image will be ready for next time in cache
	            Message message = Message.obtain();  
                message.obj = downloadBitmap(url).get();
                //Log.d("DrawableBackgroundDownloader", "Item downloaded: " + url);  

                handler.sendMessage(message);
	        }  
	    });  
	}
	
	private class RunNextJob extends TimerTask {
		public void run(){
			runOnUiThread(new Runnable() {
			    public void run() {
			    	runNextJob();
			    }
			});
		}
	}
	
	private void queueJob(final String url, final ImageView imageView, final Drawable placeholder) {  
		//Log.d("DrawableBackgroundDownloader", "queueJob: "+url);
		Asset asset = new Asset();
		asset.urlString = url;
		asset.targetImageView = imageView;
		asset.placeholderDrawable = placeholder;

		if(assets.size() > imageCount) {
			try {
				assets.remove(0);
			} catch (Throwable t) {
				assets = new ArrayList<Asset>();
			}
		}

		assets.add(asset);

		if(timer == null) {
			timer = new Timer(true);
		}

		timer.schedule(new RunNextJob(), DELAY);
	}

	private SoftReference<Bitmap> downloadBitmap(String urlString) {
	    try {  
	    	URL url = null;
	    	try {
	    		url = new URL(urlString);
	    	} catch (MalformedURLException e) {
                e.printStackTrace();
                return null;
	        }
			HttpGet httpRequest = null;
			try {
				httpRequest = new HttpGet(url.toURI());
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
			
		    URLConnection connection;
		    connection = url.openConnection();
		    connection.setConnectTimeout(1000);
		    connection.setUseCaches(true);

		    File cacheFile = new File(cacheDir, URLEncoder.encode(urlString) + ".jpg");

		    HttpParams params = new BasicHttpParams();
	          HttpConnectionParams.setStaleCheckingEnabled(params, false);
	          HttpConnectionParams.setSoTimeout(params, 5 * 1000);
	          HttpConnectionParams.setSocketBufferSize(params, 8192);

	          SchemeRegistry schemeRegistry = new SchemeRegistry();
	          schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));

	          // Create an HttpClient with the ThreadSafeClientConnManager.
	          // This connection manager must be used if more than one thread will
	          // be using the HttpClient.
	          ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);

			if(!cacheFile.exists() && httpRequest != null){
				//Log.d("DrawableBackgroundDownloader","create new cache file: "+URLEncoder.encode(urlString));
				cacheFile.createNewFile();
		        HttpResponse response = (HttpResponse) httpclient.execute(httpRequest);
				HttpEntity entity = response.getEntity();
				BufferedHttpEntity httpEntity = new BufferedHttpEntity(entity);
				FileOutputStream os = new FileOutputStream(cacheFile);

				httpEntity.writeTo(os);
				while (httpEntity.isStreaming()) {
					httpEntity.writeTo(os);
				}
				os.close();
			} else {
				//Log.d("DrawableBackgroundDownloader","cache file exists: "+cacheFile.getPath());
			}

			bitmap = decodeFile(cacheFile);

			if (bitmap != null) {
		        return bitmap;
			} else {
				return null;
			}
	        //putDrawableInCache(urlString,drawable);

	    } catch (MalformedURLException e) {  
	        e.printStackTrace();  
	    } catch (IOException e) {  
	        e.printStackTrace();  
	    }  

	    return null;  
	}  


	private InputStream getInputStream(String urlString) throws MalformedURLException, IOException {
	    URL url = new URL(urlString);
	    URLConnection connection;
	    connection = url.openConnection();
	    connection.setConnectTimeout(1000);
	    connection.setUseCaches(true); // 
	    Object response = connection.getContent();
	    if (!(response instanceof InputStream))
	        throw new IOException("URLConnection response is not instanceof InputStream");

	    return (InputStream)response;
	}
	
	private SoftReference<Bitmap> decodeFile(File f){
	    bitmap = new SoftReference<Bitmap>(null);
	    try {
	        //Decode image size
	        BitmapFactory.Options o = new BitmapFactory.Options();
	        o.inJustDecodeBounds = true;

	        FileInputStream fis = new FileInputStream(f);
	        BitmapFactory.decodeStream(fis, null, o);
	        fis.close();

	        int scale = 1;
	        int maxImageSize = IMAGE_MAX_SIZE;
	        if(isFullsize)
	        {
	        	maxImageSize = 1280;
	        }
	        
	        Log.d("maxImageSize", maxImageSize+"");
	        
	        if (o.outHeight > maxImageSize || o.outWidth > maxImageSize) {
                scale = (int)Math.pow(2, (int) Math.round(Math.log((maxImageSize / (double) Math.max(o.outHeight, o.outWidth)) / Math.log(0.5))));
            }
	        
	        if(!checkBitmapFitsInMemory(o.outWidth, o.outHeight, scale)) {
	        	Log.w("DrawableBackgroundDownloader","new bitmap requested doesn't fit in memory!");
	        	return bitmap;
	        }

	        o = null;
	        
	        //Decode with inSampleSize
	        BitmapFactory.Options o2 = new BitmapFactory.Options();
	        o2.inSampleSize = scale;

	        fis = new FileInputStream(f);
	        bitmap = new SoftReference<Bitmap>(BitmapFactory.decodeStream(fis, null, o2));
	        fis.close();
	        o2 = null;
	    } catch (IOException e) {
	    	e.printStackTrace(); 
	    } catch (Exception e) {
	    	e.printStackTrace(); 
	    }
	    return bitmap;
	}
	
	public class PatchInputStream extends FilterInputStream {
		  public PatchInputStream(InputStream in) {
		    super(in);
		  }
		  public long skip(long n) throws IOException {
		    long m = 0L;
		    while (m < n) {
		      long _m = in.skip(n-m);
		      if (_m == 0L) break;
		      m += _m;
		    }
		    return m;
		  }
		}
	
	static class FlushedInputStream extends FilterInputStream {
	    public FlushedInputStream(InputStream inputStream) {
	        super(inputStream);
	    }

	    @Override
	    public long skip(long n) throws IOException {
	        long totalBytesSkipped = 0L;
	        while (totalBytesSkipped < n) {
	            long bytesSkipped = in.skip(n - totalBytesSkipped);
	            if (bytesSkipped == 0L) {
	                  int byt = read();
	                  if (byt < 0) {
	                      break;  // we reached EOF
	                  } else {
	                      bytesSkipped = 1; // we read one byte
	                  }
	           }
	            totalBytesSkipped += bytesSkipped;
	        }
	        return totalBytesSkipped;
	    }
	}
	
	private class Asset {
		public String urlString;
		public ImageView targetImageView;
		public Drawable placeholderDrawable;
	}
	
	/**
	 * Checks if a bitmap with the specified size fits in memory
	 * @param bmpwidth Bitmap width
	 * @param bmpheight Bitmap height
	 * @param bmpdensity Bitmap bpp (use 2 as default)
	 * @return true if the bitmap fits in memory false otherwise
	 */
	public boolean checkBitmapFitsInMemory(long bmpwidth,long bmpheight, int bmpdensity ){
	    long reqsize=bmpwidth*bmpheight*bmpdensity;
	    long allocNativeHeap = Debug.getNativeHeapAllocatedSize();
	    long maxMemory = Runtime.getRuntime().maxMemory();
	    
	    final long heapPad=(long) Math.max(3*1024*1024,maxMemory*0.1);
	    if ((reqsize + allocNativeHeap + heapPad) >= maxMemory)
	    {
	        return false;
	    }
	    return true;
	}
}
