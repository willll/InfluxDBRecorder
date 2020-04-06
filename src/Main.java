import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.json.JSONObject;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class Main {

	private static class ticker_subscriber_task implements Runnable {
		protected String ticker_pub;
		protected InfluxDB influxDB;
		
		@SuppressWarnings("deprecation")
		public ticker_subscriber_task(String ticker_pub)
        {
        	this.ticker_pub = ticker_pub;
        	
    		//String rpName = "aRetentionPolicy";
    		String rpName = "autogen";
    		InfluxDB influxDB = InfluxDBFactory.connect("http://127.0.0.1:8086", "root", "root");
    		influxDB.createDatabase(ticker_pub);
    		influxDB.setDatabase(ticker_pub);
    		influxDB.setRetentionPolicy(rpName);

    		influxDB.enableBatch(BatchOptions.DEFAULTS);
        	
        	this.influxDB = influxDB;
        }
		
		@SuppressWarnings("deprecation")
		@Override
		public void run() {
			final String[] KEYS = { "open", "last", "bid", "ask", "high", "low", "vwap", "volume", "quoteVolume" };
			
			try {
	            // Prepare our context and subscriber
	            Context context = ZMQ.context(1);
	            Socket subscriber = context.socket(ZMQ.SUB);
	
	            subscriber.connect("tcp://localhost:5563");
	            subscriber.subscribe(this.ticker_pub.getBytes());
	            System.out.println("Listening : "+ticker_pub);
	            while (!Thread.currentThread ().isInterrupted ()) {

	                // Read envelope with address
	                String address = subscriber.recvStr ();
	                // Read message contents
	                String contents = subscriber.recvStr ();
	                //contents.to
	                System.out.println(address + " : " + contents);
	                
	                JSONObject obj = new JSONObject(contents);
	                
	                String currencies_pair = obj.getString("currencyPair");
	                long timestamp = 0;
	                try {
	                	timestamp = obj.getLong("timestamp");
	                	
		                for (String key : KEYS){
			                if (!obj.isNull(key) && !obj.isEmpty())
			                {
				                influxDB.write(Point.measurement(currencies_pair)
				            		    .time(timestamp, TimeUnit.MILLISECONDS)
				            		    .addField(key, obj.getDouble(key))
				            		    .build());
			                }
		                }
	                } catch (org.json.JSONException e) {
	                	// If timestamp is null, just push to influxDB without any time, by default it will add the current time
		                for (String key : KEYS){
			                if (!obj.isNull(key) && !obj.isEmpty())
			                {
				                influxDB.write(Point.measurement(currencies_pair)
				            		    .addField(key, obj.getDouble(key))
				            		    .build());
			                }
		                }
	                }
	            }
	            subscriber.close ();
	            context.term ();
        	} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
    }
	
	
	public static void main(String[] args) throws InterruptedException {
		ArrayList<Thread> thds = new ArrayList<Thread>();

		for (String ticker_str : BusDefinition.getTickersBusDefinitions())
		{
			Thread thd = new Thread(new ticker_subscriber_task(ticker_str));
			thd.start();
			thds.add(thd);
		}
		
		for (Thread thdx : thds) {
			thdx.join();
		}
	}

}
